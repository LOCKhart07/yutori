package com.yutori.ui

import com.yutori.classifier.BudgetEffect
import com.yutori.classifier.budgetEffectForClassification
import com.yutori.database.entities.SmsLogEntity
import com.yutori.parser.Classification

data class LatestIngestedMessage(
    val id: Long,
    val sender: String,
    val bodyPreview: String,
    val receivedAtMs: Long,
    val outcome: IngestedMessageOutcome,
)

enum class IngestedMessageOutcome {
    AFFECTS_BUDGET,
    TRACKED_AS_INCOME,
    IGNORED,
    NEEDS_REVIEW,
}

internal fun SmsLogEntity.toLatestIngestedMessage(): LatestIngestedMessage {
    val preview = body.replace(Regex("\\s+"), " ").trim()
    val classification = runCatching { Classification.valueOf(classification) }.getOrNull()
    return LatestIngestedMessage(
        id = id,
        sender = sender,
        bodyPreview = preview,
        receivedAtMs = receivedAtMs,
        outcome = classification.toIngestedMessageOutcome(),
    )
}

private fun Classification?.toIngestedMessageOutcome(): IngestedMessageOutcome =
    when {
        this == null || this == Classification.UNMATCHED -> IngestedMessageOutcome.NEEDS_REVIEW
        else -> this.toBudgetEffectOutcome()
    }

private fun Classification.toBudgetEffectOutcome(): IngestedMessageOutcome =
    when (budgetEffectForClassification(this)) {
        BudgetEffect.SPEND,
        BudgetEffect.REFUND,
        -> IngestedMessageOutcome.AFFECTS_BUDGET

        BudgetEffect.INCOME -> IngestedMessageOutcome.TRACKED_AS_INCOME
        BudgetEffect.DROP -> IngestedMessageOutcome.IGNORED
    }
