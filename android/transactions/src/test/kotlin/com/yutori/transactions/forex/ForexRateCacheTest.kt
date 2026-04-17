package com.yutori.transactions.forex

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ForexRateCacheTest {

    private val now = 1_700_000_000_000L
    private val oneHour = 60L * 60 * 1000L

    @Test
    fun `empty cache returns null`() {
        ForexRateCache().lookup("USD", "2026-03-03", now).shouldBeNull()
    }

    @Test
    fun `put then lookup within TTL returns the rate`() {
        val c = ForexRateCache().put("USD", "2026-03-03", 83.5, now)
        c.lookup("USD", "2026-03-03", now) shouldBe 83.5
    }

    @Test
    fun `lookup just before TTL expiry still succeeds`() {
        val c = ForexRateCache().put("USD", "2026-03-03", 83.5, now - 23 * oneHour)
        c.lookup("USD", "2026-03-03", now) shouldBe 83.5
    }

    @Test
    fun `lookup past 24h TTL returns null`() {
        val c = ForexRateCache().put(
            "USD", "2026-03-03", 83.5,
            now - 25 * oneHour,
        )
        c.lookup("USD", "2026-03-03", now).shouldBeNull()
    }

    @Test
    fun `different currency same date does not match`() {
        val c = ForexRateCache().put("USD", "2026-03-03", 83.5, now)
        c.lookup("EUR", "2026-03-03", now).shouldBeNull()
    }

    @Test
    fun `different date same currency does not match`() {
        val c = ForexRateCache().put("USD", "2026-03-03", 83.5, now)
        c.lookup("USD", "2026-03-04", now).shouldBeNull()
    }

    @Test
    fun `put returns a new cache without mutating the original`() {
        val base = ForexRateCache()
        base.put("USD", "2026-03-03", 83.5, now)
        base.size() shouldBe 0  // original unchanged
    }

    @Test
    fun `put over an existing key overwrites`() {
        val c = ForexRateCache()
            .put("USD", "2026-03-03", 83.0, now - oneHour)
            .put("USD", "2026-03-03", 83.5, now)
        c.lookup("USD", "2026-03-03", now) shouldBe 83.5
        c.size() shouldBe 1
    }

    @Test
    fun `currency is case-sensitive`() {
        val c = ForexRateCache().put("usd", "2026-03-03", 83.5, now)
        c.lookup("USD", "2026-03-03", now).shouldBeNull()
        c.lookup("usd", "2026-03-03", now) shouldBe 83.5
    }

    @Test
    fun `custom TTL of 1 hour expires faster than the default`() {
        val c = ForexRateCache(ttlMs = oneHour)
            .put("USD", "2026-03-03", 83.5, now - 2 * oneHour)
        c.lookup("USD", "2026-03-03", now).shouldBeNull()
    }
}
