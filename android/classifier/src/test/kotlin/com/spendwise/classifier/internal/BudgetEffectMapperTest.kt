package com.spendwise.classifier.internal

import com.spendwise.classifier.BudgetEffect
import com.spendwise.parser.Classification
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Enforces the `classification → budget_effect` table from
 * business-logic-spec.md §2.4. Every Classification value must map
 * to exactly one BudgetEffect.
 *
 * Locked-down; a change here changes budget math semantics and must
 * be a deliberate, reviewed spec change.
 */
class BudgetEffectMapperTest {

    @Test
    fun `CC_TRANSACTION maps to SPEND`() {
        BudgetEffectMapper.effectFor(Classification.CC_TRANSACTION) shouldBe BudgetEffect.SPEND
    }

    @Test
    fun `UPI_PAYMENT maps to SPEND`() {
        BudgetEffectMapper.effectFor(Classification.UPI_PAYMENT) shouldBe BudgetEffect.SPEND
    }

    @Test
    fun `DEBIT_CARD maps to SPEND`() {
        BudgetEffectMapper.effectFor(Classification.DEBIT_CARD) shouldBe BudgetEffect.SPEND
    }

    @Test
    fun `ATM_WITHDRAWAL maps to SPEND`() {
        BudgetEffectMapper.effectFor(Classification.ATM_WITHDRAWAL) shouldBe BudgetEffect.SPEND
    }

    @Test
    fun `CC_BILL_PAYMENT maps to DROP`() {
        BudgetEffectMapper.effectFor(Classification.CC_BILL_PAYMENT) shouldBe BudgetEffect.DROP
    }

    @Test
    fun `REFUND maps to REFUND`() {
        BudgetEffectMapper.effectFor(Classification.REFUND) shouldBe BudgetEffect.REFUND
    }

    @Test
    fun `CASHBACK maps to DROP`() {
        BudgetEffectMapper.effectFor(Classification.CASHBACK) shouldBe BudgetEffect.DROP
    }

    @Test
    fun `INCOMING_CREDIT maps to INCOME`() {
        BudgetEffectMapper.effectFor(Classification.INCOMING_CREDIT) shouldBe BudgetEffect.INCOME
    }

    @Test
    fun `SELF_TRANSFER maps to DROP`() {
        BudgetEffectMapper.effectFor(Classification.SELF_TRANSFER) shouldBe BudgetEffect.DROP
    }

    @Test
    fun `OTP maps to DROP`() {
        BudgetEffectMapper.effectFor(Classification.OTP) shouldBe BudgetEffect.DROP
    }

    @Test
    fun `BALANCE_ALERT maps to DROP`() {
        BudgetEffectMapper.effectFor(Classification.BALANCE_ALERT) shouldBe BudgetEffect.DROP
    }

    @Test
    fun `NON_FINANCIAL maps to DROP`() {
        BudgetEffectMapper.effectFor(Classification.NON_FINANCIAL) shouldBe BudgetEffect.DROP
    }

    @Test
    fun `UNMATCHED maps to DROP`() {
        BudgetEffectMapper.effectFor(Classification.UNMATCHED) shouldBe BudgetEffect.DROP
    }

    @Test
    fun `every Classification has a mapping — no MissingEffect exceptions`() {
        // Guards against a new Classification enum value being added without
        // updating the mapper. The when-block must remain exhaustive.
        Classification.entries.forEach { BudgetEffectMapper.effectFor(it) }
    }
}
