package com.yutori.feedback

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * Contract tests for the Issues API POST wrapper (#113). Verifies
 * what headers + body we send, what we map back to [SubmitResult],
 * and that an empty token short-circuits cleanly.
 */
class IssueReporterTest {

    private lateinit var server: MockWebServer
    private lateinit var reporter: IssueReporter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        reporter = IssueReporter(
            client = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build(),
            token = "pat_xyz",
            owner = "LOCKhart07",
            repo = "yutori",
            baseUrl = server.url("").toString().trimEnd('/'),
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `success maps 201 body to Success with number and htmlUrl`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"number":117,"html_url":"https://github.com/LOCKhart07/yutori/issues/117"}"""),
        )

        val result = reporter.submit("swipe gestures", "conflicting")

        result shouldBe SubmitResult.Success(
            number = 117,
            htmlUrl = "https://github.com/LOCKhart07/yutori/issues/117",
        )
    }

    @Test
    fun `request targets owner-repo path and carries bearer token + api version header`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"number":1,"html_url":"u"}"""),
        )

        reporter.submit("t", "b")

        val rec = server.takeRequest()
        rec.path shouldBe "/repos/LOCKhart07/yutori/issues"
        rec.method shouldBe "POST"
        rec.getHeader("Authorization") shouldBe "Bearer pat_xyz"
        rec.getHeader("X-GitHub-Api-Version") shouldBe "2022-11-28"
        rec.getHeader("Accept") shouldBe "application/vnd.github+json"
    }

    @Test
    fun `request body carries title and body verbatim as JSON`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"number":1,"html_url":"u"}"""),
        )

        reporter.submit(
            title = "Dashboard month lag",
            body = "Happens when swiping quickly.\n---\nApp: 0.5.1",
        )

        val sent = server.takeRequest().body.readUtf8()
        sent shouldContain "\"title\":\"Dashboard month lag\""
        sent shouldContain "\"body\":\"Happens when swiping quickly.\\n---\\nApp: 0.5.1\""
    }

    @Test
    fun `empty token short-circuits without making a request`() {
        val noTokenReporter = IssueReporter(
            client = OkHttpClient(),
            token = "",
            baseUrl = server.url("").toString().trimEnd('/'),
        )

        val result = noTokenReporter.submit("t", "b")

        result shouldBe SubmitResult.NoToken
        server.requestCount shouldBe 0
    }

    @Test
    fun `non-2xx maps to Http with the status code`() {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"message":"Validation failed"}"""))

        val result = reporter.submit("t", "b")

        result shouldBe SubmitResult.Http(422)
    }

    @Test
    fun `2xx with missing html_url falls through to BadResponse`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"number":9}"""),
        )

        val result = reporter.submit("t", "b")

        result shouldBe SubmitResult.BadResponse
    }

    @Test
    fun `malformed JSON body maps to BadResponse`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("not-json"))

        val result = reporter.submit("t", "b")

        result.shouldBeInstanceOf<SubmitResult.BadResponse>()
    }
}
