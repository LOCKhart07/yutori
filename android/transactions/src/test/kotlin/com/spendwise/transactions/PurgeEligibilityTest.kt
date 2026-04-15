package com.spendwise.transactions

import com.spendwise.parser.Classification
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PurgeEligibilityTest {

    @Test
    fun `NON_FINANCIAL is purge-eligible regardless of sender`() {
        PurgeEligibility.isPurgeEligible(Classification.NON_FINANCIAL, "+919876543210") shouldBe true
        PurgeEligibility.isPurgeEligible(Classification.NON_FINANCIAL, "VK-KOTAKB-S") shouldBe true
    }

    @Test
    fun `OTP is purge-eligible`() {
        PurgeEligibility.isPurgeEligible(Classification.OTP, "VK-HDFC-S") shouldBe true
    }

    @Test
    fun `BALANCE_ALERT is purge-eligible`() {
        PurgeEligibility.isPurgeEligible(Classification.BALANCE_ALERT, "AX-AXISBK-S") shouldBe true
    }

    @Test
    fun `UNMATCHED from non-financial sender is purge-eligible`() {
        PurgeEligibility.isPurgeEligible(Classification.UNMATCHED, "JD-MYNTRA-S") shouldBe true
        PurgeEligibility.isPurgeEligible(Classification.UNMATCHED, "+919876543210") shouldBe true
    }

    @Test
    fun `UNMATCHED from KOTAKB sender is NOT purge-eligible`() {
        // Highest-priority parser gap signal per plan §2.
        PurgeEligibility.isPurgeEligible(Classification.UNMATCHED, "VK-KOTAKB-S") shouldBe false
    }

    @Test
    fun `UNMATCHED from AXISBK sender is NOT purge-eligible`() {
        PurgeEligibility.isPurgeEligible(Classification.UNMATCHED, "AX-AXISBK-S") shouldBe false
    }

    @Test
    fun `UNMATCHED from ICICI sender is NOT purge-eligible`() {
        PurgeEligibility.isPurgeEligible(Classification.UNMATCHED, "VM-ICICIB-S") shouldBe false
    }

    @Test
    fun `UNMATCHED from HDFC sender is NOT purge-eligible`() {
        PurgeEligibility.isPurgeEligible(Classification.UNMATCHED, "VK-HDFCBK-S") shouldBe false
    }

    @Test
    fun `CC_TRANSACTION is never purge-eligible`() {
        PurgeEligibility.isPurgeEligible(Classification.CC_TRANSACTION, "VK-KOTAKB-S") shouldBe false
    }

    @Test
    fun `UPI_PAYMENT is never purge-eligible`() {
        PurgeEligibility.isPurgeEligible(Classification.UPI_PAYMENT, "VK-KOTAKB-S") shouldBe false
    }

    @Test
    fun `REFUND is never purge-eligible`() {
        PurgeEligibility.isPurgeEligible(Classification.REFUND, "JK-blnkit-S") shouldBe false
    }

    @Test
    fun `INCOMING_CREDIT is never purge-eligible`() {
        PurgeEligibility.isPurgeEligible(Classification.INCOMING_CREDIT, "VK-KOTAKB-S") shouldBe false
    }

    @Test
    fun `CASHBACK is never purge-eligible`() {
        PurgeEligibility.isPurgeEligible(Classification.CASHBACK, "AX-AXISBK-S") shouldBe false
    }

    @Test
    fun `CC_BILL_PAYMENT is never purge-eligible`() {
        PurgeEligibility.isPurgeEligible(Classification.CC_BILL_PAYMENT, "AX-AXISBK-S") shouldBe false
    }

    @Test
    fun `SELF_TRANSFER is never purge-eligible`() {
        PurgeEligibility.isPurgeEligible(Classification.SELF_TRANSFER, "VK-KOTAKB-S") shouldBe false
    }
}
