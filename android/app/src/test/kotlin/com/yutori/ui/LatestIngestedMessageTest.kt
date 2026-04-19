package com.yutori.ui

import com.yutori.database.entities.SmsLogEntity
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LatestIngestedMessageTest {

    @Test
    fun `unmatched classification maps to needs review`() {
        smsLog(classification = "UNMATCHED")
            .toLatestIngestedMessage()
            .outcome shouldBe IngestedMessageOutcome.NEEDS_REVIEW
    }

    @Test
    fun `drop classifications map to ignored`() {
        smsLog(classification = "OTP")
            .toLatestIngestedMessage()
            .outcome shouldBe IngestedMessageOutcome.IGNORED
    }

    @Test
    fun `spend classifications map to counted in budget`() {
        smsLog(classification = "UPI_PAYMENT")
            .toLatestIngestedMessage()
            .outcome shouldBe IngestedMessageOutcome.COUNTED_IN_BUDGET
    }

    @Test
    fun `unknown classification falls back to needs review`() {
        smsLog(classification = "NEW_CLASSIFICATION")
            .toLatestIngestedMessage()
            .outcome shouldBe IngestedMessageOutcome.NEEDS_REVIEW
    }

    @Test
    fun `body preview collapses whitespace`() {
        smsLog(body = "Line1\n\n   Line2")
            .toLatestIngestedMessage()
            .bodyPreview shouldBe "Line1 Line2"
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
