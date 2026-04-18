package com.yutori.suggestions

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yutori.YutoriApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Runs the heuristic rule-suggestion miner per suggestions-spec §3.5.
 *
 * Three entry points:
 * - [enqueuePeriodic] — nightly refresh, KEEP policy.
 * - [enqueueOneShot] — one-shot, REPLACE policy. Used on historical-
 *   import completion and by the manual Rescan affordance in Settings.
 *
 * Live-SMS ingestion does not trigger this worker (a single new row
 * rarely crosses the threshold).
 */
class SuggestionRescanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as YutoriApp
        val db = app.database ?: return@withContext Result.success()

        val miner = SuggestionMiner(
            transactionDao = db.transactionDao(),
            ruleSuggestionDao = db.ruleSuggestionDao(),
            recipientRuleDao = db.recipientRuleDao(),
            accountDao = db.accountDao(),
        )
        val report = miner.runOnce(System.currentTimeMillis())
        Log.i(
            TAG,
            "Suggestion rescan: considered=${report.candidatesConsidered} " +
                "inserted=${report.inserted} updated=${report.updated} " +
                "resurfaced=${report.resurfaced} skippedDismissed=${report.skippedDismissed}",
        )
        Result.success()
    }

    companion object {
        private const val TAG = "SuggestionRescanWorker"
        const val ONE_SHOT_NAME = "suggestion_rescan_one_shot"
        const val PERIODIC_NAME = "suggestion_rescan_periodic"

        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<SuggestionRescanWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SuggestionRescanWorker>(
                1, TimeUnit.DAYS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
