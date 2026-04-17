package com.yutori.ui

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yutori.importing.HistoricalImportWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Flow wrapper over the historical-import worker's progress. Renders
 * as a small status line on the dashboard while the worker is running.
 */
sealed interface ImportStatus {
    data object Idle : ImportStatus
    data class Running(val processed: Int, val total: Int) : ImportStatus
    data class Succeeded(
        val processed: Int,
        val inserted: Int,
        val duplicates: Int,
        val failures: Int,
        /**
         * Earliest month (`YYYY-MM`) among rows inserted this run, or
         * null when nothing was inserted. Drives the "Jump to <Month>"
         * affordance (#74) when the user is viewing a later month than
         * where the imported rows landed.
         */
        val earliestMonthTouched: String?,
    ) : ImportStatus
    data class Failed(val message: String?) : ImportStatus
}

fun importStatusFlow(context: Context): Flow<ImportStatus> =
    WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(HistoricalImportWorker.UNIQUE_NAME)
        .map { infos ->
            val info = infos.lastOrNull() ?: return@map ImportStatus.Idle
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> {
                    val p = info.progress.getInt(HistoricalImportWorker.KEY_PROCESSED, 0)
                    val t = info.progress.getInt(HistoricalImportWorker.KEY_TOTAL, 0)
                    ImportStatus.Running(p, t)
                }
                WorkInfo.State.SUCCEEDED -> ImportStatus.Succeeded(
                    processed = info.outputData.getInt(HistoricalImportWorker.KEY_PROCESSED, 0),
                    inserted = info.outputData.getInt(HistoricalImportWorker.KEY_INSERTED, 0),
                    duplicates = info.outputData.getInt(HistoricalImportWorker.KEY_DUPLICATES, 0),
                    failures = info.outputData.getInt(HistoricalImportWorker.KEY_FAILURES, 0),
                    earliestMonthTouched = info.outputData
                        .getString(HistoricalImportWorker.KEY_EARLIEST_MONTH),
                )
                WorkInfo.State.FAILED ->
                    ImportStatus.Failed(info.outputData.getString(HistoricalImportWorker.KEY_ERROR))
                else -> ImportStatus.Idle
            }
        }
