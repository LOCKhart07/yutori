package com.spendwise.update.model

// Domain shape of a GitHub release as the updater consumes it. `asset`
// is null when the release shipped without an APK — treated as "no
// update" upstream rather than as an error.
data class LatestRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val asset: Asset?,
) {
    data class Asset(
        val url: String,
        val sizeBytes: Long,
        val name: String,
    )
}
