package com.spendwise.transactions.internal

import com.spendwise.transactions.SourceRole
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RoleAssignerTest {

    @Test
    fun `kotak_upi_debit maps to BANK_DEBIT`() {
        RoleAssigner.roleFor("kotak_upi_debit") shouldBe SourceRole.BANK_DEBIT
    }

    @Test
    fun `kotak_cc_spend maps to BANK_DEBIT`() {
        RoleAssigner.roleFor("kotak_cc_spend") shouldBe SourceRole.BANK_DEBIT
    }

    @Test
    fun `axis_cc_spend maps to BANK_DEBIT`() {
        RoleAssigner.roleFor("axis_cc_spend") shouldBe SourceRole.BANK_DEBIT
    }

    @Test
    fun `kotak_neft_credit maps to BANK_DEBIT`() {
        RoleAssigner.roleFor("kotak_neft_credit") shouldBe SourceRole.BANK_DEBIT
    }

    @Test
    fun `axis_cashback maps to BANK_DEBIT`() {
        RoleAssigner.roleFor("axis_cashback") shouldBe SourceRole.BANK_DEBIT
    }

    @Test
    fun `vjsbnk_interest maps to BANK_DEBIT`() {
        RoleAssigner.roleFor("vjsbnk_interest") shouldBe SourceRole.BANK_DEBIT
    }

    @Test
    fun `icici_eazypay maps to GATEWAY`() {
        RoleAssigner.roleFor("icici_eazypay") shouldBe SourceRole.GATEWAY
    }

    @Test
    fun `paytm_sebi_credit maps to GATEWAY`() {
        RoleAssigner.roleFor("paytm_sebi_credit") shouldBe SourceRole.GATEWAY
    }

    @Test
    fun `axis_cc_bill_received maps to CC_PAYMENT_RECEIPT`() {
        RoleAssigner.roleFor("axis_cc_bill_received") shouldBe
            SourceRole.CC_PAYMENT_RECEIPT
    }

    @Test
    fun `kotak_cc_bill_received maps to CC_PAYMENT_RECEIPT`() {
        RoleAssigner.roleFor("kotak_cc_bill_received") shouldBe
            SourceRole.CC_PAYMENT_RECEIPT
    }

    @Test
    fun `blinkit_refund maps to MERCHANT_ACK`() {
        RoleAssigner.roleFor("blinkit_refund") shouldBe SourceRole.MERCHANT_ACK
    }

    @Test
    fun `unknown pattern falls back to DUPLICATE_NOTIF`() {
        RoleAssigner.roleFor("some_future_rule") shouldBe SourceRole.DUPLICATE_NOTIF
    }

    @Test
    fun `UNMATCHED pattern falls back to DUPLICATE_NOTIF`() {
        RoleAssigner.roleFor("UNMATCHED") shouldBe SourceRole.DUPLICATE_NOTIF
    }
}
