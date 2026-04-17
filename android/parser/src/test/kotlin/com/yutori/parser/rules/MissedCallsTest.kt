package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import com.yutori.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MissedCallsTest {

    @Test
    fun `Jio missed call notice with leading plus matches`() {
        val result = missedCalls(
            SmsInput(
                sender = "+919876543210",
                body = "Dear Customer, You have a missed call from +919876543210 " +
                    "The last missed call was at 12:00 PM on 01-Jan-2026 Thankyou, Team Jio.",
            ),
        )
        result shouldBe ParseResult(
            classification = Classification.NON_FINANCIAL,
            amount = null,
            currency = "INR",
            merchant = null,
            last4 = null,
            category = null,
            pattern = "missed_calls",
        )
    }

    @Test
    fun `callee now available notice matches via alt phrasing`() {
        val result = missedCalls(
            SmsInput(
                sender = "919876543211",
                body = "Dear Customer, +919876543211 is now available to take calls.",
            ),
        )
        result shouldBe ParseResult(
            classification = Classification.NON_FINANCIAL,
            amount = null,
            currency = "INR",
            merchant = null,
            last4 = null,
            category = null,
            pattern = "missed_calls",
        )
    }

    @Test
    fun `non-numeric sender returns null`() {
        missedCalls(
            SmsInput(
                sender = "VK-AXISBK-S",
                body = "You have a missed call from +919876543210",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `numeric sender but unrelated body returns null`() {
        missedCalls(
            SmsInput(
                sender = "+919876543210",
                body = "Your OTP is 123456",
            ),
        ).shouldBeNull()
    }
}
