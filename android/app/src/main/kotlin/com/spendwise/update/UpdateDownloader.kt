package com.spendwise.update

import com.spendwise.update.model.DownloadState
import com.spendwise.update.model.LatestRelease
import java.io.File
import java.io.IOException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class UpdateDownloader(
    private val client: OkHttpClient,
    private val cacheDir: File,
) {
    // Cold flow — subscriber controls the lifetime. Cancelling collection
    // aborts the OkHttp call and emits Failed(Cancelled).
    fun download(asset: LatestRelease.Asset): Flow<DownloadState> = callbackFlow {
        val dir = File(cacheDir, UPDATE_SUBDIR).apply { mkdirs() }
        // Purge previous attempts first — abandoned partials would
        // otherwise bloat the cache indefinitely.
        dir.listFiles { f -> f.name.endsWith(".apk") }?.forEach { it.delete() }

        val outFile = File(dir, "${sanitizeTag(asset.name)}.apk")
        // application/octet-stream on this endpoint triggers GitHub's
        // 302 to a presigned S3 URL. OkHttp strips Authorization on
        // cross-host redirects so the presigned request is clean —
        // covered by UpdateDownloaderTest.
        val request = Request.Builder()
            .url(asset.url)
            .header("Accept", "application/octet-stream")
            .build()

        val call = client.newCall(request)
        val bodyBytes = asset.sizeBytes

        var response: Response? = null
        try {
            response = call.execute()
            when {
                response.code == 404 -> {
                    trySend(DownloadState.Failed(DownloadState.Reason.NotFound))
                    close()
                    return@callbackFlow
                }
                response.code == 401 || response.code == 403 -> {
                    trySend(DownloadState.Failed(DownloadState.Reason.Auth))
                    close()
                    return@callbackFlow
                }
                !response.isSuccessful -> {
                    trySend(DownloadState.Failed(DownloadState.Reason.Network))
                    close()
                    return@callbackFlow
                }
            }

            val body = response.body ?: run {
                trySend(DownloadState.Failed(DownloadState.Reason.Network))
                close()
                return@callbackFlow
            }
            val total = body.contentLength().takeIf { it > 0 } ?: bodyBytes

            body.byteStream().use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var read: Int
                    var written = 0L
                    var bytesSinceEmit = 0L
                    var lastEmitAt = System.currentTimeMillis()
                    trySend(DownloadState.Progress(0L, total))
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        written += read
                        bytesSinceEmit += read
                        val now = System.currentTimeMillis()
                        if (bytesSinceEmit >= PROGRESS_BYTES || now - lastEmitAt >= PROGRESS_MS) {
                            trySend(DownloadState.Progress(written, total))
                            bytesSinceEmit = 0L
                            lastEmitAt = now
                        }
                    }
                    output.flush()
                    trySend(DownloadState.Progress(written, total))
                }
            }
            trySend(DownloadState.Done(outFile))
            close()
        } catch (e: IOException) {
            outFile.delete()
            trySend(DownloadState.Failed(DownloadState.Reason.Network))
            close()
        } catch (e: SecurityException) {
            outFile.delete()
            trySend(DownloadState.Failed(DownloadState.Reason.Disk))
            close()
        } finally {
            response?.close()
        }

        awaitClose {
            if (!call.isCanceled()) call.cancel()
        }
    }

    private fun sanitizeTag(name: String): String =
        name.removeSuffix(".apk").replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        private const val UPDATE_SUBDIR = "update"
        private const val BUFFER_BYTES = 16 * 1024
        private const val PROGRESS_BYTES = 64L * 1024
        private const val PROGRESS_MS = 250L
    }
}
