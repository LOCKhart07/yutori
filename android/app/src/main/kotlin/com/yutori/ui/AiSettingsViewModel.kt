package com.yutori.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yutori.ai.AiSettingsRepository
import com.yutori.ai.LlmEngineHolder
import com.yutori.ai.ModelDownloadWorker
import com.yutori.ai.ModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the Settings → AI-assisted rules surface. Combines:
 * - [AiSettingsRepository.state] — persistent toggle + installed-SHA
 *   state.
 * - WorkManager's WorkInfo for the download unique name — transient
 *   progress / failure state during a download.
 * - Local UI state — whether the opt-in sheet is visible.
 *
 * See `plans/ai-rules-spec.md` §6.1 and mockup §A.
 */
class AiSettingsViewModel(
    private val appContext: Context,
    private val repository: AiSettingsRepository,
    private val engineHolder: LlmEngineHolder,
) : ViewModel() {

    private val workManager: WorkManager = WorkManager.getInstance(appContext)
    private val optInSheetVisible = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val downloadWorkInfoFlow = MutableStateFlow(Unit).flatMapLatest {
        workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.UNIQUE_NAME)
    }

    val uiState: StateFlow<AiSettingsUiState> = combine(
        repository.state,
        downloadWorkInfoFlow,
        optInSheetVisible,
    ) { repoState, workInfos, sheetVisible ->
        AiSettingsUiState(
            enabled = repoState.enabled,
            modelState = deriveModelState(repoState, workInfos.firstOrNull()),
            optInSheetVisible = sheetVisible,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        initialValue = AiSettingsUiState(
            enabled = repository.get().enabled,
            modelState = ModelUiState.Absent,
            optInSheetVisible = false,
        ),
    )

    private fun deriveModelState(
        repoState: AiSettingsRepository.State,
        work: WorkInfo?,
    ): ModelUiState {
        if (repoState.modelInstalled) {
            return ModelUiState.Ready(
                installedAtMs = repoState.modelInstallTimeMs ?: System.currentTimeMillis(),
            )
        }
        return when (work?.state) {
            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                val downloaded = work.progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED, 0L)
                val total = work.progress.getLong(ModelDownloadWorker.KEY_TOTAL, 0L)
                ModelUiState.Downloading(downloaded, total)
            }

            WorkInfo.State.FAILED -> {
                val reason = work.outputData.getString(ModelDownloadWorker.KEY_ERROR)
                val message = work.outputData.getString(ModelDownloadWorker.KEY_MESSAGE)
                ModelUiState.Failed(
                    reason = when (reason) {
                        "checksum_mismatch" -> FailureReason.Checksum
                        "cancelled" -> FailureReason.Cancelled
                        else -> FailureReason.Network
                    },
                    message = message,
                )
            }

            WorkInfo.State.CANCELLED -> ModelUiState.Failed(
                reason = FailureReason.Cancelled,
                message = null,
            )

            else -> ModelUiState.Absent
        }
    }

    /** User tapped the toggle. Opens the opt-in sheet when turning on. */
    fun onToggle(checked: Boolean) {
        if (checked) {
            optInSheetVisible.value = true
        } else {
            disableFeature()
        }
    }

    fun onConfirmOptIn() {
        optInSheetVisible.value = false
        repository.setEnabled(true)
        // Auto-kick a download if the model isn't already on disk. The
        // worker is idempotent / re-enqueue-safe (ExistingWorkPolicy.KEEP).
        if (!repository.get().modelInstalled) {
            ModelDownloadWorker.enqueue(appContext)
        }
    }

    fun onDismissOptIn() {
        optInSheetVisible.value = false
    }

    fun onStartDownload() {
        ModelDownloadWorker.enqueue(appContext)
    }

    fun onRetryDownload() {
        ModelDownloadWorker.enqueue(appContext)
    }

    fun onCancelDownload() {
        ModelDownloadWorker.cancel(appContext)
    }

    fun onDeleteModel() {
        viewModelScope.launch {
            // Close the engine first so the file isn't still mapped.
            engineHolder.close()
            withContext(Dispatchers.IO) {
                ModelFiles.modelFile(appContext).delete()
                ModelFiles.tmpFile(appContext).delete()
            }
            repository.clearModel()
        }
    }

    /**
     * Turning the toggle off does NOT delete the model file (spec §5.4).
     * The user can flip it back on without paying the 2.58 GB download
     * again. Explicit deletion is an action of its own.
     */
    private fun disableFeature() {
        viewModelScope.launch {
            repository.setEnabled(false)
            engineHolder.close()
        }
    }

    class Factory(
        private val appContext: Context,
        private val repository: AiSettingsRepository,
        private val engineHolder: LlmEngineHolder,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AiSettingsViewModel(appContext, repository, engineHolder) as T
    }
}
