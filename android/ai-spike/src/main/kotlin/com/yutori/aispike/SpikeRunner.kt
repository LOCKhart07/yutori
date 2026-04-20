package com.yutori.aispike

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.flow.onEach
import java.io.File
import kotlin.time.Duration
import kotlin.time.TimeSource

data class ModelFile(val path: String, val sizeBytes: Long)

data class ExtractionResult(
    val prompt: String,
    val firstTokenLatency: Duration,
    val totalLatency: Duration,
    val response: String,
    val extractedRule: ExtractedRule?,
)

data class InitResult(val engine: Engine, val coldInit: Duration, val modelFile: ModelFile)

enum class RunMode { TOOL_CALL, PARSER, PARSER_FRESH_ENGINE }

class SpikeRunner(private val context: Context) {

    private var engine: Engine? = null
    private var toolConversation: Conversation? = null
    private val ruleTool = RuleExtractionTool()

    fun locateModel(): ModelFile? {
        val f = File(context.getExternalFilesDir(null), MODEL_FILENAME)
        return if (f.exists() && f.length() > 0) ModelFile(f.absolutePath, f.length()) else null
    }

    suspend fun initEngine(mode: RunMode): InitResult {
        require(engine == null) { "Engine already initialized" }
        val model = locateModel() ?: error("Model file not found. adb push to ${context.getExternalFilesDir(null)}/$MODEL_FILENAME")

        Engine.setNativeMinLogSeverity(LogSeverity.WARNING)

        val mark = TimeSource.Monotonic.markNow()
        val e = Engine(EngineConfig(modelPath = model.path, backend = Backend.CPU()))
        e.initialize()
        val cold = mark.elapsedNow()

        // TOOL_CALL mode keeps a single long-lived conversation with the tool
        // registered, matching how production would actually use @Tool.
        // PARSER mode creates a fresh conversation per prompt (in runOne) to
        // avoid the context pollution we saw in Stage A, so no engine-level
        // conversation is created here.
        if (mode == RunMode.TOOL_CALL) {
            toolConversation = e.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(TOOL_SYSTEM_PROMPT),
                    samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
                    tools = listOf(tool(ruleTool)),
                ),
            )
        }

        engine = e
        return InitResult(e, cold, model)
    }

    suspend fun runOne(prompt: String, mode: RunMode): ExtractionResult = when (mode) {
        RunMode.TOOL_CALL -> runToolCall(prompt)
        RunMode.PARSER -> runParser(prompt)
        RunMode.PARSER_FRESH_ENGINE -> runParserFreshEngine(prompt)
    }

    // Diagnostic path: tear down the Engine entirely and re-initialize it
    // before every prompt. Removes any Engine-level cache (KV cache,
    // tokenizer state, JNI-side buffers) that could leak across
    // createConversation calls. If this produces different output than
    // PARSER mode on the same prompt sequence, we've confirmed Engine
    // caching. If output is identical, the leak is lower in the stack or
    // somewhere else (prompt determinism, sampler state, etc.).
    private suspend fun runParserFreshEngine(prompt: String): ExtractionResult {
        val model = requireNotNull(engine) { "initEngine(PARSER_FRESH_ENGINE) must run first" }
            .let { locateModel()!! }

        // Throw away the current Engine + any conversation.
        runCatching { toolConversation?.close() }
        runCatching { engine?.close() }
        toolConversation = null
        engine = null

        // Re-initialize from scratch.
        val mark = TimeSource.Monotonic.markNow()
        val e = Engine(EngineConfig(modelPath = model.path, backend = Backend.CPU()))
        e.initialize()
        engine = e

        val conv = e.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(PARSER_SYSTEM_PROMPT),
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
            ),
        )
        try {
            var firstToken: Duration? = null
            val buf = StringBuilder()

            conv.sendMessageAsync(prompt)
                .onEach { chunk ->
                    if (firstToken == null) firstToken = mark.elapsedNow()
                    buf.append(chunk.toString())
                }
                .collect {}

            val raw = buf.toString()
            return ExtractionResult(
                prompt = prompt,
                firstTokenLatency = firstToken ?: mark.elapsedNow(),
                totalLatency = mark.elapsedNow(),
                response = raw,
                extractedRule = StructuredOutputParser.parse(raw),
            )
        } finally {
            runCatching { conv.close() }
        }
    }

    private suspend fun runToolCall(prompt: String): ExtractionResult {
        val conv = requireNotNull(toolConversation) { "initEngine(TOOL_CALL) must run first" }
        ruleTool.reset()

        val mark = TimeSource.Monotonic.markNow()
        var firstToken: Duration? = null
        val buf = StringBuilder()

        conv.sendMessageAsync(prompt)
            .onEach { chunk ->
                if (firstToken == null) firstToken = mark.elapsedNow()
                buf.append(chunk.toString())
            }
            .collect {}

        return ExtractionResult(
            prompt = prompt,
            firstTokenLatency = firstToken ?: mark.elapsedNow(),
            totalLatency = mark.elapsedNow(),
            response = buf.toString(),
            extractedRule = ruleTool.calls.firstOrNull(),
        )
    }

    private suspend fun runParser(prompt: String): ExtractionResult {
        val eng = requireNotNull(engine) { "initEngine(PARSER) must run first" }

        // Fresh conversation per prompt — avoids the cross-turn context
        // pollution that derailed prompts 3–10 in Stage A (the 1B model got
        // stuck on INCOMING_CREDIT for every subsequent prompt).
        val conv = eng.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(PARSER_SYSTEM_PROMPT),
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
            ),
        )

        try {
            val mark = TimeSource.Monotonic.markNow()
            var firstToken: Duration? = null
            val buf = StringBuilder()

            conv.sendMessageAsync(prompt)
                .onEach { chunk ->
                    if (firstToken == null) firstToken = mark.elapsedNow()
                    buf.append(chunk.toString())
                }
                .collect {}

            val raw = buf.toString()
            return ExtractionResult(
                prompt = prompt,
                firstTokenLatency = firstToken ?: mark.elapsedNow(),
                totalLatency = mark.elapsedNow(),
                response = raw,
                extractedRule = StructuredOutputParser.parse(raw),
            )
        } finally {
            runCatching { conv.close() }
        }
    }

    fun close() {
        runCatching { toolConversation?.close() }
        runCatching { engine?.close() }
        toolConversation = null
        engine = null
    }

    companion object {
        const val MODEL_FILENAME = "model.litertlm"

        private val TOOL_SYSTEM_PROMPT = """
            You are a rule-extraction assistant for a personal-finance app. The user describes how
            a merchant, UPI handle, or bank sender should be classified. Call add_recipient_rule with:
              - pattern: merchant name, UPI handle, or substring.
              - patternKind: LITERAL, PREFIX, or REGEX.
              - classification: UPI_PAYMENT, CC_BILL_PAYMENT, SELF_TRANSFER, INCOMING_CREDIT, or NON_FINANCIAL.
              - note: optional.
        """.trimIndent()

        // JSON-only output. Every IT model has seen massive amounts of JSON
        // in training, so it's a much more reliable output contract than
        // an ad-hoc key: value scheme.
        private val PARSER_SYSTEM_PROMPT = """
            Extract the merchant pattern and category from the user's instruction.
            Respond with ONLY a JSON object, no markdown fencing, no prose, no code blocks:
            {"pattern": "<merchant name, UPI handle, or substring>", "category": "<category>"}

            User: anything from swiggy is food
            {"pattern": "swiggy", "category": "food"}

            User: treat cred as a credit card bill
            {"pattern": "cred", "category": "credit card bill"}

            User: netflix is entertainment
            {"pattern": "netflix", "category": "entertainment"}
        """.trimIndent()
    }
}
