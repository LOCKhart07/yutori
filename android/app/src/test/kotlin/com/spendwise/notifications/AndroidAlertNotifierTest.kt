package com.spendwise.notifications

import com.spendwise.budget.MonthSnapshot
import com.spendwise.ingestion.ImpactNotification
import io.kotest.matchers.shouldBe
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.jupiter.api.Test

/**
 * Covers the title shape that issue #86 is about: the collapsed
 * notification shade truncates the body at ~35 chars and drops the
 * "· ₹X over" suffix, so the overshoot amount has to land in the title
 * for any pct ≥ 100 alert. Also guards the edge case where pct fires
 * at the exact boundary (overshoot rounds to ₹0) and a naive formatter
 * would post a misleading "Over by ₹0".
 */
class AndroidAlertNotifierTest {

    private val inr: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    private fun snap(net: Double, effective: Double): MonthSnapshot = MonthSnapshot(
        monthKey = "2026-04",
        limitInr = effective,
        carryOverInr = 0.0,
        effectiveBudgetInr = effective,
        grossSpendInr = net,
        refundsInr = 0.0,
        netSpendInr = net,
        percentUsed = if (effective > 0) net / effective * 100 else 0.0,
    )

    @Test
    fun `under-budget thresholds unchanged`() {
        val s = snap(net = 25_000.0, effective = 45_000.0)
        buildBudgetAlertTitle(50, s, inr) shouldBe "Budget: Half used"
        buildBudgetAlertTitle(80, s, inr) shouldBe "Budget: Approaching limit"
        // Warn threshold below 100 falls into the "Approaching limit"
        // bucket — any user-configured warn% between 51 and 99 shares
        // the same copy.
        buildBudgetAlertTitle(60, s, inr) shouldBe "Budget: Approaching limit"
    }

    @Test
    fun `pct equals 100 with overshoot surfaces Over by X`() {
        // Classic boundary crossing: spent = 45,178.41, budget = 45,000.
        val s = snap(net = 45_178.41, effective = 45_000.0)
        buildBudgetAlertTitle(100, s, inr) shouldBe "Budget: Over by ₹178"
    }

    @Test
    fun `pct equals 100 with zero overshoot stays bare`() {
        // Threshold fires when netSpend just barely crosses. If the
        // overshoot rounds to zero we'd otherwise post "Over by ₹0".
        val s = snap(net = 45_000.0, effective = 45_000.0)
        buildBudgetAlertTitle(100, s, inr) shouldBe "Budget: Over limit"
    }

    @Test
    fun `pct equals 100 with sub-rupee overshoot falls back to bare`() {
        // 0.3 rounds to 0 — same path as exact boundary.
        val s = snap(net = 45_000.30, effective = 45_000.0)
        buildBudgetAlertTitle(100, s, inr) shouldBe "Budget: Over limit"
    }

    @Test
    fun `pct greater than 100 surfaces X over`() {
        // 120% threshold — the scenario from #86's second screenshot.
        val s = snap(net = 54_778.41, effective = 45_000.0)
        buildBudgetAlertTitle(120, s, inr) shouldBe "Budget: 120% · ₹9,778 over"
    }

    @Test
    fun `pct greater than 100 with zero overshoot keeps pct suffix`() {
        // Pathological: threshold > 100 but overshoot rounds to 0. We
        // still want the pct visible so the user knows this isn't the
        // first crossing — just without the misleading "₹0 over".
        val s = snap(net = 45_000.0, effective = 45_000.0)
        buildBudgetAlertTitle(110, s, inr) shouldBe "Budget: 110% of limit"
    }

    @Test
    fun `negative raw overshoot is clamped`() {
        // Defensive: AlertStateMachine shouldn't fire a 100%+ threshold
        // with net < effective, but if something flukes the snapshot
        // the title shouldn't format a negative rupee figure.
        val s = snap(net = 44_000.0, effective = 45_000.0)
        buildBudgetAlertTitle(100, s, inr) shouldBe "Budget: Over limit"
    }

    // ───────── #17: pace context in body ─────────

    private val zone = ZoneId.of("Asia/Kolkata")
    // April is 30 days; using day 12 gives a clean 40% expected mark.
    private val day12April = LocalDate.of(2026, 4, 12)
        .atStartOfDay(zone).toInstant().toEpochMilli()
    private val day28April = LocalDate.of(2026, 4, 28)
        .atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `pace delta is expressed in days for a current-month snapshot`() {
        // Day 12 of 30-day April, user at 80% → expected 40, delta 40pp
        // → 12 days over pace.
        computePaceDeltaDays("2026-04", actualPct = 80.0, nowMs = day12April, zone = zone) shouldBe 12
        // Day 28, 80% spent → expected ≈ 93.3, delta ≈ -13.3pp → -4 days.
        computePaceDeltaDays("2026-04", actualPct = 80.0, nowMs = day28April, zone = zone) shouldBe -4
    }

    @Test
    fun `pace delta returns null for non-current months`() {
        computePaceDeltaDays("2026-03", actualPct = 80.0, nowMs = day12April, zone = zone) shouldBe null
        computePaceDeltaDays("2026-05", actualPct = 80.0, nowMs = day12April, zone = zone) shouldBe null
    }

    @Test
    fun `pace delta returns null for unparseable monthKey`() {
        computePaceDeltaDays("nonsense", actualPct = 50.0, nowMs = day12April, zone = zone) shouldBe null
    }

    @Test
    fun `paceSuffix hides the deadband and formats each branch`() {
        // Null and |δ| < 2 both render empty.
        paceSuffix(null) shouldBe ""
        paceSuffix(0) shouldBe ""
        paceSuffix(1) shouldBe ""
        paceSuffix(-1) shouldBe ""
        // Visible above the deadband, plural "days" on either side.
        paceSuffix(2) shouldBe " · 2 days over pace"
        paceSuffix(12) shouldBe " · 12 days over pace"
        paceSuffix(-2) shouldBe " · 2 days under pace"
        paceSuffix(-4) shouldBe " · 4 days under pace"
    }

    @Test
    fun `budget alert body appends pace suffix above deadband`() {
        val s = snap(net = 36_000.0, effective = 45_000.0)
        // No pace info at all.
        buildBudgetAlertBody(s, inr, paceDelta = null) shouldBe
            "Spent ₹36,000.00 of ₹45,000.00 · ₹9,000.00 remaining"
        // Inside deadband.
        buildBudgetAlertBody(s, inr, paceDelta = 1) shouldBe
            "Spent ₹36,000.00 of ₹45,000.00 · ₹9,000.00 remaining"
        // Over pace.
        buildBudgetAlertBody(s, inr, paceDelta = 12) shouldBe
            "Spent ₹36,000.00 of ₹45,000.00 · ₹9,000.00 remaining · 12 days over pace"
        // Under pace.
        buildBudgetAlertBody(s, inr, paceDelta = -4) shouldBe
            "Spent ₹36,000.00 of ₹45,000.00 · ₹9,000.00 remaining · 4 days under pace"
    }

    @Test
    fun `budget alert body appends pace suffix when over budget too`() {
        // Pace still useful when over budget — "you got here fast" vs
        // "you got here on schedule" is different information from the
        // flat overshoot amount.
        val s = snap(net = 54_778.41, effective = 45_000.0)
        buildBudgetAlertBody(s, inr, paceDelta = 15) shouldBe
            "Spent ₹54,778.41 of ₹45,000.00 · ₹9,778.41 over · 15 days over pace"
    }

    @Test
    fun `impact body appends pace suffix only when still under budget`() {
        val underBudget = impact(txAmount = 5_000.0, effective = 45_000.0, remaining = 12_500.0, daysLeft = 12)
        buildImpactBody(underBudget, inr, paceDelta = 15) shouldBe
            "11% of this month · ₹12,500.00 left for 12 days · 15 days over pace"
        buildImpactBody(underBudget, inr, paceDelta = null) shouldBe
            "11% of this month · ₹12,500.00 left for 12 days"

        // Over budget: "over by ₹X" reads the situation; pace noise is
        // suppressed.
        val overBudget = impact(txAmount = 5_000.0, effective = 45_000.0, remaining = -9_778.41, daysLeft = 3)
        buildImpactBody(overBudget, inr, paceDelta = 20) shouldBe
            "11% of this month · over by ₹9,778.41"
    }

    private fun impact(
        txAmount: Double,
        effective: Double,
        remaining: Double,
        daysLeft: Int,
    ): ImpactNotification = ImpactNotification(
        monthKey = "2026-04",
        txInrAmount = txAmount,
        effectiveBudgetInr = effective,
        percentOfBudget = if (effective > 0) txAmount / effective * 100 else 0.0,
        remainingInr = remaining,
        daysLeft = daysLeft,
        merchantLabel = "Zomato",
        transactionId = 42L,
    )
}
