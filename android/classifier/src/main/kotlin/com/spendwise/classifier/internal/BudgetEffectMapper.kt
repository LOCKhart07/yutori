package com.spendwise.classifier.internal

import com.spendwise.classifier.BudgetEffect
import com.spendwise.parser.Classification

/**
 * Maps [Classification] → [BudgetEffect] per business-logic-spec.md §2.4.
 *
 * The `when` expression is deliberately exhaustive (no `else` branch)
 * so adding a new [Classification] enum value becomes a compile error
 * until this mapper is updated. That's the desired fail-loud behavior:
 * budget semantics for a new classification must be an explicit decision.
 */
internal object BudgetEffectMapper {

    fun effectFor(classification: Classification): BudgetEffect =
        when (classification) {
            Classification.CC_TRANSACTION -> BudgetEffect.SPEND
            Classification.UPI_PAYMENT -> BudgetEffect.SPEND
            Classification.DEBIT_CARD -> BudgetEffect.SPEND
            Classification.ATM_WITHDRAWAL -> BudgetEffect.SPEND

            Classification.REFUND -> BudgetEffect.REFUND

            Classification.INCOMING_CREDIT -> BudgetEffect.INCOME

            Classification.CC_BILL_PAYMENT -> BudgetEffect.DROP
            Classification.CASHBACK -> BudgetEffect.DROP
            Classification.SELF_TRANSFER -> BudgetEffect.DROP
            Classification.OTP -> BudgetEffect.DROP
            Classification.BALANCE_ALERT -> BudgetEffect.DROP
            Classification.NON_FINANCIAL -> BudgetEffect.DROP
            Classification.UNMATCHED -> BudgetEffect.DROP
        }
}
