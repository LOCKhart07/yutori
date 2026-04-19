package com.yutori.transactions

import com.yutori.classifier.BudgetEffect
import com.yutori.parser.Category
import com.yutori.parser.Classification

/**
 * A single row of the `transactions` table — the derived budget layer
 * (schema.md §transactions). One row per logical money-movement event;
 * one event may be described by multiple `sms_log` rows (§12.3), which
 * are tracked in [TransactionSource].
 *
 * This domain type mirrors the Room entity field-for-field but lives
 * outside the Android dependency so the transactions module stays
 * pure-JVM.
 */
data class TransactionRow(
    val id: Long,
    val classification: Classification,
    val classificationOriginal: Classification?,
    val budgetEffect: BudgetEffect,
    val inrAmount: Double?,                // null = pending forex
    val originalAmount: Double?,
    val originalCurrency: String,
    val exchangeRate: Double? = null,
    val rateSource: String? = null,        // "exchangerate-api.com" | "manual" | "pending"
    val merchant: String?,
    val merchantKey: String?,
    val category: Category?,
    val accountId: Long?,
    val last4: String?,
    val issuer: String? = null,
    val occurredAtMs: Long,
    val monthKey: String,
    val manuallyAdjusted: Boolean = false,
    val categoryOverride: Boolean = false,
    val classificationOverride: Boolean = false,
    val classificationInferred: Classification? = null,
    val categoryInferred: Category? = null,
)
