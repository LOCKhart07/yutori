package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DeclinedTest {

    @Test
    fun `Axis CC declined due to security reasons is NON_FINANCIAL`() {
        val result = declined(
            SmsInput(
                sender = "VM-AXISBK-S",
                body = "Transaction on Axis Bank Credit Card no. XX1111 has been declined " +
                    "due to security reasons. Please call 18001035577 for details - Axis Bank",
            ),
        )!!
        result.classification shouldBe Classification.NON_FINANCIAL
        result.amount shouldBe null
        result.currency shouldBe "INR"
        result.merchant shouldBe null
        result.last4 shouldBe null
        result.category shouldBe null
        result.pattern shouldBe "declined"
    }

    @Test
    fun `Kotak CC declined for incorrect CVC matches (lowercase anywhere)`() {
        val result = declined(
            SmsInput(
                sender = "VM-KOTAKB-S",
                body = "Txn of INR 100.00 at WWW EXAMPLE COM on Kotak Credit Card x3333 " +
                    "declined as incorrect CVC entered. Please enter correct CVC.",
            ),
        )!!
        result.classification shouldBe Classification.NON_FINANCIAL
        result.pattern shouldBe "declined"
    }

    @Test
    fun `body without declined keyword returns null`() {
        declined(
            SmsInput(
                sender = "VM-AXISBK-S",
                body = "INR 100 spent on Axis Bank Credit Card XX1111 on 01-Jan-2026.",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `substring 'underlined' does not match (word boundary)`() {
        declined(
            SmsInput(
                sender = "XX-FOO",
                body = "The terms are underlined in the statement.",
            ),
        ).shouldBeNull()
    }
}
