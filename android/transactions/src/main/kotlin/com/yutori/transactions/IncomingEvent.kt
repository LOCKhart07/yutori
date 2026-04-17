package com.yutori.transactions

import com.yutori.classifier.ClassificationOutcome

/**
 * One classified SMS ready for insertion into the transactions layer.
 *
 * Combines the classifier's pure [ClassificationOutcome] with the
 * SMS-level metadata ingestion tracks: the stored `sms_log.id`, the
 * parser pattern that produced the outcome (used for role assignment
 * §4.4), the SMS's occurred-at timestamp, and the computed month key.
 */
data class IncomingEvent(
    val smsLogId: Long,
    val outcome: ClassificationOutcome,
    val parserPattern: String,
    val occurredAtMs: Long,
    val monthKey: String,
    /** Derived from the SMS sender — see [IssuerDeriver]. */
    val issuer: String? = null,
)
