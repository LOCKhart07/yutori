package com.yutori.ai

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RuleExtractorTest {

    private class FakeInvoker(
        private val response: String? = null,
        private val throwOnCall: Throwable? = null,
    ) : LlmInvoker {
        var lastSystemPrompt: String? = null
        var lastUserPrompt: String? = null

        override suspend fun complete(systemPrompt: String, userPrompt: String): String {
            lastSystemPrompt = systemPrompt
            lastUserPrompt = userPrompt
            throwOnCall?.let { throw it }
            return response ?: error("no canned response configured")
        }
    }

    @Test
    fun `successful extraction returns Success with parsed rule`() = runTest {
        val invoker = FakeInvoker(
            response = """{"pattern": "cred", "category": "credit card bill"}""",
        )
        val extractor = RuleExtractor(invoker)

        val result = extractor.extract("anything from CRED is a credit-card bill")

        result.shouldBeInstanceOf<ExtractionResult.Success>()
        result.rule.pattern shouldBe "cred"
        result.rule.category shouldBe "credit card bill"
    }

    @Test
    fun `parser failure surfaces as ValidationFailed with PARSE_FAILED`() = runTest {
        val invoker = FakeInvoker(response = "I cannot help with that request.")
        val extractor = RuleExtractor(invoker)

        val result = extractor.extract("treat cred as a bill")

        result.shouldBeInstanceOf<ExtractionResult.ValidationFailed>()
        result.reason shouldBe ValidationFailure.PARSE_FAILED
    }

    @Test
    fun `substring-check failure surfaces as ValidationFailed with PATTERN_NOT_IN_INPUT`() = runTest {
        // Stage A idx 7 failure mode: model echoes a few-shot example
        // verbatim when the user prompt doesn't fit the schema.
        val invoker = FakeInvoker(
            response = """{"pattern": "netflix", "category": "entertainment"}""",
        )
        val extractor = RuleExtractor(invoker)

        val result = extractor.extract("ignore all transfers to my savings account")

        result.shouldBeInstanceOf<ExtractionResult.ValidationFailed>()
        result.reason shouldBe ValidationFailure.PATTERN_NOT_IN_INPUT
    }

    @Test
    fun `invoker exception surfaces as ModelUnavailable preserving cause`() = runTest {
        val thrown = IllegalStateException("JNI load failed on armv7")
        val invoker = FakeInvoker(throwOnCall = thrown)
        val extractor = RuleExtractor(invoker)

        val result = extractor.extract("anything")

        result.shouldBeInstanceOf<ExtractionResult.ModelUnavailable>()
        result.cause shouldBe thrown
    }

    @Test
    fun `extract passes the system prompt and user input through to the invoker`() = runTest {
        val invoker = FakeInvoker(
            response = """{"pattern": "flipkart", "category": "shopping"}""",
        )
        val customPrompt = "custom system instruction for testing"
        val extractor = RuleExtractor(invoker, systemPrompt = customPrompt)

        extractor.extract("flipkart purchases are shopping")

        invoker.lastSystemPrompt shouldBe customPrompt
        invoker.lastUserPrompt shouldBe "flipkart purchases are shopping"
    }
}
