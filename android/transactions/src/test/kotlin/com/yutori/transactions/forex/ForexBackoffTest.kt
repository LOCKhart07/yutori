package com.yutori.transactions.forex

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ForexBackoffTest {

    private val THIRTY_S = 30_000L
    private val FIVE_M = 5 * 60_000L
    private val ONE_H = 60 * 60_000L
    private val SIX_H = 6 * ONE_H
    private val TWENTY_FOUR_H = 24 * ONE_H

    // ---- TRANSIENT schedule: 30s → 5m → 1h ----

    @Test
    fun `TRANSIENT first retry is 30 seconds`() {
        ForexBackoff.nextDelayMs(ForexErrorKind.TRANSIENT, 1) shouldBe THIRTY_S
    }

    @Test
    fun `TRANSIENT second retry is 5 minutes`() {
        ForexBackoff.nextDelayMs(ForexErrorKind.TRANSIENT, 2) shouldBe FIVE_M
    }

    @Test
    fun `TRANSIENT third retry is 1 hour`() {
        ForexBackoff.nextDelayMs(ForexErrorKind.TRANSIENT, 3) shouldBe ONE_H
    }

    @Test
    fun `TRANSIENT pins at 1 hour past third attempt`() {
        ForexBackoff.nextDelayMs(ForexErrorKind.TRANSIENT, 4) shouldBe ONE_H
        ForexBackoff.nextDelayMs(ForexErrorKind.TRANSIENT, 50) shouldBe ONE_H
    }

    // ---- QUOTA schedule: 1h → 6h → 24h ----

    @Test
    fun `QUOTA first retry is 1 hour`() {
        ForexBackoff.nextDelayMs(ForexErrorKind.QUOTA_EXHAUSTED, 1) shouldBe ONE_H
    }

    @Test
    fun `QUOTA second retry is 6 hours`() {
        ForexBackoff.nextDelayMs(ForexErrorKind.QUOTA_EXHAUSTED, 2) shouldBe SIX_H
    }

    @Test
    fun `QUOTA third retry is 24 hours`() {
        ForexBackoff.nextDelayMs(ForexErrorKind.QUOTA_EXHAUSTED, 3) shouldBe TWENTY_FOUR_H
    }

    @Test
    fun `QUOTA pins at 24 hours past third attempt`() {
        ForexBackoff.nextDelayMs(ForexErrorKind.QUOTA_EXHAUSTED, 10) shouldBe TWENTY_FOUR_H
    }

    // ---- CURRENCY_UNKNOWN: no auto-retry ----

    @Test
    fun `CURRENCY_UNKNOWN never auto-retries`() {
        ForexBackoff.nextDelayMs(ForexErrorKind.CURRENCY_UNKNOWN, 1).shouldBeNull()
        ForexBackoff.nextDelayMs(ForexErrorKind.CURRENCY_UNKNOWN, 5).shouldBeNull()
    }

    // ---- contract ----

    @Test
    fun `zero consecutiveFailures is rejected as a programming error`() {
        assertThrows<IllegalArgumentException> {
            ForexBackoff.nextDelayMs(ForexErrorKind.TRANSIENT, 0)
        }
    }

    @Test
    fun `negative consecutiveFailures is rejected`() {
        assertThrows<IllegalArgumentException> {
            ForexBackoff.nextDelayMs(ForexErrorKind.QUOTA_EXHAUSTED, -1)
        }
    }
}
