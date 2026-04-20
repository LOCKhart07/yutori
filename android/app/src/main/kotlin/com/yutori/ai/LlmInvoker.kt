package com.yutori.ai

import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.flow.onEach

/**
 * Thin boundary around LiteRT-LM's streaming message API.
 *
 * Exists so `RuleExtractor` can be unit-tested without needing a real
 * `Engine`. Tests inject a fake `LlmInvoker` that returns canned
 * strings; production uses [DefaultLlmInvoker] which wraps an
 * [LlmEngineHolder].
 */
interface LlmInvoker {
    suspend fun complete(systemPrompt: String, userPrompt: String): String
}

/**
 * Production implementation. Creates a fresh `Conversation` per call
 * per spec §4.2 (confirmed in Stage A — shared conversations leak
 * state across prompts even with `Engine` recreation).
 */
class DefaultLlmInvoker(
    private val engineHolder: LlmEngineHolder,
) : LlmInvoker {

    override suspend fun complete(systemPrompt: String, userPrompt: String): String {
        val engine = engineHolder.ensureReady()
        val conversation = engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                samplerConfig = SamplerConfig(
                    topK = 1,
                    topP = 1.0,
                    temperature = 0.0,
                ),
            ),
        )
        try {
            val buf = StringBuilder()
            conversation.sendMessageAsync(userPrompt)
                .onEach { chunk -> buf.append(chunk.toString()) }
                .collect {}
            return buf.toString()
        } finally {
            runCatching { conversation.close() }
        }
    }
}
