package com.yutori.update

import com.yutori.update.model.DownloadState
import com.yutori.update.model.LatestRelease
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateDownloaderTest {
    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File

    @BeforeEach
    fun setUp() {
        server = MockWebServer().also { it.start() }
        cacheDir = Files.createTempDirectory("ut-cache").toFile()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    private fun asset(path: String = "/assets/1", name: String = "app-0.3.0.apk", size: Long) =
        LatestRelease.Asset(
            url = server.url(path).toString(),
            sizeBytes = size,
            name = name,
        )

    private fun downloader(): UpdateDownloader {
        val client = UpdateModule.createHttpClient()
        return UpdateDownloader(client, cacheDir)
    }

    @Test
    fun `happy path writes apk to cache and emits Done`() = runTest {
        val body = ByteArray(300_000) { (it % 256).toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setBody(Buffer().write(body)),
        )

        val emissions = downloader().download(asset(size = body.size.toLong())).toList()

        val done = emissions.last().shouldBeInstanceOf<DownloadState.Done>()
        done.apk.length() shouldBe body.size.toLong()
        done.apk.readBytes() shouldBe body
        // Lives under cacheDir/update/, name mangled from the asset name.
        done.apk.parentFile!!.name shouldBe "update"
        // At least one Progress emission with the full size.
        val progress = emissions.filterIsInstance<DownloadState.Progress>()
        progress.map { it.bytes } shouldContain body.size.toLong()
    }

    @Test
    fun `404 emits Failed NotFound`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val emissions = downloader().download(asset(size = 10)).toList()

        val failed = emissions.last().shouldBeInstanceOf<DownloadState.Failed>()
        failed.reason shouldBe DownloadState.Reason.NotFound
    }

    @Test
    fun `401 emits Failed Auth`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val emissions = downloader().download(asset(size = 10)).toList()

        val failed = emissions.last().shouldBeInstanceOf<DownloadState.Failed>()
        failed.reason shouldBe DownloadState.Reason.Auth
    }

    @Test
    fun `mid-stream disconnect emits Failed Network`() = runTest {
        val body = ByteArray(10_000) { 0x42 }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", body.size.toString())
                .setBody(Buffer().write(body.copyOfRange(0, 1000)))
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        val emissions = downloader().download(asset(size = body.size.toLong())).toList()

        emissions.last().shouldBeInstanceOf<DownloadState.Failed>()
            .reason shouldBe DownloadState.Reason.Network
    }

    @Test
    fun `purges previous apk files before downloading`() = runTest {
        val updateDir = File(cacheDir, "update").apply { mkdirs() }
        val stale = File(updateDir, "old.apk").apply { writeText("stale") }
        val body = ByteArray(50) { 1 }
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(body)))

        downloader().download(asset(size = body.size.toLong())).toList()

        stale.exists() shouldBe false
    }

    @Test
    fun `cross-host 302 to presigned URL is followed and bytes download`() = runTest {
        // The asset API URL returns a 302 to a presigned S3 URL on a
        // different host; the downloader must follow that cross-host hop
        // and stream the real bytes. (Public repo: anonymous — no auth
        // header on either leg, so nothing to leak across the redirect.)
        val body = ByteArray(64) { 9 }
        val redirectTarget = MockWebServer().also { it.start() }
        try {
            redirectTarget.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(body)))
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", redirectTarget.url("/signed").toString()),
            )

            val emissions = downloader().download(asset(size = body.size.toLong())).toList()

            val done = emissions.last().shouldBeInstanceOf<DownloadState.Done>()
            done.apk.readBytes() shouldBe body
            val redirectedRequest = redirectTarget.takeRequest()
            redirectedRequest.requestUrl.toString() shouldContain "/signed"
            // No Authorization is ever sent (anonymous public repo).
            (redirectedRequest.getHeader("Authorization") == null) shouldBe true
        } finally {
            redirectTarget.shutdown()
        }
    }
}
