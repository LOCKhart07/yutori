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
fun interface AlertNotifier {
    /** Fire one notification per [thresholdPct] crossed. */
    fun notify(thresholdPct: Int, evaluation: AlertEvaluation)
}
