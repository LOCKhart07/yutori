package com.spendwise.notifications

import com.spendwise.budget.MonthSnapshot
import io.kotest.matchers.shouldBe
import java.text.NumberFormat
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
}
