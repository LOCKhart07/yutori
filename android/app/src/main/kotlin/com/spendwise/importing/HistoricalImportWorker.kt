package com.spendwise.importing

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.spendwise.ingestion.IngestionCoordinator
import com.spendwise.ingestion.RawSms
import com.spendwise.ingestion.SmsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Per [ingestion-spec §7](../../../../../plans/ingestion-spec.md).
 * Reads `content://sms/inbox` in a date range and feeds each row
 * through [IngestionCoordinator.ingestAndNotify]. Dedup on
 * `android_sms_id` is enforced by Room's unique index, so a
 * re-import over overlapping dates is safe.
 *
 * Progress is reported via [setProgress] with `processed` and
 * `total` keys so any observer can render a progress bar. On
 * completion the final counts ride on the work result.
 *
 * Only READ_SMS is consulted — RECEIVE_SMS is orthogonal. If
 * READ_SMS is denied, the content query fails and we return
 * [Result.failure].
 */
class HistoricalImportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sinceMs = inputData.getLong(KEY_SINCE_MS, 0L)
        val coordinator = IngestionCoordinator.instance
            ?: return@withContext Result.failure(
                workDataOf(KEY_ERROR to "coordinator not initialized"),
            )

        val cursor = try {
            applicationContext.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                ),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(sinceMs.toString()),
                "${Telephony.Sms.DATE} ASC",
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "READ_SMS denied while running import", e)
            return@withContext Result.failure(
                workDataOf(KEY_ERROR to "READ_SMS permission missing"),
            )
        }
        if (cursor == null) {
            return@withContext Result.failure(
                workDataOf(KEY_ERROR to "SMS content provider returned null"),
            )
        }

        var processed = 0
        var inserted = 0
        var duplicates = 0
        var failures = 0
        val total = cursor.count

        setProgress(progressData(processed, total))

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressCol = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (c.moveToNext()) {
                val sender = c.getString(addressCol)
                val body = c.getString(bodyCol)
                if (sender.isNullOrBlank() || body.isNullOrBlank()) {
                    processed++
                    continue
                }
                val raw = RawSms(
                    androidSmsId = c.getLong(idCol),
                    sender = sender,
                    body = body,
                    receivedAtMs = c.getLong(dateCol),
                    source = SmsSource.SMS_IMPORT,
                )
                try {
                    val outcome = coordinator.ingestAndNotify(raw)
                    when (outcome) {
                        is com.spendwise.ingestion.IngestionOutcome.Ingested -> inserted++
                        is com.spendwise.ingestion.IngestionOutcome.Duplicate -> duplicates++
                    }
                } catch (e: Throwable) {
                    failures++
                    Log.e(TAG, "Failed to ingest historical SMS id=${raw.androidSmsId}", e)
                }
                processed++
                if (processed % PROGRESS_EVERY_N == 0 || processed == total) {
                    setProgress(progressData(processed, total))
                }
            }
        }

        Result.success(
            workDataOf(
                KEY_PROCESSED to processed,
                KEY_INSERTED to inserted,
                KEY_DUPLICATES to duplicates,
                KEY_FAILURES to failures,
                KEY_TOTAL to total,
            ),
        )
    }

    private fun progressData(processed: Int, total: Int): Data =
        workDataOf(
            KEY_PROCESSED to processed,
            KEY_TOTAL to total,
        )

    companion object {
        private const val TAG = "HistoricalImportWorker"

        /**
         * User-triggered import. The dashboard's progress UI observes
         * this name specifically so the silent catch-up doesn't fill
         * the screen.
         */
        const val UNIQUE_NAME = "historical_import"

        /**
         * Silent catch-up, enqueued on every app launch with a floor
         * at `max(sms_log.received_at_ms) + 1`. Picks up anything
         * missed while the process was in stopped state.
         */
        const val CATCH_UP_UNIQUE_NAME = "catch_up_import"

        const val KEY_SINCE_MS = "since_ms"
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
        const val KEY_INSERTED = "inserted"
        const val KEY_DUPLICATES = "duplicates"
        const val KEY_FAILURES = "failures"
        const val KEY_ERROR = "error"

        private const val PROGRESS_EVERY_N = 10

        /**
         * User-triggered import. If one is already running, keep it
         * running (user doesn't accidentally start a second copy).
         */
        fun enqueue(context: Context, sinceMs: Long) {
            val request = OneTimeWorkRequestBuilder<HistoricalImportWorker>()
                .setInputData(workDataOf(KEY_SINCE_MS to sinceMs))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Silent catch-up on app start. Separate unique name so its
         * progress stream doesn't feed the user-facing import UI.
         */
        fun enqueueCatchUp(context: Context, sinceMs: Long) {
            val request = OneTimeWorkRequestBuilder<HistoricalImportWorker>()
                .setInputData(workDataOf(KEY_SINCE_MS to sinceMs))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                CATCH_UP_UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
