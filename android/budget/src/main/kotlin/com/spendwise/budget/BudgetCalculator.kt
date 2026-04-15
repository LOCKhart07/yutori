package com.spendwise.budget

import com.spendwise.classifier.BudgetEffect

/**
 * Pure-function budget math. No DB, no clock reads, no caching.
 *
 * Implements business-logic-spec.md §6:
 *   - monthSpend: sum of SPEND transactions in a month (§6.2).
 *   - monthRefunds: sum of REFUND transactions in a month (§6.3).
 *   - carryOver: (limit - netSpend) rolled forward across all prior
 *     months with a Budget row (§6.1, §6.6).
 *   - effectiveBudget = limit + carryOver.
 *
 * Pending-FX transactions (inrAmount == null) are excluded from every
 * sum (§5, §6.2). They reappear once the forex worker fills them in.
 */
object BudgetCalculator {

    /** Sum of SPEND transactions in [monthKey]. Excludes pending FX. */
    fun monthSpend(transactions: List<Transaction>, monthKey: String): Double =
        transactions
            .asSequence()
            .filter { it.monthKey == monthKey }
            .filter { it.budgetEffect == BudgetEffect.SPEND }
            .mapNotNull { it.inrAmount }
            .sum()

    /** Sum of REFUND transactions in [monthKey]. Always ≥ 0. */
    fun monthRefunds(transactions: List<Transaction>, monthKey: String): Double =
        transactions
            .asSequence()
            .filter { it.monthKey == monthKey }
            .filter { it.budgetEffect == BudgetEffect.REFUND }
            .mapNotNull { it.inrAmount }
            .sum()

    /**
     * Carry-over from all budget months strictly before [monthKey].
     *
     * For each prior budget month P:
     *   contribution(P) = budgets[P].limitInr - monthSpend(P) + monthRefunds(P)
     *
     * Returns 0 if no prior budget months exist (§6.6). A month with
     * transactions but no Budget row does NOT contribute — explicit
     * budget-tracking is opt-in per month.
     */
    fun carryOver(
        transactions: List<Transaction>,
        budgets: List<Budget>,
        upToMonth: String,
    ): Double =
        budgets
            .asSequence()
            .filter { it.monthKey < upToMonth }
            .sumOf { budget ->
                val gross = monthSpend(transactions, budget.monthKey)
                val refunds = monthRefunds(transactions, budget.monthKey)
                budget.limitInr - gross + refunds
            }

    /** Full snapshot for a month. Convenience wrapper. */
    fun snapshot(
        transactions: List<Transaction>,
        budgets: List<Budget>,
        monthKey: String,
    ): MonthSnapshot {
        val budget = budgets.firstOrNull { it.monthKey == monthKey }
        val limit = budget?.limitInr ?: 0.0
        val carryOverInr = carryOver(transactions, budgets, monthKey)
        val effective = limit + carryOverInr
        val gross = monthSpend(transactions, monthKey)
        val refunds = monthRefunds(transactions, monthKey)
        val net = gross - refunds
        val percent = if (effective > 0.0) (net / effective) * 100.0 else 0.0

        return MonthSnapshot(
            monthKey = monthKey,
            limitInr = limit,
            carryOverInr = carryOverInr,
            effectiveBudgetInr = effective,
            grossSpendInr = gross,
            refundsInr = refunds,
            netSpendInr = net,
            percentUsed = percent,
        )
    }
}
