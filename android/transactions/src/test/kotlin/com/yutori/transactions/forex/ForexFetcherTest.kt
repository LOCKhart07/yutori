package com.yutori.transactions.forex

import com.yutori.classifier.BudgetEffect
import com.yutori.parser.Classification
import com.yutori.transactions.TransactionRow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ForexFetcherTest {

    @Test
    fun `empty input returns empty result without calling client`() = runTest {
        val client = RecordingClient(ForexFetchResult.Success(83.5))
        val out = ForexFetcher(client).resolvePending(
            pending = emptyList(),
            nowMs = 0L,
            dateKeyOf = { "2026-04-15" },
        )
        out.resolved.shouldHaveSize(0)
        out.stillPending.shouldHaveSize(0)
        out.failures.shouldHaveSize(0)
        client.calls shouldBe emptyList()
    }

    @Test
    fun `success path applies rate to all rows of that currency with one fetch`() = runTest {
        val rows = listOf(usdRow(id = 1, amount = 10.0), usdRow(id = 2, amount = 20.0))
        val client = RecordingClient(ForexFetchResult.Success(83.5))
        val out = ForexFetcher(client).resolvePending(
            pending = rows,
            nowMs = 100L,
            dateKeyOf = { "2026-04-15" },
        )
        // Both rows converted, one fetch made.
        client.calls shouldBe listOf("USD")
        out.resolved.shouldHaveSize(2)
        out.stillPending.shouldHaveSize(0)
        out.resolved[0].row.inrAmount!! shouldBe (835.0 plusOrMinus 1e-9)
        out.resolved[0].row.exchangeRate shouldBe 83.5
        out.resolved[0].row.rateSource shouldBe "exchangerate-api.com"
        out.resolved[1].row.inrAmount!! shouldBe (1670.0 plusOrMinus 1e-9)
    }

    @Test
    fun `different currencies each fetch independently`() = runTest {
        val rows = listOf(usdRow(id = 1, amount = 10.0), eurRow(id = 2, amount = 10.0))
        val client = MultiClient(mapOf(
            "USD" to ForexFetchResult.Success(83.5),
            "EUR" to ForexFetchResult.Success(90.0),
        ))
        val out = ForexFetcher(client).resolvePending(
            pending = rows,
            nowMs = 0L,
            dateKeyOf = { "2026-04-15" },
        )
        out.resolved.shouldHaveSize(2)
        client.calls.toSet() shouldBe setOf("USD", "EUR")
    }

    @Test
    fun `cache hit avoids client call`() = runTest {
        val client = RecordingClient(ForexFetchResult.Success(83.5))
        val holder = ForexFetcher.CacheHolder(
            ForexRateCache().put(
                currency = "USD",
                dateKey = "2026-04-15",
                rate = 84.0,
                capturedAtMs = 0L,
            ),
        )
        val fetcher = ForexFetcher(client, holder)
        val out = fetcher.resolvePending(
            pending = listOf(usdRow(id = 1, amount = 10.0)),
            nowMs = 1000L,
            dateKeyOf = { "2026-04-15" },
        )
        client.calls.shouldHaveSize(0)
        out.resolved.single().row.exchangeRate shouldBe 84.0
    }

    @Test
    fun `successful fetch populates cache for subsequent calls`() = runTest {
        val client = RecordingClient(ForexFetchResult.Success(83.5))
        val holder = ForexFetcher.CacheHolder(ForexRateCache())
        val fetcher = ForexFetcher(client, holder)
        // First call — misses cache, fetches.
        fetcher.resolvePending(
            pending = listOf(usdRow(id = 1, amount = 10.0)),
            nowMs = 0L,
            dateKeyOf = { "2026-04-15" },
        )
        client.calls shouldBe listOf("USD")
        // Second call — same date/currency, should hit cache.
        fetcher.resolvePending(
            pending = listOf(usdRow(id = 2, amount = 20.0)),
            nowMs = 1000L,
            dateKeyOf = { "2026-04-15" },
        )
        client.calls shouldBe listOf("USD") // unchanged
    }

    @Test
    fun `transient failure surfaces as failure and leaves rows pending`() = runTest {
        val rows = listOf(usdRow(id = 1, amount = 10.0), usdRow(id = 2, amount = 20.0))
        val client = RecordingClient(
            ForexFetchResult.Failure(ForexErrorKind.TRANSIENT, "timeout"),
        )
        val out = ForexFetcher(client).resolvePending(
            pending = rows,
            nowMs = 0L,
            dateKeyOf = { "2026-04-15" },
        )
        out.resolved.shouldHaveSize(0)
        out.stillPending.shouldHaveSize(2)
        out.failures.shouldHaveSize(1)
        out.failures.single().kind shouldBe ForexErrorKind.TRANSIENT
        out.failures.single().currency shouldBe "USD"
    }

    @Test
    fun `one currency failing doesn't block another currency succeeding`() = runTest {
        val rows = listOf(usdRow(id = 1, amount = 10.0), eurRow(id = 2, amount = 5.0))
        val client = MultiClient(mapOf(
            "USD" to ForexFetchResult.Failure(ForexErrorKind.QUOTA_EXHAUSTED),
            "EUR" to ForexFetchResult.Success(90.0),
        ))
        val out = ForexFetcher(client).resolvePending(
            pending = rows,
            nowMs = 0L,
            dateKeyOf = { "2026-04-15" },
        )
        out.resolved.shouldHaveSize(1)
        out.resolved.single().row.originalCurrency shouldBe "EUR"
        out.stillPending.shouldHaveSize(1)
        out.stillPending.single().originalCurrency shouldBe "USD"
        out.failures.single().kind shouldBe ForexErrorKind.QUOTA_EXHAUSTED
    }

    // ---- helpers ----

    private fun usdRow(id: Long, amount: Double) = pendingRow(id, "USD", amount)
    private fun eurRow(id: Long, amount: Double) = pendingRow(id, "EUR", amount)

    private fun pendingRow(id: Long, currency: String, amount: Double) = TransactionRow(
        id = id,
        classification = Classification.CC_TRANSACTION,
        classificationOriginal = null,
        budgetEffect = BudgetEffect.SPEND,
        inrAmount = null,
        originalAmount = amount,
        originalCurrency = currency,
        rateSource = "pending",
        merchant = "X",
        merchantKey = "x",
        category = null,
        accountId = null,
        last4 = "1111",
        occurredAtMs = 1_700_000_000_000L,
        monthKey = "2026-04",
    )

    private class RecordingClient(private val result: ForexFetchResult) : ForexRateClient {
        val calls = mutableListOf<String>()
        override suspend fun fetch(currency: String): ForexFetchResult {
            calls += currency
            return result
        }
    }

    private class MultiClient(private val map: Map<String, ForexFetchResult>) : ForexRateClient {
        val calls = mutableListOf<String>()
        override suspend fun fetch(currency: String): ForexFetchResult {
            calls += currency
            return map[currency] ?: ForexFetchResult.Failure(ForexErrorKind.CURRENCY_UNKNOWN)
        }
    }
}
