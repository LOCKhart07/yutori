package com.yutori.ui

import com.yutori.database.entities.SmsLogEntity
import com.yutori.parser.Classification
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LatestIngestedMessageTest {

    @Test
    fun `every known Classification decodes onto the model`() {
        // Covers the decoding contract across all 13 values — if Classification
        // grows an enum value, Classification.valueOf still decodes it and the
        // model carries it through without lossy rollup.
        Classification.entries.forEach { c ->
            smsLog(classification = c.name)
                .toLatestIngestedMessage()
                .classification shouldBe c
        }
    }

    @Test
    fun `unknown classification string decodes to null`() {
        // Legacy rows or rows written by a future app version may carry a
        // string that no longer matches any enum name. The model surfaces
        // this as null; the screen renders it the same as UNMATCHED.
        smsLog(classification = "NEW_CLASSIFICATION")
            .toLatestIngestedMessage()
            .classification shouldBe null
    }

    @Test
    fun `body preview collapses whitespace`() {
        smsLog(body = "Line1\n\n   Line2")
            .toLatestIngestedMessage()
            .bodyPreview shouldBe "Line1 Line2"
    }

    @Test
    fun `body preview is not hard-capped and relies on UI ellipsis`() {
        val longBody = "a".repeat(200)
        smsLog(body = longBody)
            .toLatestIngestedMessage()
            .bodyPreview shouldBe longBody
    }

    private fun smsLog(
        classification: String = "UPI_PAYMENT",
        body: String = "test",
    ) = SmsLogEntity(
        id = 1,
        androidSmsId = 2,
        sender = "JD-BANK",
        body = body,
        receivedAtMs = 123L,
        classification = classification,
        patternMatched = null,
        source = "SMS_IMPORT",
        reparsedAtMs = null,
    )
}
