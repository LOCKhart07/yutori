package com.yutori.ai

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StructuredOutputParserTest {

    @Test
    fun `extracts pattern and category from bare JSON object`() {
        val result = StructuredOutputParser.parse(
            """{"pattern": "CRED", "category": "credit card bill"}""",
        )
        result?.pattern shouldBe "CRED"
        result?.category shouldBe "credit card bill"
    }

    @Test
    fun `extracts from JSON wrapped in markdown code fence`() {
        val result = StructuredOutputParser.parse(
            """```json
            |{"pattern": "swiggy", "category": "food"}
            |```""".trimMargin(),
        )
        result?.pattern shouldBe "swiggy"
        result?.category shouldBe "food"
    }

    @Test
    fun `extracts from JSON with leading preamble`() {
        val result = StructuredOutputParser.parse(
            "Sure, here you go: " +
                """{"pattern": "netflix", "category": "entertainment"}""",
        )
        result?.pattern shouldBe "netflix"
        result?.category shouldBe "entertainment"
    }

    @Test
    fun `returns null when no JSON object present`() {
        StructuredOutputParser.parse("I don't understand this request").shouldBeNull()
    }

    @Test
    fun `returns null when pattern field is missing`() {
        StructuredOutputParser.parse(
            """{"category": "food"}""",
        ).shouldBeNull()
    }

    @Test
    fun `returns null when pattern field is empty string`() {
        StructuredOutputParser.parse(
            """{"pattern": "", "category": "food"}""",
        ).shouldBeNull()
    }

    @Test
    fun `tolerates escaped quotes inside string values`() {
        val result = StructuredOutputParser.parse(
            """{"pattern": "he said \"hi\"", "category": "odd"}""",
        )
        result?.pattern shouldBe """he said "hi""""
    }

    @Test
    fun `picks outermost JSON object when nested objects exist`() {
        val result = StructuredOutputParser.parse(
            """{"pattern": "cred", "category": "cc bill", "meta": {"nested": true}}""",
        )
        result?.pattern shouldBe "cred"
        result?.category shouldBe "cc bill"
    }

    @Test
    fun `category is null when field is absent`() {
        val result = StructuredOutputParser.parse(
            """{"pattern": "salary"}""",
        )
        result?.pattern shouldBe "salary"
        result?.category shouldBe null
    }

    @Test
    fun `category is null when field value is empty string`() {
        val result = StructuredOutputParser.parse(
            """{"pattern": "salary", "category": ""}""",
        )
        result?.pattern shouldBe "salary"
        result?.category shouldBe null
    }
}
