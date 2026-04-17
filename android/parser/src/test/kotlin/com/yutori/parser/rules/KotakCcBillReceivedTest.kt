package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import com.yutori.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class KotakCcBillReceivedTest {

    @Test
    fun `integer amount`() {
        val result = kotakCcBillReceived(
            SmsInput(
                sender = "VK-KOTAKB-S",
                body = "Payment of INR 1000 is credited to your Kotak Bank " +
                    "Credit Card x3333 on 01-JAN-2026. Available Credit " +
                    "limit is INR 100000.00.",
            ),
        )
        result shouldBe ParseResult(
            classification = Classification.CC_BILL_PAYMENT,
            amount = 1000.0,
            currency = "INR",
            merchant = null,
            last4 = "3333",
            category = null,
            pattern = "kotak_cc_bill_received",
        )
    }

    @Test
    fun `decimal amount`() {
        val result = kotakCcBillReceived(
            SmsInput(
                sender = "VK-KOTAKB-S",
                body = "Payment of INR 500.00 is credited to your Kotak Bank " +
                    "Credit Card x3333 on 01-JAN-2026. Available Credit " +
                    "limit is INR 100000.00.",
            ),
        )
        result shouldBe ParseResult(
            classification = Classification.CC_BILL_PAYMENT,
            amount = 500.00,
            currency = "INR",
            merchant = null,
            last4 = "3333",
            category = null,
            pattern = "kotak_cc_bill_received",
        )
    }

    @Test
    fun `wrong sender returns null`() {
        kotakCcBillReceived(
            SmsInput(
                sender = "VK-AXISBK-S",
                body = "Payment of INR 1000 is credited to your Kotak Bank " +
                    "Credit Card x3333 on 01-JAN-2026.",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `axis-shaped body returns null`() {
        // Axis phrasing ("has been received towards") must not match the Kotak rule.
        kotakCcBillReceived(
            SmsInput(
                sender = "VK-KOTAKB-S",
                body = "Payment of INR 1000 has been received towards your " +
                    "Axis Bank Credit Card XX1111 on 01-01-26",
            ),
        ).shouldBeNull()
    }
}
