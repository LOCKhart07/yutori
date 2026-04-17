package com.spendwise.update

import com.spendwise.update.model.LatestRelease
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import java.io.IOException

class UpdateRepository(private val api: GithubApi) {
    suspend fun latestRelease(): Result<LatestRelease?> = try {
        val response = api.latestRelease()
        when {
            // 404 means the repo has zero releases yet. Treat as
            // "up to date" — no error surface for the user.
            response.code() == 404 -> Result.success(null)
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
