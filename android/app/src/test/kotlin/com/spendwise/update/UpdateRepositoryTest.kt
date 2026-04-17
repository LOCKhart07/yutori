package com.spendwise.update

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateRepositoryTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun repo(token: String = "test-token"): UpdateRepository {
        val client = UpdateModule.createHttpClient(token = token)
        return UpdateModule.createRepository(
            client = client,
            baseUrl = server.url("/").toString(),
            tokenPresent = token.isNotEmpty(),
        )
    }

    @Test
    fun `200 with apk asset parses into domain model`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "tag_name": "v0.3.0",
                  "name": "Yutori 0.3.0",
                  "body": "- new thing\n- another thing",
                  "assets": [
                    {
                      "url": "https://api.github.com/repos/X/Y/releases/assets/1",
                      "name": "yutori-0.3.0.apk",
                      "size": 12345
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = repo().latestRelease()

        val release = result.getOrThrow()
        release.shouldNotBeNull()
        release.tagName shouldBe "v0.3.0"
        release.name shouldBe "Yutori 0.3.0"
        release.body shouldBe "- new thing\n- another thing"
        release.asset.shouldNotBeNull()
        release.asset!!.name shouldBe "yutori-0.3.0.apk"
        release.asset!!.sizeBytes shouldBe 12345L
        release.asset!!.url shouldBe "https://api.github.com/repos/X/Y/releases/assets/1"
    }

    @Test
    fun `200 without apk asset yields null asset`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "tag_name": "v0.3.0",
                  "name": "Yutori 0.3.0",
                  "body": "",
                  "assets": [
                    {
                      "url": "https://example.test/src.zip",
                      "name": "source.zip",
                      "size": 100
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val release = repo().latestRelease().getOrThrow()
        release.shouldNotBeNull()
        release.asset.shouldBeNull()
    }

    @Test
    fun `404 with token yields success with null release`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))

        val result = repo(token = "test-token").latestRelease()

        result.isSuccess shouldBe true
        result.getOrNull().shouldBeNull()
    }

    @Test
    fun `404 without token yields failure`() = runTest {
        // Private-repo-era safeguard: an anonymous 404 on a private repo
        // must surface as an error ("Updater offline") rather than a
        // silent "up to date". Removal target at #71(a).
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))

        val result = repo(token = "").latestRelease()

        result.isFailure shouldBe true
    }

    @Test
    fun `401 yields failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Bad credentials"}"""))

        val result = repo().latestRelease()

        result.isFailure shouldBe true
    }

    @Test
    fun `malformed json yields failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{not-json"))

        val result = repo().latestRelease()

        result.isFailure shouldBe true
    }

    @Test
    fun `bearer header present when token non-empty`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        repo(token = "pat-xyz").latestRelease()

        server.takeRequest().getHeader("Authorization") shouldBe "Bearer pat-xyz"
    }

    @Test
    fun `bearer header absent when token empty`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        repo(token = "").latestRelease()

        server.takeRequest().getHeader("Authorization").shouldBeNull()
    }

    @Test
    fun `standard github headers are set`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        repo().latestRelease()

        val recorded = server.takeRequest()
        recorded.getHeader("Accept") shouldBe "application/vnd.github+json"
        recorded.getHeader("X-GitHub-Api-Version") shouldBe "2022-11-28"
    }
}
