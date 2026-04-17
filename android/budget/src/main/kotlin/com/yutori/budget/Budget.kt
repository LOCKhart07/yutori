package com.yutori.budget

/**
 * A user-set monthly budget. One row per [monthKey].
 * See schema.md `budgets` table and business-logic-spec.md §6.
 */
data class Budget(
    val monthKey: String,
    val limitInr: Double,
    val warnThresholdPct: Int = 80,   // user-adjustable §7.1, default 80
)
