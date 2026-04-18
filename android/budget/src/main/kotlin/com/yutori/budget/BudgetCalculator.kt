package com.yutori.budget

import com.yutori.classifier.BudgetEffect

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
     *
     * Implementation note: single-pass O(T + P). The obvious loop would
     * call [monthSpend]+[monthRefunds] per prior budget month, each
     * scanning the full transaction list (O(P × T) — ~50k ops per
     * dashboard render at 7k+ SMS scale, on the main thread). Instead
     * we build a month→net-spend map in one pass over transactions,
     * then sum (limit − net) across prior budgets. Behaviour is
     * identical; the old form is preserved in tests as the oracle.
     */
    fun carryOver(
        transactions: List<Transaction>,
        budgets: List<Budget>,
        upToMonth: String,
    ): Double {
        val priorBudgets = budgets.filter { it.monthKey < upToMonth }
        if (priorBudgets.isEmpty()) return 0.0

        val priorMonthKeys = priorBudgets.mapTo(HashSet(priorBudgets.size)) { it.monthKey }
        val netByMonth = HashMap<String, Double>(priorBudgets.size)
        for (tx in transactions) {
            if (tx.monthKey !in priorMonthKeys) continue
            val amount = tx.inrAmount ?: continue
            when (tx.budgetEffect) {
                BudgetEffect.SPEND -> netByMonth.merge(tx.monthKey, amount, Double::plus)
                BudgetEffect.REFUND -> netByMonth.merge(tx.monthKey, -amount, Double::plus)
                else -> Unit
            }
        }
        return priorBudgets.sumOf { b -> b.limitInr - (netByMonth[b.monthKey] ?: 0.0) }
    }

    /**
     * Full snapshot for a month. Convenience wrapper.
     *
     * [currentMonthLimit], when non-null, overrides the viewed month's
     * limit — used for #14 (budgets roll forward). Callers that have
     * already resolved the limit (explicit or inherited) pass it here
     * directly and can leave [budgets] as prior-only. When null,
     * falls back to scanning [budgets] for a row keyed on [monthKey].
     *
     * Carry-over is unchanged either way — it only walks months
     * strictly before [monthKey], so inherited months (no explicit
     * row) never contribute a fresh `(limit − net)` term (§6.6).
     */
    fun snapshot(
        transactions: List<Transaction>,
        budgets: List<Budget>,
        monthKey: String,
        currentMonthLimit: Double? = null,
    ): MonthSnapshot {
        val limit = currentMonthLimit
            ?: budgets.firstOrNull { it.monthKey == monthKey }?.limitInr
            ?: 0.0
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
