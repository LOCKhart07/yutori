package com.yutori.database.mappers

import com.yutori.budget.AlertFiring
import com.yutori.budget.Budget
import com.yutori.database.entities.BudgetAlertStateEntity
import com.yutori.database.entities.BudgetEntity

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
