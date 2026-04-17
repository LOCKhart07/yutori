package com.yutori.update.model

import java.io.File

sealed interface DownloadState {
    data class Progress(val bytes: Long, val total: Long) : DownloadState
    data class Done(val apk: File) : DownloadState
    data class Failed(val reason: Reason) : DownloadState

    enum class Reason { Network, Auth, NotFound, Disk, Cancelled, Unknown }
}
