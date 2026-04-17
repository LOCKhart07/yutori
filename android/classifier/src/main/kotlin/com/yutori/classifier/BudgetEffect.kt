package com.yutori.classifier

/**
 * How a classified transaction affects the monthly spend budget.
 * See business-logic-spec.md §2.4 for the classification→effect mapping.
 */
enum class BudgetEffect {
    /** Counts against the month's spend total. */
    SPEND,

    /** Offsets spend in the month it was received (§4.4). */
    REFUND,

    /** Shown separately; does not affect spend budget. */
    INCOME,

    /** Explicitly excluded from budget (OTP, alert, CC bill, self-transfer). */
    DROP,
}
