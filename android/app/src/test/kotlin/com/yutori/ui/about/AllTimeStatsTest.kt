package com.yutori.ui.about

import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Test

class AllTimeStatsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    @Test
    fun `zero-spend days is SMS-date-set minus SPEND-date-set cardinality`() {
        val d1 = epoch("2026-04-10")
        val d2 = epoch("2026-04-11")
        val d3 = epoch("2026-04-12")
        val smsMs = listOf(d1, d1, d2, d3) // SMS on 3 distinct dates
        val spendMs = listOf(d1) // spend only on d1
        AllTimeStats.computeZeroSpendDays(smsMs, spendMs, zone) shouldBe 2
    }

    @Test
    fun `zero-spend days is zero when every SMS date had at least one SPEND`() {
        val d1 = epoch("2026-04-10")
        val d2 = epoch("2026-04-11")
        val smsMs = listOf(d1, d2)
        val spendMs = listOf(d1, d2, d2) // both days covered
        AllTimeStats.computeZeroSpendDays(smsMs, spendMs, zone) shouldBe 0
    }

    @Test
    fun `zero-spend days is zero when there are no SMS at all`() {
        AllTimeStats.computeZeroSpendDays(emptyList(), emptyList(), zone) shouldBe 0
    }

    @Test
    fun `zero-spend days ignores SPEND on dates the phone never received SMS`() {
        // Historical import can produce SPEND occurred_at from a month
        // where the device never received an SMS in that day (e.g.
        // imported from an earlier device). Those days aren't
        // "SMS-active days" and don't count toward the denominator.
        val smsDay = epoch("2026-04-15")
        val orphanSpendDay = epoch("2020-01-01") // way before smsDay
        AllTimeStats.computeZeroSpendDays(
            smsReceivedAtMs = listOf(smsDay),
            spendOccurredAtMs = listOf(orphanSpendDay),
            zone = zone,
        ) shouldBe 1 // the one smsDay that had no spend
    }

    // ---- formatInrShort ----

    @Test
    fun `formatInrShort renders under-1k with rupee prefix and no unit`() {
        formatInrShort(500.0) shouldBe "₹500"
        formatInrShort(999.9) shouldBe "₹999"
    }

    @Test
    fun `formatInrShort uses K for thousands, trimming trailing zero`() {
        formatInrShort(1_000.0) shouldBe "₹1K"
        formatInrShort(12_400.0) shouldBe "₹12.4K"
        formatInrShort(99_500.0) shouldBe "₹99.5K"
    }

    @Test
    fun `formatInrShort uses L at 1 lakh and up`() {
        formatInrShort(100_000.0) shouldBe "₹1L"
        formatInrShort(940_000.0) shouldBe "₹9.4L"
    }

    @Test
    fun `formatInrShort uses Cr at 1 crore and up`() {
        formatInrShort(10_000_000.0) shouldBe "₹1Cr"
        formatInrShort(12_600_000.0) shouldBe "₹1.3Cr"
    }

    @Test
    fun `formatInrShort handles zero`() {
        formatInrShort(0.0) shouldBe "₹0"
    }

    private fun epoch(ymd: String): Long {
        val (y, m, d) = ymd.split("-").map { it.toInt() }
        return LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
