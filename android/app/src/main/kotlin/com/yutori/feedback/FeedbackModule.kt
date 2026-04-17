package com.yutori.feedback

import com.yutori.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Factory for [IssueReporter]. Dedicated OkHttpClient so the
 * feedback flow has its own connection pool + timeouts, independent
 * from the autoupdater's client.
 */
object FeedbackModule {

    fun createReporter(
        token: String = BuildConfig.GITHUB_ISSUES_TOKEN,
    ): IssueReporter {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return IssueReporter(client = client, token = token)
    }
}
