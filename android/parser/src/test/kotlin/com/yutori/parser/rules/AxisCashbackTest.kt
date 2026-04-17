package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AxisCashbackTest {

    @Test
    fun `Axis Ace cashback credit`() {
        val result = axisCashback(
            SmsInput(
                sender = "JD-AXISBK-S",
                body = "Congratulations! Cashback of INR 100 has been credited to your Axis Bank Ace Credit Card XX1111 towards your last month spends - Axis Bank",
            ),
        )!!
        result.classification shouldBe Classification.CASHBACK
        result.amount shouldBe 100.0
        result.currency shouldBe "INR"
        result.last4 shouldBe "1111"
        result.merchant shouldBe null
        result.category shouldBe null
        result.pattern shouldBe "axis_cashback"
    }

    @Test
    fun `decimal amount parses`() {
        val result = axisCashback(
            SmsInput(
                sender = "VK-AXISBK",
                body = "Congratulations! Cashback of INR 42.50 has been credited to your Axis Bank Magnus Credit Card XX1234 towards your spends",
            ),
        )!!
        result.amount shouldBe 42.50
        result.last4 shouldBe "1234"
    }

    @Test
    fun `wrong sender returns null`() {
        axisCashback(
            SmsInput(
                sender = "JD-ICICIT-S",
                body = "Congratulations! Cashback of INR 100 has been credited to your Axis Bank Ace Credit Card XX1111 towards your last month spends - Axis Bank",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `non-matching body returns null`() {
        axisCashback(
            SmsInput(
                sender = "JD-AXISBK-S",
                body = "Spent INR 100.00\nAxis Bank Card no. XX1111\n01-01-26 12:00:00 IST\nBHARAT PETR\nAvl Limit: INR 50000",
            ),
        ).shouldBeNull()
    }
}
