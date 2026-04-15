package com.spendwise.parser

/**
 * Spend category per spendwise-plan.md §5.
 *
 * Categories only apply to money-moving events (SPEND, REFUND).
 * Drop-effect classifications leave category as null on the
 * transactions row.
 */
enum class Category {
    FOOD_DINING,
    GROCERIES,
    TRAVEL_TRANSPORT,
    SHOPPING,
    BILLS_UTILITIES,
    HEALTH,
    ENTERTAINMENT,
    SUBSCRIPTIONS,
    UPI_TRANSFER,
    CASH,
    UNCATEGORIZED,
    OTHER,
}
