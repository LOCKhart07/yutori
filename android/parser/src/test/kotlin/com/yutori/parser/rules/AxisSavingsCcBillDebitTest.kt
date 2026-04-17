package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.ParseResult
import com.yutori.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AxisSavingsCcBillDebitTest {

    @Test
    fun `debit integer amount`() {
        val result = axisSavingsCcBillDebit(
            SmsInput(
                sender = "VK-AXISBK-S",
                body = "Debit INR 1000.00\n" +
                    "Axis Bank A/c XX2222\n" +
                    "01-01-26 12:00:00\n" +
                    "CreditCard Payment XX 1111\n" +
                    "WhatsApp BAL to 917036165000\n" +
                    "Not You? SMS BLOCKALL CustID to 919951860002",
            ),
        )
        result shouldBe ParseResult(
            classification = Classification.CC_BILL_PAYMENT,
            amount = 1000.0,
            currency = "INR",
            merchant = null,
            last4 = "2222",
            category = null,
            pattern = "axis_savings_cc_bill_debit",
        )
    }

    @Test
    fun `debit with decimal amount`() {
        // Constructed to cover the decimal-amount shape — Python regex accepts it.
        val result = axisSavingsCcBillDebit(
            SmsInput(
                sender = "VK-AXISBK-S",
                body = "Debit INR 500.00\n" +
                    "Axis Bank A/c XX2222\n" +
                    "01-01-26 12:00:00\n" +
                    "CreditCard Payment XX 1111\n" +
                    "Not You? SMS BLOCKALL",
            ),
        )
        result shouldBe ParseResult(
            classification = Classification.CC_BILL_PAYMENT,
            amount = 500.00,
            currency = "INR",
            merchant = null,
            last4 = "2222",
            category = null,
            pattern = "axis_savings_cc_bill_debit",
        )
    }

    @Test
    fun `wrong sender returns null`() {
        axisSavingsCcBillDebit(
            SmsInput(
                sender = "VK-KOTAKB-S",
                body = "Debit INR 1000.00\n" +
                    "Axis Bank A/c XX2222\n" +
                    "01-01-26 12:00:00\n" +
                    "CreditCard Payment XX 1111",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `malformed body without CreditCard Payment line returns null`() {
        axisSavingsCcBillDebit(
            SmsInput(
                sender = "VK-AXISBK-S",
                body = "Debit INR 1000.00\n" +
                    "Axis Bank A/c XX2222\n" +
                    "01-01-26 12:00:00\n" +
                    "UPI Payment XX 1111",
            ),
        ).shouldBeNull()
    }
}
