package com.yutori.update

import com.yutori.update.model.LatestRelease
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import java.io.IOException

// Typed failure for the updater. Carried through Result.failure so the
// UI can distinguish "server said no" (code in hand — surface it so
// future-me can diagnose from the screenshot alone) from "we never
// got an answer" (network, DNS, or malformed body — all indistinguishable
// to the user). See docs/RELEASING.md "Updater status codes" for what
// each code means.
sealed class UpdateCheckError(message: String) : Throwable(message) {
    data class Http(val code: Int) : UpdateCheckError("HTTP $code")
    data object Offline : UpdateCheckError("offline")
}

class UpdateRepository(
    private val api: GithubApi,
) {
    suspend fun latestRelease(): Result<LatestRelease?> = try {
        val response = api.latestRelease()
        when {
            // The repo is public, so the Releases API is anonymous and a
            // 404 unambiguously means "no releases published yet" — treat
            // it as up-to-date rather than an error.
            response.code() == 404 -> Result.success(null)
            response.isSuccessful -> Result.success(response.body()?.toDomain())
            else -> Result.failure(UpdateCheckError.Http(response.code()))
        }
    } catch (e: IOException) {
        Result.failure(UpdateCheckError.Offline)
    } catch (e: JsonDataException) {
        Result.failure(UpdateCheckError.Offline)
    } catch (e: JsonEncodingException) {
        Result.failure(UpdateCheckError.Offline)
    }
}
