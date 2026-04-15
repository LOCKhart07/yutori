package com.spendwise.ingestion

/**
 * Raw SMS as delivered by Android. See ingestion-spec.md §4.
 *
 * `androidSmsId` may be null when the receiver fires before the
 * system content provider has committed the row (see §5.4). A
 * reconciler fills it in later; dedup in the interim uses
 * (sender, body, receivedAtMs) as a fallback.
 */
data class RawSms(
    val androidSmsId: Long?,
    val sender: String,
    val body: String,
    val receivedAtMs: Long,
    val source: SmsSource,
)

/**
 * Where the ingestion pipeline received an SMS from. Persisted as
 * `sms_log.source`.
 */
enum class SmsSource {
    /** Live, via the BroadcastReceiver. */
    SMS_REALTIME,

    /** Bulk import from `content://sms/inbox`. */
    SMS_IMPORT,

    /** Future scope §10.1. Reserved. */
    STATEMENT_PDF,
    STATEMENT_CSV,
    MANUAL,
}
