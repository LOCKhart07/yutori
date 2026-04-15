package com.spendwise.budget

import com.spendwise.classifier.BudgetEffect

/**
 * The budget-relevant projection of a `transactions` row.
 *
 * Only the fields the budget calculator needs are modeled here. The
 * full Room entity (once it exists) will have more columns; a simple
 * mapper produces these from that.
 *
 * Using our own type keeps the budget module testable without Room.
 */
data class Transaction(
    val id: Long,
    val monthKey: String,           // YYYY-MM, computed at insert time (§6.5)
    val inrAmount: Double?,         // null for pending forex (§5.2)
    val budgetEffect: BudgetEffect,
    val occurredAtMs: Long,
)
