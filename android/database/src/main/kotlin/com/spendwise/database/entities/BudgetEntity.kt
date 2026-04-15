package com.spendwise.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One monthly budget per schema.md `budgets`. Carry-over is NOT
 * cached here — computed on read (decision 2026-04-15 #2).
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    @ColumnInfo(name = "month_key")
    val monthKey: String,

    @ColumnInfo(name = "limit_inr")
    val limitInr: Double,

    @ColumnInfo(name = "threshold_warn_pct")
    val thresholdWarnPct: Int = 80,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,

    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
)
