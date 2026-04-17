package com.spendwise.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spendwise.update.UpdateDownloader
import com.spendwise.update.UpdateInstallEvents
import com.spendwise.update.UpdateInstaller
import com.spendwise.update.UpdatePrefs
import com.spendwise.update.UpdateRepository
import com.spendwise.update.VersionComparator
import com.spendwise.update.model.DownloadState
import com.spendwise.update.model.LatestRelease
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UpdateViewModel(
    private val fetchLatest: suspend () -> Result<LatestRelease?>,
    private val downloadAsset: (LatestRelease.Asset) -> Flow<DownloadState>,
    private val installApk: (File) -> Unit,
    private val prefs: UpdatePrefs,
    currentVersion: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    installOutcomes: SharedFlow<UpdateInstallEvents.Outcome> = UpdateInstallEvents.outcomes,
) : ViewModel() {

    private val _state = MutableStateFlow(
        UpdateScreenState(
            currentVersion = currentVersion,
            checkOnOpenEnabled = prefs.checkOnOpenEnabled,
            phase = if (prefs.lastCheckAt == 0L) {
                UpdateScreenState.Phase.NotCheckedYet
            } else {
                UpdateScreenState.Phase.UpToDate
            },
        ),
    )
    val state: StateFlow<UpdateScreenState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    init {
        // Unstick the Downloading dialog when the system-install flow
        // ends with anything other than success — user cancelled the
        // confirmation UI, denied "Install unknown apps", Android
        // rejected the APK, etc. On success the process usually dies
        // before we see the event, so no state change is needed there.
        viewModelScope.launch {
            installOutcomes.collectLatest { outcome ->
                if (outcome is UpdateInstallEvents.Outcome.Failure) {
                    val phase = _state.value.phase
                    if (phase is UpdateScreenState.Phase.Downloading) {
                        _state.update {
                            it.copy(phase = UpdateScreenState.Phase.DownloadFailed(phase.release))
                        }
                    }
                }
            }
        }
    }

    fun onCheckNow() {
        if (_state.value.phase is UpdateScreenState.Phase.Checking) return
        _state.update { it.copy(phase = UpdateScreenState.Phase.Checking) }
        viewModelScope.launch(ioDispatcher) {
            val result = fetchLatest()
            result.onSuccess { release ->
                prefs.lastCheckAt = clock()
                if (release == null) {
                    _state.update { it.copy(phase = UpdateScreenState.Phase.UpToDate) }
                } else if (VersionComparator.hasUpdate(_state.value.currentVersion, release.tagName)) {
                    _state.update { it.copy(phase = UpdateScreenState.Phase.Available(release)) }
                } else {
                    _state.update { it.copy(phase = UpdateScreenState.Phase.UpToDate) }
                }
            }.onFailure {
                _state.update { it.copy(phase = UpdateScreenState.Phase.ErrorChecking) }
            }
        }
    }

    fun onOpenDialog() {
        val phase = _state.value.phase
        if (phase is UpdateScreenState.Phase.Available) {
            _state.update { it.copy(dialogVisible = true) }
        }
    }

    fun onDismissDialog() {
        val phase = _state.value.phase
        cancelDownload()
        // Stamp `dismissed_tag` only when user chose Later on an
        // available update — not when dismissing a completed download
        // or an error state. Spec §4.7.
        if (phase is UpdateScreenState.Phase.Available) {
            prefs.dismissedTag = phase.release.tagName
        }
        _state.update { it.copy(dialogVisible = false) }
    }

    fun onStartDownload() {
        val phase = _state.value.phase
        val release = when (phase) {
            is UpdateScreenState.Phase.Available -> phase.release
            is UpdateScreenState.Phase.DownloadFailed -> phase.release
            else -> return
        }
        val asset = release.asset ?: return
        _state.update {
            it.copy(phase = UpdateScreenState.Phase.Downloading(release, 0L, asset.sizeBytes))
        }
        downloadJob = viewModelScope.launch(ioDispatcher) {
            downloadAsset(asset).collectLatest { ds ->
                when (ds) {
                    is DownloadState.Progress -> _state.update {
                        it.copy(phase = UpdateScreenState.Phase.Downloading(release, ds.bytes, ds.total))
                    }
                    is DownloadState.Done -> {
                        installApk(ds.apk)
                        // System takes over from here; keep the dialog
                        // open on Downloading-at-100% until the user
                        // either confirms install (process dies) or the
                        // receiver routes back.
                    }
                    is DownloadState.Failed -> _state.update {
                        it.copy(phase = UpdateScreenState.Phase.DownloadFailed(release))
                    }
                }
            }
        }
    }

    fun onCancelDownload() {
        val phase = _state.value.phase
        cancelDownload()
        if (phase is UpdateScreenState.Phase.Downloading) {
            _state.update { it.copy(phase = UpdateScreenState.Phase.Available(phase.release)) }
        }
    }

    fun onToggleCheckOnOpen(enabled: Boolean) {
        prefs.checkOnOpenEnabled = enabled
        _state.update { it.copy(checkOnOpenEnabled = enabled) }
    }

    private fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    private fun MutableStateFlow<UpdateScreenState>.update(
        transform: (UpdateScreenState) -> UpdateScreenState,
    ) {
        value = transform(value)
    }

    class Factory(
        private val repo: UpdateRepository,
        private val downloader: UpdateDownloader,
        private val installer: UpdateInstaller,
        private val prefs: UpdatePrefs,
        private val currentVersion: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(UpdateViewModel::class.java))
            return UpdateViewModel(
                fetchLatest = repo::latestRelease,
                downloadAsset = downloader::download,
                installApk = installer::install,
                prefs = prefs,
                currentVersion = currentVersion,
            ) as T
        }
    }
}
