package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class KotakUpiCreditTest {

    @Test
    fun `Kotak UPI credit from axisbank VPA`() {
        val result = kotakUpiCredit(
            SmsInput(
                sender = "JD-KOTAKB-S",
                body = "Received Rs.100.00 in your Kotak Bank AC X0000 from goog-payments@axisbank on 01-01-26.UPI Ref:000000000000.",
            ),
        )!!
        result.classification shouldBe Classification.INCOMING_CREDIT
        result.amount shouldBe 100.00
        result.currency shouldBe "INR"
        result.merchant shouldBe "goog-payments@axisbank"
        result.last4 shouldBe "0000"
        result.category shouldBe null
        result.pattern shouldBe "kotak_upi_credit"
    }

    @Test
    fun `Kotak UPI credit from okicici VPA`() {
        val result = kotakUpiCredit(
            SmsInput(
                sender = "JD-KOTAKB-S",
                body = "Received Rs.50.00 in your Kotak Bank AC X0000 from friendone-1@okicici on 01-01-26.UPI Ref:000000000000.",
            ),
        )!!
        result.classification shouldBe Classification.INCOMING_CREDIT
        result.amount shouldBe 50.00
        result.merchant shouldBe "friendone-1@okicici"
        result.last4 shouldBe "0000"
        result.pattern shouldBe "kotak_upi_credit"
    }

    @Test
    fun `wrong sender returns null`() {
        kotakUpiCredit(
            SmsInput(
                sender = "VK-AXISBK",
                body = "Received Rs.100.00 in your Kotak Bank AC X0000 from foo@bar on 01-01-26.",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `body missing Received Rs prefix returns null`() {
        kotakUpiCredit(
            SmsInput(
                sender = "JD-KOTAKB-S",
                body = "Sent Rs.100.00 from Kotak Bank AC X0000 to foo@bar on 01-01-26",
            ),
        ).shouldBeNull()
    }
}
