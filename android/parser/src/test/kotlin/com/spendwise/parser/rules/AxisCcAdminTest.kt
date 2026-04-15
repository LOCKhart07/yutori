package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AxisCcAdminTest {

    @Test
    fun `New PIN generated for Axis CC is NON_FINANCIAL`() {
        val result = axisCcAdmin(
            SmsInput(
                sender = "VM-AXISBK-S",
                body = "New PIN for Axis Bank Credit Card no. XX4444 has been generated. " +
                    "Transact now at a store near you & enjoy the card benefits!",
            ),
        )!!
        result.classification shouldBe Classification.NON_FINANCIAL
        result.amount shouldBe null
        result.currency shouldBe "INR"
        result.merchant shouldBe null
        result.last4 shouldBe null
        result.category shouldBe null
        result.pattern shouldBe "axis_cc_admin"
    }

    @Test
    fun `replacement card dispatched matches`() {
        val result = axisCcAdmin(
            SmsInput(
                sender = "VM-AXISBK-S",
                body = "Your replace Ace Credit Card no. XX4444 has been dispatched to " +
                    "ADDRESS on 01-01-26 via BLUEDART 00000000000 courier.",
            ),
        )!!
        result.classification shouldBe Classification.NON_FINANCIAL
        result.pattern shouldBe "axis_cc_admin"
    }

    @Test
    fun `limit options updated matches`() {
        val result = axisCcAdmin(
            SmsInput(
                sender = "VM-AXISBK-S",
                body = "As per your request, the usage & transaction limit options have been " +
                    "updated on Axis Bank Credit Card XX1111. Call 18001035577, if not " +
                    "requested by you - Axis Bank",
            ),
        )!!
        result.classification shouldBe Classification.NON_FINANCIAL
        result.pattern shouldBe "axis_cc_admin"
    }

    @Test
    fun `SR no 'for' variant matches`() {
        val result = axisCcAdmin(
            SmsInput(
                sender = "VM-AXISBK-S",
                body = "Thank you for contacting Axis Bank. Your SR no. is SAK00000000000 " +
                    "for unauthorised txn on Card no. XX1111.",
            ),
        )!!
        result.classification shouldBe Classification.NON_FINANCIAL
        result.pattern shouldBe "axis_cc_admin"
    }

    @Test
    fun `SR no 'related to' variant matches`() {
        val result = axisCcAdmin(
            SmsInput(
                sender = "VM-AXISBK-S",
                body = "We are pleased to inform you that your SR no. 000000000000 related " +
                    "to blocking payment channel has been resolved - Axis Bank",
            ),
        )!!
        result.classification shouldBe Classification.NON_FINANCIAL
        result.pattern shouldBe "axis_cc_admin"
    }

    @Test
    fun `scheduled maintenance pause matches`() {
        val result = axisCcAdmin(
            SmsInput(
                sender = "VM-AXISBK-S",
                body = "Due to scheduled maintenance, txns. with Axis Bank Credit Card on " +
                    "01-01-26, 01:45 AM - 02:30 AM IST will be paused. Inconvenience caused " +
                    "is regretted.",
            ),
        )!!
        result.classification shouldBe Classification.NON_FINANCIAL
        result.pattern shouldBe "axis_cc_admin"
    }

    @Test
    fun `'Thank you for contacting Axis Bank' matches`() {
        val result = axisCcAdmin(
            SmsInput(
                sender = "VM-AXISBK-S",
                body = "Thank you for contacting Axis Bank regarding your query.",
            ),
        )!!
        result.classification shouldBe Classification.NON_FINANCIAL
        result.pattern shouldBe "axis_cc_admin"
    }

    @Test
    fun `non-AXISBK sender returns null even with matching body`() {
        axisCcAdmin(
            SmsInput(
                sender = "VM-HDFCBK-S",
                body = "New PIN for Axis Bank Credit Card no. XX4444 has been generated.",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `AXISBK sender with unrelated body returns null`() {
        axisCcAdmin(
            SmsInput(
                sender = "VM-AXISBK-S",
                body = "INR 100 spent on Axis Bank Credit Card XX1111 on 01-Jan-2026.",
            ),
        ).shouldBeNull()
    }
}
