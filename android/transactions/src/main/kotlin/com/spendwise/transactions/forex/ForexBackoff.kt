package com.spendwise.transactions.forex

/**
 * Pure backoff state machine for the forex rate fetcher.
 * Given the number of consecutive failures and the error kind,
 * returns the next-retry delay in milliseconds.
 *
 * Schedules per business-logic-spec.md §5.2:
 *   - TRANSIENT: 30s → 5min → 1h, then pinned at 1h.
 *   - QUOTA_EXHAUSTED: 1h → 6h → 24h, then pinned at 24h.
 *   - CURRENCY_UNKNOWN: infinite — do not auto-retry.
 *
 * The worker records consecutive failures per-transaction and passes
 * them in; this function is stateless.
 */
object ForexBackoff {

    private val TRANSIENT_SCHEDULE_MS = longArrayOf(
        30_000L,              // 30 seconds
        5L * 60 * 1000L,      // 5 minutes
        60L * 60 * 1000L,     // 1 hour — sticky from here on
    )

    private val QUOTA_SCHEDULE_MS = longArrayOf(
        60L * 60 * 1000L,            // 1 hour
        6L * 60 * 60 * 1000L,        // 6 hours
        24L * 60 * 60 * 1000L,       // 24 hours — sticky from here on
    )

    /**
     * Returns the delay to wait before the next retry. `null` means
     * "do not retry automatically" — the caller should surface this
     * to the user instead.
     *
     * @param consecutiveFailures 1-based count. The very first retry
     *        after the initial failure is `consecutiveFailures = 1`.
     */
    fun nextDelayMs(kind: ForexErrorKind, consecutiveFailures: Int): Long? {
        require(consecutiveFailures >= 1) {
            "consecutiveFailures must be ≥ 1 (got $consecutiveFailures)"
        }
        return when (kind) {
            ForexErrorKind.TRANSIENT -> pickFromSchedule(TRANSIENT_SCHEDULE_MS, consecutiveFailures)
            ForexErrorKind.QUOTA_EXHAUSTED -> pickFromSchedule(QUOTA_SCHEDULE_MS, consecutiveFailures)
            ForexErrorKind.CURRENCY_UNKNOWN -> null
        }
    }

    private fun pickFromSchedule(schedule: LongArray, attempt: Int): Long {
        val idx = (attempt - 1).coerceAtMost(schedule.lastIndex)
        return schedule[idx]
    }
}
