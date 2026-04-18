package com.yutori.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Derived budget layer per schema.md `transactions`.
 *
 * One row per logical money-movement event (§4.1). Supporting SMSes
 * are linked via [TransactionSourceEntity].
 *
 * No foreign key to [BudgetEntity] — `month_key` is just a denormalized
 * YYYY-MM string, and transactions exist even in months where no budget
 * was set.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["month_key"]),
        Index(value = ["account_id"]),
        Index(value = ["last4"]),
        Index(value = ["classification"]),
        Index(value = ["merchant_key"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val classification: String,

    @ColumnInfo(name = "classification_original")
    val classificationOriginal: String? = null,

    @ColumnInfo(name = "budget_effect")
    val budgetEffect: String,

    /** INR amount. Null while a forex transaction is awaiting rate fetch. */
    @ColumnInfo(name = "inr_amount")
    val inrAmount: Double?,

    @ColumnInfo(name = "original_amount")
    val originalAmount: Double?,

    @ColumnInfo(name = "original_currency")
    val originalCurrency: String,

    @ColumnInfo(name = "exchange_rate")
    val exchangeRate: Double? = null,

    /** "exchangerate-api.com" | "manual" | "pending" | null (for INR). */
    @ColumnInfo(name = "rate_source")
    val rateSource: String? = null,

    val merchant: String?,

    @ColumnInfo(name = "merchant_key")
    val merchantKey: String?,

    val category: String?,

    @ColumnInfo(name = "account_id")
    val accountId: Long?,

    val last4: String?,

    val issuer: String? = null,

    @ColumnInfo(name = "occurred_at_ms")
    val occurredAtMs: Long,

    @ColumnInfo(name = "month_key")
    val monthKey: String,

    @ColumnInfo(name = "is_manual_entry")
    val isManualEntry: Boolean = false,

    @ColumnInfo(name = "manually_adjusted")
    val manuallyAdjusted: Boolean = false,

    val notes: String? = null,

    @ColumnInfo(name = "category_override")
    val categoryOverride: Boolean = false,
)
