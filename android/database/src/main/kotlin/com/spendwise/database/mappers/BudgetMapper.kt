package com.spendwise.database.mappers

import com.spendwise.budget.AlertFiring
import com.spendwise.budget.Budget
import com.spendwise.database.entities.BudgetAlertStateEntity
import com.spendwise.database.entities.BudgetEntity

object BudgetMapper {

    fun toDomain(entity: BudgetEntity): Budget = Budget(
        monthKey = entity.monthKey,
        limitInr = entity.limitInr,
        warnThresholdPct = entity.thresholdWarnPct,
    )

    fun toEntity(budget: Budget, createdAtMs: Long, updatedAtMs: Long): BudgetEntity =
        BudgetEntity(
            monthKey = budget.monthKey,
            limitInr = budget.limitInr,
            thresholdWarnPct = budget.warnThresholdPct,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
        )
}

object BudgetAlertStateMapper {

    fun toDomain(entity: BudgetAlertStateEntity): AlertFiring = AlertFiring(
        monthKey = entity.monthKey,
        thresholdPct = entity.thresholdPct,
    )

    fun toEntity(firing: AlertFiring, firedAtMs: Long): BudgetAlertStateEntity =
        BudgetAlertStateEntity(
            monthKey = firing.monthKey,
            thresholdPct = firing.thresholdPct,
            firedAtMs = firedAtMs,
        )
}
