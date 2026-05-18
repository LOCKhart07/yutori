package com.yutori.update

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

// One-stop wiring for the in-app autoupdater. The JSON Releases API and
// the APK asset download share one OkHttpClient. Now that the repo is
// public the Releases API is anonymous — no auth interceptor, no token.
object UpdateModule {
    private const val GITHUB_BASE_URL = "https://api.github.com/"

    fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun createRepository(
        client: OkHttpClient,
        baseUrl: String = GITHUB_BASE_URL,
    ): UpdateRepository {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GithubApi::class.java)
        return UpdateRepository(api)
    }

    fun createDownloader(client: OkHttpClient, context: Context): UpdateDownloader =
        UpdateDownloader(client = client, cacheDir = context.cacheDir)

    fun createInstaller(context: Context): UpdateInstaller =
        UpdateInstaller(context.applicationContext)

    fun createPrefs(context: Context): UpdatePrefs = UpdatePrefs(context.applicationContext)
}
