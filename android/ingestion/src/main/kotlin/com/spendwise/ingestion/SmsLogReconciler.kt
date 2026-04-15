package com.spendwise.ingestion

import com.spendwise.database.dao.SmsLogDao

/**
 * Fills in missing `android_sms_id` values on `sms_log` rows that
 * were inserted by the real-time receiver before the system content
 * provider had committed the SMS (ingestion-spec §5.4 + §8).
 *
 * Runs on app launch after a short delay so it doesn't block startup
 * (ingestion-spec §8). Idempotent and cheap: bounded by the number of
 * rows with `android_sms_id IS NULL`, typically 0 after the first
 * successful pass.
 *
 * Conflict handling: if the lookup returns an `android_sms_id` that
 * another row already holds (can happen when the same SMS was
 * ingested both via real-time AND historical import), we leave the
 * NULL row alone — v1 MVP doesn't try to merge them. A v1.1 pass can
 * reassign sources and delete the duplicate.
 */
class SmsLogReconciler(
    private val smsLogDao: SmsLogDao,
    private val inboxLookup: SmsInboxLookup,
) {

    /** Returns a summary of what changed, useful for tests + logs. */
    data class Outcome(
        val scanned: Int,
        val resolved: Int,
        val conflictsSkipped: Int,
        val notFound: Int,
    )

    suspend fun reconcile(): Outcome {
        val candidates = smsLogDao.findRowsMissingAndroidSmsId()
        var resolved = 0
        var conflicts = 0
        var notFound = 0

        for (row in candidates) {
            val id = inboxLookup.findId(
                sender = row.sender,
                body = row.body,
                receivedAtMs = row.receivedAtMs,
            )
            if (id == null) {
                notFound++
                continue
            }

            val clashing = smsLogDao.findByAndroidSmsId(id)
            if (clashing != null && clashing.id != row.id) {
                // Another sms_log row already claims this id. Leave the
                // NULL row alone — merging is out of scope for v1 MVP.
                // v1.1 TODO: reassign transaction_sms_sources to the
                // canonical row, then delete this NULL dup.
                conflicts++
                continue
            }

            smsLogDao.update(row.copy(androidSmsId = id))
            resolved++
        }

        return Outcome(
            scanned = candidates.size,
            resolved = resolved,
            conflictsSkipped = conflicts,
            notFound = notFound,
        )
    }
}
