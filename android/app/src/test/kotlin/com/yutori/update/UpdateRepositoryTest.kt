package com.yutori.update

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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

    private fun repo(): UpdateRepository {
        val client = UpdateModule.createHttpClient()
        return UpdateModule.createRepository(
            client = client,
            baseUrl = server.url("/").toString(),
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
        release.asset.name shouldBe "yutori-0.3.0.apk"
        release.asset.sizeBytes shouldBe 12345L
        release.asset.url shouldBe "https://api.github.com/repos/X/Y/releases/assets/1"
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
    fun `404 yields success with null release (public repo, no releases yet)`() = runTest {
        // Public repo: the Releases API is anonymous and a 404 just means
        // nothing is published yet, so it must read as up-to-date, never
        // as an error.
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))

        val result = repo().latestRelease()

        result.isSuccess shouldBe true
        result.getOrNull().shouldBeNull()
    }

    @Test
    fun `401 yields Http 401 failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Bad credentials"}"""))

        val result = repo().latestRelease()

        result.exceptionOrNull() shouldBe UpdateCheckError.Http(401)
    }

    @Test
    fun `5xx yields Http failure carrying the code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = repo().latestRelease()

        result.exceptionOrNull() shouldBe UpdateCheckError.Http(503)
    }

    @Test
    fun `malformed json folds into Offline`() = runTest {
        // Moshi parse failure is indistinguishable to the user from a
        // dropped connection — keep the UI copy simple by folding both
        // into Offline.
        server.enqueue(MockResponse().setResponseCode(200).setBody("{not-json"))

        val result = repo().latestRelease()

        result.exceptionOrNull().shouldBeInstanceOf<UpdateCheckError.Offline>()
    }

    @Test
    fun `requests carry no Authorization header (anonymous public repo)`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        repo().latestRelease()

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
