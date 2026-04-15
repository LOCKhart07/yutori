package com.spendwise.ui

/**
 * Shared pretty-printer for [com.spendwise.parser.Category] enum names
 * (e.g. FOOD_DINING → "Food & Dining"). Dashboard, drill-down, and
 * detail screens all consume this.
 */
fun prettyCategory(name: String): String = when (name) {
    "FOOD_DINING" -> "Food & Dining"
    "TRAVEL_TRANSPORT" -> "Travel & Transport"
    "BILLS_UTILITIES" -> "Bills & Utilities"
    "UPI_TRANSFER" -> "UPI Transfer"
    else -> name.lowercase().replaceFirstChar { it.titlecase() }
}
