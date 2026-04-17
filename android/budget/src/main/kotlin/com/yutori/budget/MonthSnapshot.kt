package com.yutori.budget

/**
 * Computed view of a single month's budget state. All fields INR.
 *
 * Derived on read from [Transaction]s and [Budget]s — never cached
 * (schema.md Decision 2). See business-logic-spec.md §6.
 *
 * Sign conventions (§6.4):
 *   - [carryOverInr] > 0 → prior months underspent; extra headroom.
 *   - [carryOverInr] < 0 → prior overspending; starting behind.
 *   - [netSpendInr] can be negative if refunds exceed gross spend in
 *     this month alone; the UI clamps that to 0 for display.
 */
data class MonthSnapshot(
    val monthKey: String,
    val limitInr: Double,
    val carryOverInr: Double,
    val effectiveBudgetInr: Double,   // limit + carryOver
    val grossSpendInr: Double,        // sum(SPEND)
    val refundsInr: Double,           // sum(REFUND) — always positive
    val netSpendInr: Double,          // grossSpend - refunds
    val percentUsed: Double,          // (netSpend / effective) * 100; 0 if effective ≤ 0
)
