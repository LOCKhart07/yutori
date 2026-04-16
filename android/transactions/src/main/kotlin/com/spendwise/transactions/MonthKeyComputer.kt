package com.spendwise.transactions

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Computes a transaction's `month_key` from its `occurred_at_ms` and
 * the device timezone at the time of insert. See business-logic-spec.md
 * §6.5.
 *
 * Rules:
 *   - `month_key` is `YYYY-MM` formatted in the supplied timezone.
 *   - Computed once at insert; immutable thereafter. Later timezone
 *     changes do NOT re-bucket past transactions.
 *   - `occurredAtMs` from the bank SMS is the authoritative time.
 *
 * This is an intentionally thin wrapper — the reason it exists as its
 * own object is to centralize the choice of timezone (device-local
 * per decision 2026-04-15) so every call site uses the same rule.
 */
object MonthKeyComputer {

    private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM")

    /** Compute month_key in the supplied zone. */
    fun of(occurredAtMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(occurredAtMs).atZone(zone).format(FORMATTER)

    /** Compute month_key using the JVM's default zone (device-local). */
    fun ofDevice(occurredAtMs: Long): String =
        of(occurredAtMs, ZoneId.systemDefault())

    /** Return month_key shifted by [deltaMonths] (can be negative). */
    fun shift(monthKey: String, deltaMonths: Long): String =
        YearMonth.parse(monthKey, FORMATTER).plusMonths(deltaMonths).format(FORMATTER)

    /**
     * Whole-month distance from [from] to [to] (`to - from`). Positive
     * when [to] is later than [from], zero when equal, negative when
     * earlier.
     */
    fun monthsBetween(from: String, to: String): Long =
        YearMonth.parse(from, FORMATTER).until(
            YearMonth.parse(to, FORMATTER),
            java.time.temporal.ChronoUnit.MONTHS,
        )
}
