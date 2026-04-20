package com.yutori.ai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.yutori.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads the AI model file, verifies its SHA-256, and stamps the
 * installed state into [AiSettingsRepository] on success.
 *
 * See `plans/ai-rules-spec.md` §5.2 / §5.3. Supports Range-based resume
 * (on re-enqueue after a process death mid-download) via the partial
 * `.tmp` file left by the previous attempt.
 *
 * Enqueueing path:
 * - [enqueue] uses `NetworkType.UNMETERED` (Wi-Fi) by default.
 * - [enqueueOnMobile] sets `NetworkType.CONNECTED` for the explicit
 *   "Download anyway" button (spec §5.2). Not a silent fallback —
 *   never called without user action.
 */
class ModelDownloadWorker(
    context: Context,
    params: androidx.work.WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: BuildConfig.AI_MODEL_URL
        val expectedSha = inputData.getString(KEY_SHA) ?: BuildConfig.AI_MODEL_SHA256
        val expectedSize = inputData.getLong(KEY_SIZE, BuildConfig.AI_MODEL_SIZE_BYTES)

        val target = ModelFiles.modelFile(applicationContext)
        val tmp = ModelFiles.tmpFile(applicationContext)

        if (target.exists() && target.length() == expectedSize) {
            // Already present — verify and update repo state. No network.
            return@withContext activateIfMatches(target, expectedSha, expectedSize)
        }

        val client = httpClient()
        val alreadyHave = if (tmp.exists()) tmp.length() else 0L

        val request = Request.Builder().url(url).apply {
            if (alreadyHave > 0) header("Range", "bytes=$alreadyHave-")
        }.build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching model", e)
            return@withContext Result.failure(
                workDataOf(KEY_ERROR to "network_error", KEY_MESSAGE to e.message),
            )
        }

        response.use {
            if (!it.isSuccessful && it.code != 206) {
                Log.e(TAG, "Unexpected HTTP ${it.code} ${it.message}")
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "http_${it.code}", KEY_MESSAGE to it.message),
                )
            }
            val body = it.body
                ?: return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "empty_body"),
                )
            val append = (it.code == 206)
            val total = expectedSize
            var written = alreadyHave

            body.byteStream().use { input ->
                java.io.FileOutputStream(tmp, append).use { out ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var lastProgressEmit = 0L
                    while (!isStopped) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        written += read
                        if (written - lastProgressEmit >= PROGRESS_EVERY_BYTES ||
                            written == total
                        ) {
                            setProgress(
                                workDataOf(
                                    KEY_DOWNLOADED to written,
                                    KEY_TOTAL to total,
                                ),
                            )
                            lastProgressEmit = written
                        }
                    }
                }
            }

            if (isStopped) {
                // User cancellation: leave the .tmp in place so a future
                // enqueue can resume via Range (spec §5.2).
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "cancelled"),
                )
            }
        }

        // SHA-256 verify on the completed tmp.
        val actualSha = sha256(tmp)
        if (!actualSha.equals(expectedSha, ignoreCase = true)) {
            Log.w(TAG, "SHA-256 mismatch: expected=$expectedSha actual=$actualSha")
            tmp.delete()
            return@withContext Result.failure(
                workDataOf(
                    KEY_ERROR to "checksum_mismatch",
                    KEY_MESSAGE to "Downloaded file didn't match the expected checksum.",
                ),
            )
        }

        if (!tmp.renameTo(target)) {
            Log.e(TAG, "Atomic rename failed: ${tmp.absolutePath} -> ${target.absolutePath}")
            return@withContext Result.failure(
                workDataOf(KEY_ERROR to "rename_failed"),
            )
        }

        AiSettingsRepositoryProvider.get(applicationContext)
            .markModelInstalled(sha256 = actualSha, timeMs = System.currentTimeMillis())

        Result.success(
            workDataOf(
                KEY_DOWNLOADED to target.length(),
                KEY_TOTAL to expectedSize,
                KEY_SHA to actualSha,
            ),
        )
    }

    private fun activateIfMatches(file: File, expectedSha: String, expectedSize: Long): Result {
        val actual = sha256(file)
        if (!actual.equals(expectedSha, ignoreCase = true)) {
            Log.w(TAG, "Existing file hash mismatch, deleting; will need a fresh download.")
            file.delete()
            return Result.failure(
                workDataOf(KEY_ERROR to "checksum_mismatch"),
            )
        }
        AiSettingsRepositoryProvider.get(applicationContext)
            .markModelInstalled(sha256 = actual, timeMs = System.currentTimeMillis())
        return Result.success(
            workDataOf(
                KEY_DOWNLOADED to expectedSize,
                KEY_TOTAL to expectedSize,
                KEY_SHA to actual,
            ),
        )
    }

    private fun sha256(file: File): String = ModelHashing.sha256(file)

    private fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val TAG = "YutoriAiDownload"

        const val UNIQUE_NAME = "ai_model_download"

        const val KEY_URL = "url"
        const val KEY_SHA = "sha256"
        const val KEY_SIZE = "size"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        const val KEY_MESSAGE = "message"

        private const val BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_EVERY_BYTES = 256L * 1024L
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L

        fun enqueue(context: Context) {
            enqueueInternal(context, NetworkType.UNMETERED)
        }

        fun enqueueOnMobile(context: Context) {
            enqueueInternal(context, NetworkType.CONNECTED)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        private fun enqueueInternal(context: Context, networkType: NetworkType) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}

/**
 * Single-source accessor for the app-scoped [AiSettingsRepository].
 * Exists because `CoroutineWorker` constructs via reflection and
 * cannot take the repository as a ctor parameter, so the worker has
 * to obtain it lazily at call time.
 *
 * `YutoriApp` registers its instance here in `onCreate`. Tests can
 * register their own fake via `register(context, fake)`.
 */
object AiSettingsRepositoryProvider {
    @Volatile
    private var instance: AiSettingsRepository? = null

    fun register(repository: AiSettingsRepository) {
        instance = repository
    }

    fun get(context: Context): AiSettingsRepository =
        instance ?: AiSettingsRepository(context).also { instance = it }
}
