package com.spendwise.forex

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendwise.SpendWiseApp
import com.spendwise.database.mappers.TransactionMapper
import com.spendwise.transactions.forex.ErApiForexRateClient
import com.spendwise.transactions.forex.ForexErrorKind
import com.spendwise.transactions.forex.ForexFetcher
import com.spendwise.transactions.forex.ForexRateCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Resolves pending-forex transactions per business-logic-spec §5.
 * Reads `rate_source = "pending"` rows, groups by currency, fetches
 * once per currency, writes inrAmount/exchangeRate back via Room.
 *
 * On `TRANSIENT` / `QUOTA_EXHAUSTED` the rows stay pending — next run
 * retries. CURRENCY_UNKNOWN also leaves rows pending but we don't
 * auto-retry those on a short cadence (backoff schedule is enforced
 * by WorkManager's 15-minute minimum).
 */
class ForexConversionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as SpendWiseApp
        val db = app.database
        val txDao = db.transactionDao()

        // Pull a snapshot of pending rows. `observePendingForex()` is a
        // Flow; `first()` gives the current value without subscribing.
        val pendingEntities = txDao.observePendingForex().first()
        if (pendingEntities.isEmpty()) {
            Log.i(TAG, "No pending forex rows.")
            return@withContext Result.success()
        }

        val pendingRows = pendingEntities.map(TransactionMapper::toDomain)
        val fetcher = fetcher()
        val out = fetcher.resolvePending(
            pending = pendingRows,
            nowMs = System.currentTimeMillis(),
            dateKeyOf = { ms -> DATE_FMT.format(Instant.ofEpochMilli(ms)) },
        )

        var wrote = 0
        for (r in out.resolved) {
            txDao.update(TransactionMapper.toEntity(r.row))
            wrote++
        }

        Log.i(
            TAG,
            "Forex resolve: resolved=$wrote, stillPending=${out.stillPending.size}, " +
                "failures=${out.failures.joinToString { "${it.currency}:${it.kind}" }}",
        )

        // Retry-policy: any TRANSIENT failure means "try again sooner."
        // CURRENCY_UNKNOWN and QUOTA_EXHAUSTED are not worth a fast
        // retry — periodic schedule catches them. Returning retry()
        // lets WorkManager re-run with exponential backoff.
        return@withContext if (out.failures.any { it.kind == ForexErrorKind.TRANSIENT }) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val TAG = "ForexConversionWorker"
        const val ONE_SHOT_NAME = "forex_one_shot"
        const val PERIODIC_NAME = "forex_periodic"

        private val DATE_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

        // Cache + client are process-singletons so same-day rate lookups
        // survive across worker invocations (saves free-tier quota).
        @Volatile
        private var cacheHolder: ForexFetcher.CacheHolder = ForexFetcher.CacheHolder(ForexRateCache())

        private fun fetcher(): ForexFetcher =
            ForexFetcher(ErApiForexRateClient(), cacheHolder)

        /** Fire once now — used on app start and after new ingestion. */
        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<ForexConversionWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /** Every ~6h while the phone has network. Picks up stragglers. */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ForexConversionWorker>(
                6, TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
