package com.yutori.budget

/**
 * A budget suggestion derived from spending history (#15).
 *
 * [median] is the median net-spend over the contributing [months];
 * BudgetSetup seeds its limit field with it as a tap-to-fill chip.
 * [months] are the months that fed the median, **newest-first**, so the
 * UI can show the breakdown ("Mar 40k · Apr 45k · May 42k").
 */
data class SpendSuggestion(
    val median: Double,
    val months: List<MonthNet>,
)

/** Net spend (gross − refunds, in INR) for one [monthKey] (YYYY-MM). */
data class MonthNet(
    val monthKey: String,
    val netInr: Double,
)
