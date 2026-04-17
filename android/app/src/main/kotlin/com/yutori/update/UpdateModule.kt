package com.yutori.update

import android.content.Context
import com.yutori.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

// One-stop wiring for the in-app autoupdater. Both the JSON Releases API
// and the APK asset download share the same OkHttpClient so the auth
// interceptor is registered exactly once — matters for #71(a) removal.
object UpdateModule {
    private const val GITHUB_BASE_URL = "https://api.github.com/"

    fun createHttpClient(
        token: String = BuildConfig.GITHUB_RELEASES_TOKEN,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(GithubAuthInterceptor(token))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun createRepository(
        client: OkHttpClient,
        baseUrl: String = GITHUB_BASE_URL,
        // `#71(a)` cleanup: drop this default (and parameter) when the
        // repo goes public — every build will behave as if a token is
        // present because 404s will all be legitimate.
        tokenPresent: Boolean = BuildConfig.GITHUB_RELEASES_TOKEN.isNotEmpty(),
    ): UpdateRepository {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GithubApi::class.java)
        return UpdateRepository(api, tokenPresent = tokenPresent)
    }

    fun createDownloader(client: OkHttpClient, context: Context): UpdateDownloader =
        UpdateDownloader(client = client, cacheDir = context.cacheDir)

    fun createInstaller(context: Context): UpdateInstaller =
        UpdateInstaller(context.applicationContext)

    fun createPrefs(context: Context): UpdatePrefs = UpdatePrefs(context.applicationContext)
}
