package com.yutori.ui

import com.yutori.database.entities.SmsLogEntity
import com.yutori.parser.Classification

data class LatestIngestedMessage(
    val id: Long,
    val sender: String,
    val bodyPreview: String,
    val receivedAtMs: Long,
    val classification: Classification?,
)

internal fun SmsLogEntity.toLatestIngestedMessage(): LatestIngestedMessage {
    val preview = body.replace(Regex("\\s+"), " ").trim()
    val decoded = runCatching { Classification.valueOf(classification) }.getOrNull()
    return LatestIngestedMessage(
        id = id,
        sender = sender,
        bodyPreview = preview,
        receivedAtMs = receivedAtMs,
        classification = decoded,
    )
}
