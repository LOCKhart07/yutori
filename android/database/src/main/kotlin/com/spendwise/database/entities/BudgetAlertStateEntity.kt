package com.spendwise.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * "This threshold has fired for this month" rows per schema.md
 * `budget_alert_state`. Composite PK enforces the §7.2 "at most once
 * per month per threshold" invariant.
 */
@Entity(
    tableName = "budget_alert_state",
    primaryKeys = ["month_key", "threshold_pct"],
    foreignKeys = [
        ForeignKey(
            entity = BudgetEntity::class,
            parentColumns = ["month_key"],
            childColumns = ["month_key"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BudgetAlertStateEntity(
    @ColumnInfo(name = "month_key")
    val monthKey: String,

    @ColumnInfo(name = "threshold_pct")
    val thresholdPct: Int,

    @ColumnInfo(name = "fired_at_ms")
    val firedAtMs: Long,
)
