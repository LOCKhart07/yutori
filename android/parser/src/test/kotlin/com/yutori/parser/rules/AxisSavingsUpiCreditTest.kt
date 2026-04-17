package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AxisSavingsUpiCreditTest {

    @Test
    fun `Axis savings UPI credit from P2A transfer`() {
        val result = axisSavingsUpiCredit(
            SmsInput(
                sender = "JD-AXISBK-S",
                body = "INR 100.00 credited\nA/c no. XX2222\n01-01-26, 12:00:00 IST\nUPI/P2A/000000000000/EXAMPLE E/KKBK/Cc p - Axis Bank",
            ),
        )!!
        result.classification shouldBe Classification.INCOMING_CREDIT
        result.amount shouldBe 100.00
        result.currency shouldBe "INR"
        result.merchant shouldBe null
        result.last4 shouldBe "2222"
        result.category shouldBe null
        result.pattern shouldBe "axis_savings_upi_credit"
    }

    @Test
    fun `Axis savings UPI credit second fixture`() {
        val result = axisSavingsUpiCredit(
            SmsInput(
                sender = "AX-AXISBK-S",
                body = "INR 50.00 credited\nA/c no. XX2222\n01-01-26, 12:00:00 IST\nUPI/P2A/000000000000/EXAMPLE E/BACB/Jeet - Axis Bank",
            ),
        )!!
        result.amount shouldBe 50.00
        result.last4 shouldBe "2222"
        result.merchant shouldBe null
        result.pattern shouldBe "axis_savings_upi_credit"
    }

    @Test
    fun `wrong sender returns null`() {
        axisSavingsUpiCredit(
            SmsInput(
                sender = "VK-KOTAKB",
                body = "INR 100.00 credited\nA/c no. XX0000\n01-01-26, 12:00:00 IST\n",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `Axis CC spend body does not match savings credit rule`() {
        axisSavingsUpiCredit(
            SmsInput(
                sender = "JD-AXISBK-S",
                body = "Spent INR 100.00\nAxis Bank Card no. XX1111\n01-01-26 12:00:00 IST\nBHARAT PETR\nAvl Limit: INR 100",
            ),
        ).shouldBeNull()
    }
}
