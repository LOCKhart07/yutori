package com.spendwise.database.mappers

import com.spendwise.classifier.BudgetEffect
import com.spendwise.database.entities.TransactionEntity
import com.spendwise.parser.Category
import com.spendwise.parser.Classification
import com.spendwise.transactions.TransactionRow

object TransactionMapper {

    fun toDomain(entity: TransactionEntity): TransactionRow = TransactionRow(
        id = entity.id,
        classification = Classification.valueOf(entity.classification),
        classificationOriginal = entity.classificationOriginal?.let {
            Classification.valueOf(it)
        },
        budgetEffect = BudgetEffect.valueOf(entity.budgetEffect),
        inrAmount = entity.inrAmount,
        originalAmount = entity.originalAmount,
        originalCurrency = entity.originalCurrency,
        exchangeRate = entity.exchangeRate,
        rateSource = entity.rateSource,
        merchant = entity.merchant,
        merchantKey = entity.merchantKey,
        category = entity.category?.let { Category.valueOf(it) },
        accountId = entity.accountId,
        last4 = entity.last4,
        issuer = entity.issuer,
        occurredAtMs = entity.occurredAtMs,
        monthKey = entity.monthKey,
        manuallyAdjusted = entity.manuallyAdjusted,
    )

    fun toEntity(row: TransactionRow): TransactionEntity = TransactionEntity(
        id = row.id,
        classification = row.classification.name,
        classificationOriginal = row.classificationOriginal?.name,
        budgetEffect = row.budgetEffect.name,
        inrAmount = row.inrAmount,
        originalAmount = row.originalAmount,
        originalCurrency = row.originalCurrency,
        exchangeRate = row.exchangeRate,
        rateSource = row.rateSource,
        merchant = row.merchant,
        merchantKey = row.merchantKey,
        category = row.category?.name,
        accountId = row.accountId,
        last4 = row.last4,
        issuer = row.issuer,
        occurredAtMs = row.occurredAtMs,
        monthKey = row.monthKey,
        manuallyAdjusted = row.manuallyAdjusted,
    )
}
