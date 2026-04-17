package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CcStatementGeneratedTest {

    @Test
    fun `Axis 'statement for Credit Card is generated' pattern`() {
        val result = ccStatementGenerated(
            SmsInput(
                sender = "VM-AXISBK-S",
                body = "Your statement for Axis Bank Credit Card no. XX1111 is generated.\n" +
                    "Due on: 01-01-26\nTotal amt: INR  Dr. 500.00\nMin amt due: INR  Dr. 100.00",
            ),
        )!!
        result.classification shouldBe Classification.BALANCE_ALERT
        result.amount shouldBe null
        result.currency shouldBe "INR"
        result.merchant shouldBe null
        result.last4 shouldBe null
        result.category shouldBe null
        result.pattern shouldBe "cc_statement_generated"
    }

    @Test
    fun `CRED bill 'for X has been generated' pattern`() {
        val result = ccStatementGenerated(
            SmsInput(
                sender = "VM-CREDIT-S",
                body = "Your credit card bill for Axis Bank XXXX-1111 has been generated.\n\n" +
                    "Total amount: INR 500.00\nDue date: January 01, 2026",
            ),
        )!!
        result.classification shouldBe Classification.BALANCE_ALERT
        result.pattern shouldBe "cc_statement_generated"
    }

    @Test
    fun `CRED bill 'has been generated' without 'for' clause`() {
        val result = ccStatementGenerated(
            SmsInput(
                sender = "VM-CREDIT-S",
                body = "Update from CRED:\n\nYour credit card bill has been generated.\n\n" +
                    "Total amount: INR 1,000.00\nDue Date: January 01, 2026",
            ),
        )!!
        result.classification shouldBe Classification.BALANCE_ALERT
        result.pattern shouldBe "cc_statement_generated"
    }

    @Test
    fun `statement for Credit Card X-digits Total Due variant matches case-insensitively`() {
        val result = ccStatementGenerated(
            SmsInput(
                sender = "VM-BANK-S",
                body = "Your statement for Credit Card X1234 Total Due: INR 500.",
            ),
        )!!
        result.classification shouldBe Classification.BALANCE_ALERT
        result.pattern shouldBe "cc_statement_generated"
    }

    @Test
    fun `unrelated body returns null`() {
        ccStatementGenerated(
            SmsInput(
                sender = "VM-AXISBK-S",
                body = "INR 100 spent on Axis Bank Credit Card XX1111 on 01-Jan-2026.",
            ),
        ).shouldBeNull()
    }
}
