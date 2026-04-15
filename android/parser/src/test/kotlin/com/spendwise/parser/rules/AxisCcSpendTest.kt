package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AxisCcSpendTest {

    @Test
    fun `T20 - Axis CC spend in INR`() {
        val result = axisCcSpend(
            SmsInput(
                sender = "VK-AXISBK",
                body = "Spent INR 100.00\nAxis Bank Card no. XX1111\n01-01-26 12:00:00 IST\nBHARAT PETR\nAvl Limit: INR 50000",
            ),
        )!!
        result.classification shouldBe Classification.CC_TRANSACTION
        result.amount shouldBe 100.00
        result.currency shouldBe "INR"
        result.merchant shouldBe "BHARAT PETR"
        result.last4 shouldBe "1111"
        result.category shouldBe null
        result.pattern shouldBe "axis_cc_spend"
    }

    @Test
    fun `T21 - Axis CC forex spend in USD captures ccy raw`() {
        val result = axisCcSpend(
            SmsInput(
                sender = "VK-AXISBK",
                body = "Spent USD 10.00\nAxis Bank Card no. XX1111\n01-01-26 12:00:00 IST\nGITHUB, INC\nAvl Limit: INR 50000",
            ),
        )!!
        result.classification shouldBe Classification.CC_TRANSACTION
        result.amount shouldBe 10.00
        result.currency shouldBe "USD"
        result.merchant shouldBe "GITHUB, INC"
        result.last4 shouldBe "1111"
        result.pattern shouldBe "axis_cc_spend"
    }

    @Test
    fun `non-whitelisted currency falls through to null - UNMATCHED`() {
        axisCcSpend(
            SmsInput(
                sender = "VK-AXISBK",
                body = "Spent JPY 1000\nAxis Bank Card no. XX1111\n01-01-26 12:00:00 IST\nSOME MERCHANT\nAvl Limit: INR 50000",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `wrong sender returns null`() {
        axisCcSpend(
            SmsInput(
                sender = "JD-KOTAKB-S",
                body = "Spent INR 100.00\nAxis Bank Card no. XX1111\n01-01-26 12:00:00 IST\nBHARAT PETR\nAvl Limit: INR 50000",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `non-matching body returns null`() {
        axisCcSpend(
            SmsInput(
                sender = "VK-AXISBK",
                body = "Payment of INR 1000 has been received towards your Axis Bank Credit Card XX1111 on 01-01-26",
            ),
        ).shouldBeNull()
    }
}
