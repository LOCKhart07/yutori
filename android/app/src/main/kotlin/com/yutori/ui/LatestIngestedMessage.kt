package com.yutori.ui

import com.yutori.database.entities.SmsLogEntity
import com.yutori.parser.Classification

data class LatestIngestedMessage(
    val id: Long,
    val sender: String,
    val bodyPreview: String,
    val outcome: IngestedMessageOutcome,
)

enum class IngestedMessageOutcome {
    COUNTED_IN_BUDGET,
    IGNORED,
    NEEDS_REVIEW,
}

internal fun SmsLogEntity.toLatestIngestedMessage(): LatestIngestedMessage {
    val normalized = body.replace(Regex("\\s+"), " ").trim()
    val preview = normalized.take(BODY_PREVIEW_MAX_CHARS)
    val classification = runCatching { Classification.valueOf(classification) }.getOrNull()
    return LatestIngestedMessage(
        id = id,
        sender = sender,
        bodyPreview = preview,
        outcome = classification.toIngestedMessageOutcome(),
    )
}

private fun Classification?.toIngestedMessageOutcome(): IngestedMessageOutcome =
    when (this) {
        Classification.UNMATCHED,
        null,
        -> IngestedMessageOutcome.NEEDS_REVIEW

        Classification.CC_BILL_PAYMENT,
        Classification.CASHBACK,
        Classification.SELF_TRANSFER,
        Classification.OTP,
        Classification.BALANCE_ALERT,
        Classification.NON_FINANCIAL,
        -> IngestedMessageOutcome.IGNORED

        Classification.CC_TRANSACTION,
        Classification.UPI_PAYMENT,
        Classification.DEBIT_CARD,
        Classification.ATM_WITHDRAWAL,
        Classification.REFUND,
        Classification.INCOMING_CREDIT,
        -> IngestedMessageOutcome.COUNTED_IN_BUDGET
    }

private const val BODY_PREVIEW_MAX_CHARS = 80
