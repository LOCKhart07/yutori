package com.yutori.parser

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ParserTest {

    @Test
    fun `returns UNMATCHED when no rule matches`() {
        val result = Parser.parse(
            SmsInput(
                sender = "VM-RANDOM",
                body = "Hello from a non-financial bulk sender without parser patterns.",
            ),
        )

        result.classification shouldBe Classification.UNMATCHED
        result.pattern shouldBe "UNMATCHED"
        result.amount shouldBe null
        result.merchant shouldBe null
        result.last4 shouldBe null
    }

    @Test
    fun `money movement rule wins over later admin notice rule`() {
        val result = Parser.parse(
            SmsInput(
                sender = "VK-AXISBK-S",
                body = "Spent INR 100.00\n" +
                    "Axis Bank Card no. XX1111\n" +
                    "01-01-26 12:00:00 IST\n" +
                    "BHARAT PETR\n" +
                    "Avl Limit: INR 50000\n" +
                    "Thank you for contacting Axis Bank",
            ),
        )

        result.classification shouldBe Classification.CC_TRANSACTION
        result.pattern shouldBe "axis_cc_spend"
        result.amount shouldBe 100.0
        result.last4 shouldBe "1111"
    }

    @Test
    fun `otp guard phrase suppresses OTP catch-all and falls through to UNMATCHED`() {
        val result = Parser.parse(
            SmsInput(
                sender = "VM-UNKWN-S",
                body = "Your OTP is 123456, do not share this with anyone.",
            ),
        )

        result.classification shouldBe Classification.UNMATCHED
        result.pattern shouldBe "UNMATCHED"
    }

    @Test
    fun `known non-fin sender still classifies as NON_FINANCIAL`() {
        val result = Parser.parse(
            SmsInput(
                sender = "JD-VIJAYS-S",
                body = "Loyalty reminder for your next visit.",
            ),
        )

        result.classification shouldBe Classification.NON_FINANCIAL
        result.pattern shouldBe "known_non_fin_sender"
    }
}
