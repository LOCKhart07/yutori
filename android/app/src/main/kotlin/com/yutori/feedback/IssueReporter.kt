package com.yutori.feedback

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Thin wrapper around the GitHub Issues REST API — creates one issue
 * per call. Used by the Settings → Send feedback flow (#113).
 *
 * The token lifecycle mirrors the autoupdater PAT: baked into
 * `BuildConfig.GITHUB_ISSUES_TOKEN` at build time, injected here.
 * An empty token is a first-class state — [submit] returns
 * [SubmitResult.NoToken] so the UI can disable Send gracefully
 * instead of firing a request that would 401.
 */
class IssueReporter(
    private val client: OkHttpClient,
    private val token: String,
    private val owner: String = "LOCKhart07",
    private val repo: String = "yutori",
    private val baseUrl: String = "https://api.github.com",
    private val moshi: Moshi = DefaultMoshi,
) {

    /** Callable from a coroutine on an IO dispatcher. Blocking under the hood. */
    fun submit(title: String, body: String): SubmitResult {
        if (token.isEmpty()) return SubmitResult.NoToken

        val payload = CreateIssueRequest(title = title, body = body)
        val requestJson = moshi.adapter(CreateIssueRequest::class.java).toJson(payload)

        val httpRequest = Request.Builder()
            .url("$baseUrl/repos/$owner/$repo/issues")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .post(requestJson.toRequestBody(JSON))
            .build()

        return try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use SubmitResult.Http(response.code)
                }
                val responseJson = response.body?.string()
                    ?: return@use SubmitResult.BadResponse
                val parsed = try {
                    moshi.adapter(CreateIssueResponse::class.java).fromJson(responseJson)
                } catch (_: Exception) {
                    null
                }
                val number = parsed?.number
                val htmlUrl = parsed?.htmlUrl
                if (number == null || htmlUrl.isNullOrBlank()) {
                    SubmitResult.BadResponse
                } else {
                    SubmitResult.Success(number = number, htmlUrl = htmlUrl)
                }
            }
        } catch (e: IOException) {
            SubmitResult.Network(e.message ?: "connection failed")
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val DefaultMoshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    }
}

sealed interface SubmitResult {
    data class Success(val number: Int, val htmlUrl: String) : SubmitResult
    data class Http(val code: Int) : SubmitResult
    data class Network(val message: String) : SubmitResult
    /** Token wasn't baked into this build — feature disabled. */
    data object NoToken : SubmitResult
    /** 2xx but the response didn't match the expected shape. */
    data object BadResponse : SubmitResult
}

@JsonClass(generateAdapter = false)
internal data class CreateIssueRequest(
    val title: String,
    val body: String,
)

@JsonClass(generateAdapter = false)
internal data class CreateIssueResponse(
    val number: Int?,
    @Json(name = "html_url") val htmlUrl: String?,
)
