package com.spendwise.update

import com.spendwise.update.model.LatestRelease
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import java.io.IOException

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
                    // existence from anonymous callers. Surface as error
                    // so the user sees "Updater offline" instead of a
                    // silent false "Up to date."
                    Result.failure(IOException("HTTP 404 (no token configured)"))
                }
            }
            response.isSuccessful -> Result.success(response.body()?.toDomain())
            else -> Result.failure(IOException("HTTP ${response.code()}"))
        }
    } catch (e: IOException) {
        Result.failure(e)
    } catch (e: JsonDataException) {
        Result.failure(e)
    } catch (e: JsonEncodingException) {
        Result.failure(e)
    }
}
