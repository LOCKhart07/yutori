package com.yutori.budget

import kotlin.math.floor

/**
 * Pure threshold-firing logic per business-logic-spec.md §7.
 *
 * Given a [MonthSnapshot], the user's warn threshold, and the set of
 * already-fired thresholds for that month, returns the NEW thresholds
 * that should fire now. Caller inserts them into `budget_alert_state`
 * and decides whether to dispatch notifications (past-month reparses
 * silently record without dispatching; see §7.6).
 *
 * Threshold set (§7.1):
 *   - 50% (fixed, informational)
 *   - warnPct (default 80, user-adjustable)
 *   - 100% (fixed, critical)
 *   - Dynamic: 110, 120, 130, ... as spend climbs
 *
 * Firing rule (§7.2): each threshold fires at most once per month.
 * Once in `alreadyFired` it stays there forever — refunds dropping
 * spend below the threshold do not un-fire (§7.5).
 */
object AlertStateMachine {

    /**
     * Clamps [warnThresholdPct] to a safe range. The UI should keep
     * the user inside 60–95 (§7.1), but we accept anything and clamp
     * defensively — a malformed value shouldn't crash budget math.
     */
    internal fun clampWarn(warnThresholdPct: Int): Int =
        warnThresholdPct.coerceIn(1, 100)

    /**
     * Returns all thresholds potentially applicable at [percentUsed],
     * given [warnThresholdPct]. Ascending, deduplicated.
     */
    fun thresholdsForMonth(warnThresholdPct: Int, percentUsed: Double): List<Int> {
        val result = sortedSetOf(50, clampWarn(warnThresholdPct), 100)

        if (percentUsed >= 110.0) {
            val max = floor(percentUsed / 10.0).toInt() * 10
            var t = 110
            while (t <= max) {
                result.add(t)
                t += 10
            }
        }

        return result.toList()
    }

    /**
     * Returns the thresholds that should fire now: those crossed by
     * [percentUsed] and not yet present in [alreadyFiredPcts]. Empty
     * list if nothing new crossed.
     */
    fun thresholdsToFire(
        percentUsed: Double,
        warnThresholdPct: Int,
        alreadyFiredPcts: Set<Int>,
    ): List<Int> =
        thresholdsForMonth(warnThresholdPct, percentUsed)
            .filter { it.toDouble() <= percentUsed && it !in alreadyFiredPcts }

    /** Overload that takes a [MonthSnapshot] + existing [AlertFiring]s. */
    fun thresholdsToFire(
        snapshot: MonthSnapshot,
        warnThresholdPct: Int,
        alreadyFired: List<AlertFiring>,
    ): List<Int> {
        val firedPcts = alreadyFired
            .asSequence()
            .filter { it.monthKey == snapshot.monthKey }
            .map { it.thresholdPct }
            .toSet()
        return thresholdsToFire(snapshot.percentUsed, warnThresholdPct, firedPcts)
    }
}
