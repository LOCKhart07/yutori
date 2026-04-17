package com.spendwise.update

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GithubAuthInterceptorTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `empty token leaves request unauthenticated`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(GithubAuthInterceptor(token = ""))
            .build()
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        server.takeRequest().getHeader("Authorization").shouldBeNull()
    }

    @Test
    fun `non-empty token attaches Bearer authorization header`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(GithubAuthInterceptor(token = "pat-xyz"))
            .build()
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        server.takeRequest().getHeader("Authorization") shouldBe "Bearer pat-xyz"
    }
}
