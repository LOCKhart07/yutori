package com.spendwise.ingestion

/**
 * Abstraction over `content://sms/inbox` queries. Used by
 * [SmsLogReconciler] to resolve an Android SMS `_id` from the raw
 * (sender, body, receivedAtMs) of a row the real-time receiver stored
 * with `android_sms_id = NULL` (ingestion-spec §5.4).
 *
 * Kept as an interface so the reconciler's logic tests cleanly with a
 * fake — the Android [android.content.ContentResolver] binding lives
 * in [ContentProviderSmsInboxLookup].
 */
interface SmsInboxLookup {

    /**
     * Returns the Android SMS `_id` for the message matching
     * (`sender`, `body`) whose timestamp is within ±[toleranceMs] of
     * [receivedAtMs]. Returns null if zero or >1 matches — we never
     * guess.
     */
    suspend fun findId(
        sender: String,
        body: String,
        receivedAtMs: Long,
        toleranceMs: Long = 2_000L,
    ): Long?
}
