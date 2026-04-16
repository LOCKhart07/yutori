package com.spendwise.transactions

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.ZoneId

class MonthKeyComputerTest {

    private val IST = ZoneId.of("Asia/Kolkata")
    private val UTC = ZoneId.of("UTC")
    private val LA = ZoneId.of("America/Los_Angeles")

    @Test
    fun `mid-month UTC epoch produces YYYY-MM`() {
        // 2026-04-15T12:00:00Z
        val ts = 1776254400000L
        MonthKeyComputer.of(ts, UTC) shouldBe "2026-04"
    }

    @Test
    fun `late-evening IST on last day of March stays in March`() {
        // 2026-03-31T23:55:00+05:30
        val ts = 1774981500000L
        MonthKeyComputer.of(ts, IST) shouldBe "2026-03"
    }

    @Test
    fun `timezone choice matters near month boundaries`() {
        // 2026-04-01T02:00:00+05:30 = 2026-03-31T20:30:00Z
        // Device-local (IST) sees April; UTC sees March. Decision
        // 2026-04-15: use device-local.
        val ts = 1774989000000L
        MonthKeyComputer.of(ts, IST) shouldBe "2026-04"
        MonthKeyComputer.of(ts, UTC) shouldBe "2026-03"
    }

    @Test
    fun `LA timezone buckets mid-day into the correct local month`() {
        // 2026-04-01T00:30:00-07:00 = 2026-04-01T07:30:00Z
        val ts = 1775028600000L
        MonthKeyComputer.of(ts, LA) shouldBe "2026-04"
    }

    @Test
    fun `January 1 at local midnight is in YYYY-01`() {
        // 2026-01-01T00:00:00+05:30 = 2025-12-31T18:30:00Z
        val ts = 1767205800000L
        MonthKeyComputer.of(ts, IST) shouldBe "2026-01"
    }

    @Test
    fun `December 31 at local 23 59 59 is in YYYY-12`() {
        // 2026-12-31T23:59:59+05:30
        val ts = 1798741799000L
        MonthKeyComputer.of(ts, IST) shouldBe "2026-12"
    }

    @Test
    fun `ofDevice uses the JVM default zone`() {
        val ts = 1776254400000L
        val expected = MonthKeyComputer.of(ts, ZoneId.systemDefault())
        MonthKeyComputer.ofDevice(ts) shouldBe expected
    }

    @Test
    fun `monthsBetween returns positive delta for later month`() {
        MonthKeyComputer.monthsBetween("2026-01", "2026-04") shouldBe 3L
    }

    @Test
    fun `monthsBetween returns negative delta for earlier month`() {
        MonthKeyComputer.monthsBetween("2026-04", "2026-01") shouldBe -3L
    }

    @Test
    fun `monthsBetween returns zero for same month`() {
        MonthKeyComputer.monthsBetween("2026-04", "2026-04") shouldBe 0L
    }

    @Test
    fun `monthsBetween crosses year boundary`() {
        MonthKeyComputer.monthsBetween("2025-10", "2026-03") shouldBe 5L
    }
}
