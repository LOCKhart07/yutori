package com.spendwise.ui.update

import com.spendwise.update.model.LatestRelease

data class UpdateScreenState(
    val currentVersion: String,
    val checkOnOpenEnabled: Boolean,
    val phase: Phase,
    val dialogVisible: Boolean = false,
) {
    sealed interface Phase {
        data object NotCheckedYet : Phase
        data object Checking : Phase
        data object UpToDate : Phase
        data class Available(val release: LatestRelease) : Phase
        data class Downloading(
            val release: LatestRelease,
            val bytes: Long,
            val total: Long,
        ) : Phase
        data class DownloadFailed(val release: LatestRelease) : Phase
        data object ErrorChecking : Phase
    }
}
