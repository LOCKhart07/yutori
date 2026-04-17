package com.yutori.parser.rules

import com.yutori.parser.Category
import com.yutori.parser.Classification
import com.yutori.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class KotakUpiDebitTest {

    @Test
    fun `T1 - Kotak UPI debit to a regular VPA is UPI_PAYMENT`() {
        val result = kotakUpiDebit(
            SmsInput(
                sender = "JD-KOTAKB-S",
                body = "Sent Rs.100.00 from Kotak Bank AC X0000 to 0000000000000000.bqr@kotak on 01-01-26",
            ),
        )!!
        result.classification shouldBe Classification.UPI_PAYMENT
        result.amount shouldBe 100.00
        result.currency shouldBe "INR"
        result.merchant shouldBe "0000000000000000.bqr@kotak"
        result.last4 shouldBe "0000"
        result.category shouldBe Category.UPI_TRANSFER
        result.pattern shouldBe "kotak_upi_debit"
    }

    @Test
    fun `T2 - Kotak UPI debit to CRED at axisb is CC_BILL_PAYMENT`() {
        val result = kotakUpiDebit(
            SmsInput(
                sender = "JD-KOTAKB-S",
                body = "Sent Rs.1000.00 from Kotak Bank AC X0000 to cred.club@axisb on 01-01-26",
            ),
        )!!
        result.classification shouldBe Classification.CC_BILL_PAYMENT
        result.amount shouldBe 1000.00
        result.merchant shouldBe "cred.club@axisb"
        result.last4 shouldBe "0000"
        result.category shouldBe null
        result.pattern shouldBe "kotak_upi_debit"
    }

    @Test
    fun `T3 - CRED encoding glitch with inverted exclamation still matches`() {
        val result = kotakUpiDebit(
            SmsInput(
                sender = "JD-KOTAKB-S",
                body = "Sent Rs.500.00 from Kotak Bank AC X0000 to cred.club\u00A1axisb on 01-01-26",
            ),
        )!!
        result.classification shouldBe Classification.CC_BILL_PAYMENT
        result.amount shouldBe 500.00
        result.merchant shouldBe "cred.club\u00A1axisb"
        result.last4 shouldBe "0000"
        result.category shouldBe null
        result.pattern shouldBe "kotak_upi_debit"
    }

    @Test
    fun `wrong sender returns null`() {
        kotakUpiDebit(
            SmsInput(
                sender = "VK-HDFCBK",
                body = "Sent Rs.100.00 from Kotak Bank AC X0000 to foo@bar on 01-01-26",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `non-matching body returns null`() {
        kotakUpiDebit(
            SmsInput(
                sender = "JD-KOTAKB-S",
                body = "INR 100 spent on Kotak Credit Card x3333 on 01-Jan-2026 at XYZ. Avl limit ...",
            ),
        ).shouldBeNull()
    }
}
