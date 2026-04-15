package com.spendwise.parser.rules

import com.spendwise.parser.Category
import com.spendwise.parser.Classification
import com.spendwise.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BlinkitRefundTest {

    @Test
    fun `Blinkit cancellation refund`() {
        val result = blinkitRefund(
            SmsInput(
                sender = "JK-blnkit-S",
                body = "We have initiated a refund of Rs.100.00 for the cancelled order ORD000000000000 into your UPI after applying a cancellation fee of Rs 20.00. The refund amount should reflect in 3-5 business days. -blinkit",
            ),
        )!!
        result.classification shouldBe Classification.REFUND
        result.amount shouldBe 100.00
        result.currency shouldBe "INR"
        result.merchant shouldBe "Blinkit"
        result.category shouldBe Category.UNCATEGORIZED
        result.last4 shouldBe null
        result.pattern shouldBe "blinkit_refund"
    }

    @Test
    fun `sender match is case-insensitive`() {
        val result = blinkitRefund(
            SmsInput(
                sender = "VM-BLNKIT-T",
                body = "We have initiated a refund of Rs.100.00 for the cancelled order ORD999 into your UPI",
            ),
        )!!
        result.amount shouldBe 100.00
        result.merchant shouldBe "Blinkit"
    }

    @Test
    fun `wrong sender returns null`() {
        blinkitRefund(
            SmsInput(
                sender = "JD-AXISBK-S",
                body = "We have initiated a refund of Rs.100.00 for the cancelled order ORD000000000000 into your UPI",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `non-matching body returns null`() {
        blinkitRefund(
            SmsInput(
                sender = "JK-blnkit-S",
                body = "Your Blinkit order has been dispatched and will arrive shortly",
            ),
        ).shouldBeNull()
    }
}
