package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class IciciEazypayTest {

    @Test
    fun `ICICI eazypay property-tax payment`() {
        val result = iciciEazypay(
            SmsInput(
                sender = "JD-ICICIT-S",
                body = "Dear Sir/Madam, you have made a payment of Rs. 100.00 to VVCMC ONLINE PROPERTY TAX ACCOUNT vide ICICI Bank eazypay reference ID 000000000000000.",
            ),
        )!!
        result.classification shouldBe Classification.UPI_PAYMENT
        result.amount shouldBe 100.00
        result.currency shouldBe "INR"
        result.merchant shouldBe "VVCMC ONLINE PROPERTY TAX ACCOUNT"
        result.last4 shouldBe null
        result.category shouldBe null
        result.pattern shouldBe "icici_eazypay"
    }

    @Test
    fun `merchant is trimmed`() {
        val result = iciciEazypay(
            SmsInput(
                sender = "VM-ICICIT",
                body = "Dear Sir/Madam, you have made a payment of Rs. 50.00 to  SOME MERCHANT   vide ICICI Bank eazypay reference ID 123",
            ),
        )!!
        result.merchant shouldBe "SOME MERCHANT"
    }

    @Test
    fun `wrong sender returns null`() {
        iciciEazypay(
            SmsInput(
                sender = "JD-AXISBK-S",
                body = "Dear Sir/Madam, you have made a payment of Rs. 100.00 to VVCMC vide ICICI Bank eazypay reference ID 000000000000000.",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `non-matching body returns null`() {
        iciciEazypay(
            SmsInput(
                sender = "JD-ICICIT-S",
                body = "Your ICICI Bank OTP is 123456. Do not share.",
            ),
        ).shouldBeNull()
    }
}
