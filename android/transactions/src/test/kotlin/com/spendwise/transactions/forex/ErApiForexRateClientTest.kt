package com.spendwise.transactions.forex

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class ErApiForexRateClientTest {

    private val client = ErApiForexRateClient()

    @Test
    fun `parses well-formed response`() {
        val body = """
            {"result":"success","base_code":"USD",
             "rates":{"EUR":0.91,"INR":83.52,"JPY":155.0}}
        """.trimIndent()
        val out = client.parseInrFromBody(body)
        out.shouldBeInstanceOf<ForexFetchResult.Success>()
        out.rateInrPerUnit shouldBe 83.52
    }

    @Test
    fun `whitespace and integer rates parse`() {
        val body = """{"rates":{ "INR" :  83 }}"""
        val out = client.parseInrFromBody(body)
        out.shouldBeInstanceOf<ForexFetchResult.Success>()
        out.rateInrPerUnit shouldBe 83.0
    }

    @Test
    fun `missing INR returns CURRENCY_UNKNOWN`() {
        val body = """{"rates":{"EUR":0.91,"JPY":155.0}}"""
        val out = client.parseInrFromBody(body)
        out.shouldBeInstanceOf<ForexFetchResult.Failure>()
        out.kind shouldBe ForexErrorKind.CURRENCY_UNKNOWN
    }

    @Test
    fun `zero rate treated as transient`() {
        val body = """{"rates":{"INR":0}}"""
        val out = client.parseInrFromBody(body)
        out.shouldBeInstanceOf<ForexFetchResult.Failure>()
        out.kind shouldBe ForexErrorKind.TRANSIENT
    }
}
