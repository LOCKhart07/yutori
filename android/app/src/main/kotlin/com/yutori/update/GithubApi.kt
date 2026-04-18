package com.yutori.update

import com.yutori.update.model.LatestRelease
import com.squareup.moshi.Json
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers

interface GithubApi {
    @Headers(
        "Accept: application/vnd.github+json",
        "X-GitHub-Api-Version: 2022-11-28",
    )
    @GET("repos/LOCKhart07/yutori/releases/latest")
    suspend fun latestRelease(): Response<LatestReleaseDto>
}

data class LatestReleaseDto(
    @param:Json(name = "tag_name") val tagName: String,
    @param:Json(name = "name") val name: String?,
    @param:Json(name = "body") val body: String?,
    @param:Json(name = "assets") val assets: List<AssetDto> = emptyList(),
) {
    data class AssetDto(
        @param:Json(name = "url") val url: String,
        @param:Json(name = "name") val name: String,
        @param:Json(name = "size") val sizeBytes: Long,
    )
}

// We pick the first `.apk` asset. A release without one is user error
// on our end (should never happen on CI-built tags) — surface as "no
// update" rather than a parse failure.
fun LatestReleaseDto.toDomain(): LatestRelease = LatestRelease(
    tagName = tagName,
    name = name.orEmpty(),
    body = body.orEmpty(),
    asset = assets
        .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        ?.let { LatestRelease.Asset(url = it.url, sizeBytes = it.sizeBytes, name = it.name) },
)
