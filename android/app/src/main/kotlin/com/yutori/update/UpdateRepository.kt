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
    // Distinguishes "public repo + anonymous request + no releases yet"
    // (a legit 404 meaning up-to-date) from "private repo + missing PAT"
    // (also a 404, but a configuration bug we must surface). False while
    // the repo is private and no PAT is embedded. Removal target at
    // #71(a) — when the repo goes public, all 404s are legit and this
    // parameter becomes meaningless.
    private val tokenPresent: Boolean,
) {
    suspend fun latestRelease(): Result<LatestRelease?> = try {
        val response = api.latestRelease()
        when {
            response.code() == 404 -> {
                if (tokenPresent) {
                    // Authed 404 genuinely means "repo reachable, no
                    // releases yet." Treat as up-to-date.
                    Result.success(null)
                } else {
                    // Unauthed 404 on a private repo is how GitHub hides
                    // existence from anonymous callers. Surface the 404
                    // so the UI can prompt rotating the PAT.
                    Result.failure(UpdateCheckError.Http(404))
                }
            }
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
