package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.ParseResult
import com.spendwise.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AxisCcBillReceivedTest {

    @Test
    fun `T30 - integer amount`() {
        val result = axisCcBillReceived(
            SmsInput(
                sender = "VK-AXISBK-S",
                body = "Payment of INR 1000 has been received towards your " +
                    "Axis Bank Credit Card XX1111 on 01-01-26",
            ),
        )
        result shouldBe ParseResult(
            classification = Classification.CC_BILL_PAYMENT,
            amount = 1000.0,
            currency = "INR",
            merchant = null,
            last4 = "1111",
            category = null,
            pattern = "axis_cc_bill_received",
        )
    }

    @Test
    fun `T31 - decimal amount`() {
        val result = axisCcBillReceived(
            SmsInput(
                sender = "VK-AXISBK-S",
                body = "Payment of INR 500.00 has been received towards your " +
                    "Axis Bank Credit Card XX1111 on 01-01-26",
            ),
        )
        result shouldBe ParseResult(
            classification = Classification.CC_BILL_PAYMENT,
            amount = 500.00,
            currency = "INR",
            merchant = null,
            last4 = "1111",
            category = null,
            pattern = "axis_cc_bill_received",
        )
    }

    @Test
    fun `wrong sender returns null`() {
        axisCcBillReceived(
            SmsInput(
                sender = "VK-KOTAKB-S",
                body = "Payment of INR 1000 has been received towards your " +
                    "Axis Bank Credit Card XX1111 on 01-01-26",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `malformed body returns null`() {
        axisCcBillReceived(
            SmsInput(
                sender = "VK-AXISBK-S",
                body = "Payment of INR 1000 received for something else entirely",
            ),
        ).shouldBeNull()
    }
}
