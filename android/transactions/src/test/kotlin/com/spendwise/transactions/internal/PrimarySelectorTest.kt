package com.spendwise.transactions.internal

import com.spendwise.transactions.SourceRole
import com.spendwise.transactions.TransactionSource
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PrimarySelectorTest {

    private fun primary(role: SourceRole) = TransactionSource(
        transactionId = 1, smsLogId = 1, role = role, isPrimary = true,
    )

    @Test
    fun `BANK_DEBIT beats GATEWAY`() {
        PrimarySelector.shouldPromote(SourceRole.BANK_DEBIT, primary(SourceRole.GATEWAY)) shouldBe true
    }

    @Test
    fun `BANK_DEBIT beats MERCHANT_ACK`() {
        PrimarySelector.shouldPromote(SourceRole.BANK_DEBIT, primary(SourceRole.MERCHANT_ACK)) shouldBe true
    }

    @Test
    fun `GATEWAY beats MERCHANT_ACK`() {
        PrimarySelector.shouldPromote(SourceRole.GATEWAY, primary(SourceRole.MERCHANT_ACK)) shouldBe true
    }

    @Test
    fun `MERCHANT_ACK does not beat GATEWAY`() {
        PrimarySelector.shouldPromote(SourceRole.MERCHANT_ACK, primary(SourceRole.GATEWAY)) shouldBe false
    }

    @Test
    fun `DUPLICATE_NOTIF never promotes`() {
        PrimarySelector.shouldPromote(SourceRole.DUPLICATE_NOTIF, primary(SourceRole.MERCHANT_ACK)) shouldBe false
    }

    @Test
    fun `same role does not promote - stability wins ties`() {
        PrimarySelector.shouldPromote(SourceRole.BANK_DEBIT, primary(SourceRole.BANK_DEBIT)) shouldBe false
    }

    @Test
    fun `CC_PAYMENT_RECEIPT beats MERCHANT_ACK`() {
        PrimarySelector.shouldPromote(SourceRole.CC_PAYMENT_RECEIPT, primary(SourceRole.MERCHANT_ACK)) shouldBe true
    }

    @Test
    fun `CC_PAYMENT_RECEIPT does not beat GATEWAY`() {
        PrimarySelector.shouldPromote(SourceRole.CC_PAYMENT_RECEIPT, primary(SourceRole.GATEWAY)) shouldBe false
    }
}
