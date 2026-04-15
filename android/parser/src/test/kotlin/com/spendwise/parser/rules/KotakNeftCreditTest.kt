package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class KotakNeftCreditTest {

    @Test
    fun `Kotak NEFT salary credit with trailing dot in beneficiary`() {
        val result = kotakNeftCredit(
            SmsInput(
                sender = "AX-KOTAKB-S",
                body = "Rs. 50000.00 credited to your Kotak Bank a/c XX0000 via NEFT from beneficiary ACME CORP. UTR Ref. HDFCH00000000001",
            ),
        )!!
        result.classification shouldBe Classification.INCOMING_CREDIT
        result.amount shouldBe 50000.00
        result.currency shouldBe "INR"
        result.merchant shouldBe "ACME CORP"
        result.last4 shouldBe "0000"
        result.category shouldBe null
        result.pattern shouldBe "kotak_neft_credit"
    }

    @Test
    fun `Kotak NEFT credit second fixture`() {
        val result = kotakNeftCredit(
            SmsInput(
                sender = "VM-KOTAKB-S",
                body = "Rs. 60000.00 credited to your Kotak Bank a/c XX0000 via NEFT from beneficiary ACME CORP. UTR Ref. HDFCH00000000002",
            ),
        )!!
        result.amount shouldBe 60000.00
        result.merchant shouldBe "ACME CORP"
        result.last4 shouldBe "0000"
        result.pattern shouldBe "kotak_neft_credit"
    }

    @Test
    fun `wrong sender returns null`() {
        kotakNeftCredit(
            SmsInput(
                sender = "VK-AXISBK",
                body = "Rs. 50000.00 credited to your Kotak Bank a/c XX0000 via NEFT from beneficiary ACME CORP. UTR Ref. HDFCH00000000001",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `UPI credit body does not match NEFT rule`() {
        kotakNeftCredit(
            SmsInput(
                sender = "JD-KOTAKB-S",
                body = "Received Rs.100.00 in your Kotak Bank AC X0000 from foo@bar on 01-01-26.",
            ),
        ).shouldBeNull()
    }
}
