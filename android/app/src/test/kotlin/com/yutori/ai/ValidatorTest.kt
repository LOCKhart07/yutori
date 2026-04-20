package com.yutori.ai

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class ValidatorTest {

    @Test
    fun `accepts pattern that appears in user input`() {
        val extracted = ExtractedRule(pattern = "cred", category = "cc bill")
        val result = Validator.validate(
            userInput = "anything from CRED is a credit-card bill payment",
            extracted = extracted,
        )
        result.shouldBeInstanceOf<Validator.Result.Valid>()
        result.rule.pattern shouldBe "cred"
        result.rule.category shouldBe "cc bill"
    }

    @Test
    fun `rejects null extraction with PARSE_FAILED`() {
        val result = Validator.validate("user input", extracted = null)
        result.shouldBeInstanceOf<Validator.Result.Invalid>()
        result.reason shouldBe ValidationFailure.PARSE_FAILED
    }

    @Test
    fun `rejects pattern shorter than 3 chars`() {
        val result = Validator.validate(
            userInput = "treat ab as spam",
            extracted = ExtractedRule(pattern = "ab", category = "x"),
        )
        result.shouldBeInstanceOf<Validator.Result.Invalid>()
        result.reason shouldBe ValidationFailure.PATTERN_TOO_SHORT
    }

    @Test
    fun `rejects pattern longer than 80 chars`() {
        val bigPattern = "a".repeat(90)
        val result = Validator.validate(
            userInput = bigPattern,
            extracted = ExtractedRule(pattern = bigPattern, category = "x"),
        )
        result.shouldBeInstanceOf<Validator.Result.Invalid>()
        result.reason shouldBe ValidationFailure.PATTERN_TOO_LONG
    }

    @Test
    fun `rejects pattern that does not appear in user input`() {
        // The Stage A idx 7 failure mode: model echoed "netflix" from
        // the few-shot example when input didn't fit the schema.
        val result = Validator.validate(
            userInput = "ignore all transfers to my savings account",
            extracted = ExtractedRule(pattern = "netflix", category = "entertainment"),
        )
        result.shouldBeInstanceOf<Validator.Result.Invalid>()
        result.reason shouldBe ValidationFailure.PATTERN_NOT_IN_INPUT
    }

    @Test
    fun `substring check is case-insensitive`() {
        val result = Validator.validate(
            userInput = "anything from CRED is a bill",
            extracted = ExtractedRule(pattern = "cred", category = null),
        )
        result.shouldBeInstanceOf<Validator.Result.Valid>()
    }

    @Test
    fun `accepts null category`() {
        val result = Validator.validate(
            userInput = "cred is a cc bill",
            extracted = ExtractedRule(pattern = "cred", category = null),
        )
        result.shouldBeInstanceOf<Validator.Result.Valid>()
        result.rule.category shouldBe null
    }

    @Test
    fun `rejects category longer than 40 chars`() {
        val longCategory = "x".repeat(50)
        val result = Validator.validate(
            userInput = "cred is a bill",
            extracted = ExtractedRule(pattern = "cred", category = longCategory),
        )
        result.shouldBeInstanceOf<Validator.Result.Invalid>()
        result.reason shouldBe ValidationFailure.CATEGORY_TOO_LONG
    }

    @Test
    fun `trims pattern whitespace before validating`() {
        val result = Validator.validate(
            userInput = "cred is a bill",
            extracted = ExtractedRule(pattern = "  cred  ", category = "cc bill"),
        )
        result.shouldBeInstanceOf<Validator.Result.Valid>()
        result.rule.pattern shouldBe "cred"
    }

    @Test
    fun `empty-after-trim category becomes null in Valid result`() {
        val result = Validator.validate(
            userInput = "cred is a bill",
            extracted = ExtractedRule(pattern = "cred", category = "   "),
        )
        result.shouldBeInstanceOf<Validator.Result.Valid>()
        result.rule.category shouldBe null
    }
}
