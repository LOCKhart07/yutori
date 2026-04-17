package com.spendwise.update

import com.spendwise.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

// One-stop wiring for the in-app autoupdater. Both the JSON Releases API
// and the APK asset download share the same OkHttpClient so the auth
// interceptor is registered exactly once — matters for #71(a) removal.
object UpdateModule {
    fun createHttpClient(
        token: String = BuildConfig.GITHUB_RELEASES_TOKEN,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(GithubAuthInterceptor(token))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
