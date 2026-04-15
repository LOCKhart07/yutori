package com.spendwise.ingestion

/**
 * Dispatches a system-level budget alert. Implementation lives in the
 * :app module — ingestion stays free of Android `Context` and
 * `NotificationManager`, making the pipeline testable with pure
 * fakes.
 *
 * Called by [IngestionCoordinator] after [IngestionPipeline.ingest]
 * returns an [AlertEvaluation] with non-empty [AlertEvaluation.newlyFired]
 * AND [AlertEvaluation.isCurrentMonth] true.
 */
interface AlertNotifier {
    /** Fire one notification per [thresholdPct] crossed. */
    fun notify(thresholdPct: Int, evaluation: AlertEvaluation)

    /**
     * Fire a per-transaction "impact" notification. Distinct from the
     * cumulative threshold alerts above — this is the "₹X at MERCHANT
     * is N% of your budget" push that surfaces meaningful single
     * transactions within seconds.
     */
    fun notifyImpact(impact: ImpactNotification) {
        // Default no-op so legacy/test fakes don't have to implement it.
    }
}
