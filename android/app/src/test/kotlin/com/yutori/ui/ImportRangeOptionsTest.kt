package com.yutori.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Exercises the option list + sinceMs computation that
 * [ImportPromptScreen] uses. Drift here would mean the onboarding
 * import picker enqueues a different range than the in-app
 * `ImportDialog` that maps to the same labels.
 */
class ImportRangeOptionsTest {

    @Test
    fun `range list ships the same five options as the in-app dialog`() {
        val labels = ImportRangeOptions.map { it.label }
        labels shouldBe listOf(
            "Last 1 month",
            "Last 3 months",
            "Last 6 months",
            "Last 1 year",
            "Everything on this phone",
        )
    }

    @Test
    fun `sinceMsFor returns 0 for the everything sentinel`() {
        val everything = ImportRangeOptions.last()
        sinceMsFor(everything) shouldBe 0L
    }

    @Test
    fun `sinceMsFor subtracts the option's daysBack from now`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(2026, 4, 23, 12, 0, 0, 0, zone)
        val nowMs = now.toInstant().toEpochMilli()

        val threeMonths = ImportRangeOptions[1]   // Last 3 months → 90 days
        val sinceMs = sinceMsFor(threeMonths, nowMs = nowMs, zone = zone)
        val sinceInstant = Instant.ofEpochMilli(sinceMs)
        sinceInstant shouldBe now.minusDays(90).toInstant()
    }

    @Test
    fun `last 1 month uses 30 days, not a calendar month`() {
        // Calendar months vary 28-31 days; the existing dialog uses
        // a fixed 30-day window so behavior is predictable across
        // months. The onboarding picker must match.
        ImportRangeOptions.first().daysBack shouldBe 30
    }
}
