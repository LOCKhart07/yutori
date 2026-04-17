package com.yutori.ingestion

import com.yutori.budget.MonthSnapshot
import com.yutori.classifier.ClassificationOutcome
import com.yutori.transactions.MergeDecision

/**
 * Result of ingesting one SMS. Makes the pipeline observable for
 * tests and for callers that log per-SMS outcomes.
 */
sealed interface IngestionOutcome {

    /**
     * SMS was rejected before any work happened. Only one case today:
     * duplicate `android_sms_id` — ingestion-spec §5.3.
     */
    data class Duplicate(val existingSmsLogId: Long) : IngestionOutcome

    /**
     * SMS was classified (may be DROP / UNMATCHED / anything) and
     * stored in `sms_log`. [smsLogId] is the row id.
     *
     * If the classification produced a transaction, [transactionDecision]
     * is the builder's decision. If not (DROP effect), it's null.
     *
     * [alertEvaluation] is populated only when the new transaction may
     * have crossed a budget threshold (budget_effect ∈ SPEND / REFUND).
     * Its [AlertEvaluation.newlyFired] list is what gets dispatched as
     * notifications.
     */
    data class Ingested(
        val smsLogId: Long,
        val classifiedOutcome: ClassificationOutcome,
        val transactionDecision: MergeDecision?,
        val alertEvaluation: AlertEvaluation? = null,
        /**
         * Per-transaction "impact" notification payload. Populated only
         * when the new tx is a SPEND with a known INR amount that is
         * ≥ the user's per-tx threshold % of the effective budget.
         * Decoupled from [alertEvaluation] (which is cumulative) — the
         * dispatcher fires both independently when both are populated.
         */
        val impactNotification: ImpactNotification? = null,
    ) : IngestionOutcome
}

/**
 * Per-transaction impact: a single SPEND was a meaningful chunk of the
 * monthly budget. UI uses [percentOfBudget] for the headline; the rest
 * helps the notifier render a useful body.
 */
data class ImpactNotification(
    val monthKey: String,
    val txInrAmount: Double,
    val effectiveBudgetInr: Double,
    val percentOfBudget: Double,
    val remainingInr: Double,
    val daysLeft: Int,
    val merchantLabel: String?,
    val transactionId: Long,
)

/** User-controlled config for the impact-notification feature. */
data class ImpactConfig(
    val enabled: Boolean,
    val thresholdPct: Int,
) {
    companion object {
        val OFF = ImpactConfig(enabled = false, thresholdPct = 10)
    }
}

/**
 * Output of the post-ingest alert evaluation. Captures which
 * thresholds fired and the snapshot they fired against, plus whether
 * the transaction was in the current device-local month
 * (business-logic-spec §7.6 — past-month evaluations don't dispatch
 * notifications, but they are still recorded in `budget_alert_state`).
 */
data class AlertEvaluation(
    val monthKey: String,
    val snapshot: MonthSnapshot,
    val warnThresholdPct: Int,
    val newlyFired: List<Int>,
    val isCurrentMonth: Boolean,
)
