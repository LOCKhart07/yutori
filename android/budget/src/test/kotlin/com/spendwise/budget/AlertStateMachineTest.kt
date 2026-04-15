package com.spendwise.budget

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AlertStateMachineTest {

    private fun fire(
        percentUsed: Double,
        warn: Int = 80,
        already: Set<Int> = emptySet(),
    ): List<Int> = AlertStateMachine.thresholdsToFire(percentUsed, warn, already)

    // ---- thresholdsForMonth: correct threshold set ----

    @Test
    fun `threshold set contains 50, warn, and 100 at low percent`() {
        AlertStateMachine.thresholdsForMonth(80, 10.0) shouldContainExactly listOf(50, 80, 100)
    }

    @Test
    fun `threshold set deduplicates when warn equals a fixed threshold`() {
        AlertStateMachine.thresholdsForMonth(50, 10.0) shouldContainExactly listOf(50, 100)
        AlertStateMachine.thresholdsForMonth(100, 10.0) shouldContainExactly listOf(50, 100)
    }

    @Test
    fun `threshold set clamps warn below 1 to 1`() {
        AlertStateMachine.thresholdsForMonth(-5, 10.0) shouldContainExactly listOf(1, 50, 100)
    }

    @Test
    fun `threshold set clamps warn above 100 to 100`() {
        AlertStateMachine.thresholdsForMonth(150, 10.0) shouldContainExactly listOf(50, 100)
    }

    @Test
    fun `threshold set expands with overage levels at exactly 110pct`() {
        AlertStateMachine.thresholdsForMonth(80, 110.0) shouldContainExactly
            listOf(50, 80, 100, 110)
    }

    @Test
    fun `threshold set expands through multiple overage levels`() {
        AlertStateMachine.thresholdsForMonth(80, 137.0) shouldContainExactly
            listOf(50, 80, 100, 110, 120, 130)
    }

    // ---- thresholdsToFire: firing on upward crossing ----

    @Test
    fun `nothing fires below the 50pct mark`() {
        fire(49.9) shouldBe emptyList()
    }

    @Test
    fun `50pct threshold fires exactly at 50`() {
        fire(50.0) shouldContainExactly listOf(50)
    }

    @Test
    fun `80pct warn threshold fires when crossed`() {
        fire(80.0) shouldContainExactly listOf(50, 80)
    }

    @Test
    fun `100pct threshold fires when crossed`() {
        fire(100.0) shouldContainExactly listOf(50, 80, 100)
    }

    @Test
    fun `110pct fires for 10pct overage`() {
        fire(110.0) shouldContainExactly listOf(50, 80, 100, 110)
    }

    @Test
    fun `multiple overage levels fire together when discovered late`() {
        // First check of the month happens at 137pct (late-arriving batch).
        // All not-yet-fired thresholds cross.
        fire(137.0) shouldContainExactly listOf(50, 80, 100, 110, 120, 130)
    }

    // ---- thresholdsToFire: idempotency with alreadyFired ----

    @Test
    fun `already-fired thresholds are not re-fired`() {
        fire(85.0, already = setOf(50, 80)) shouldBe emptyList()
    }

    @Test
    fun `only the newly-crossed threshold fires when others already fired`() {
        fire(102.0, already = setOf(50, 80)) shouldContainExactly listOf(100)
    }

    @Test
    fun `all-already-fired still returns empty when no new crossing`() {
        fire(95.0, already = setOf(50, 80)) shouldBe emptyList()
    }

    // ---- §7.5: refund below threshold does not re-arm ----

    @Test
    fun `refund dropping below 80pct does NOT re-fire 80 threshold`() {
        // Simulate the mid-month sequence:
        //   user hit 85% → 50 and 80 fired.
        //   refund arrives → percent drops to 72%.
        //   next evaluate: no new thresholds cross.
        val afterRefund = fire(72.0, already = setOf(50, 80))
        afterRefund shouldBe emptyList()
    }

    @Test
    fun `percent climbing back up after a dip does not re-fire already-crossed thresholds`() {
        // 85% → 50 and 80 fired. Refund drops to 72%. Then more spend brings
        // it back to 90%. 80 is already fired; 100 not yet crossed. Empty.
        fire(90.0, already = setOf(50, 80)) shouldBe emptyList()
    }

    // ---- warn threshold user-adjustable ----

    @Test
    fun `user-set warn at 60 fires first of the warn-tier`() {
        fire(62.0, warn = 60) shouldContainExactly listOf(50, 60)
    }

    @Test
    fun `user-set warn at 95 delays the warn-tier fire`() {
        fire(90.0, warn = 95) shouldContainExactly listOf(50)
        fire(96.0, warn = 95) shouldContainExactly listOf(50, 95)
    }

    // ---- snapshot-overload convenience ----

    @Test
    fun `snapshot overload pulls alreadyFired rows scoped to this month only`() {
        val snapshot = MonthSnapshot(
            monthKey = "2026-04",
            limitInr = 30_000.0,
            carryOverInr = 0.0,
            effectiveBudgetInr = 30_000.0,
            grossSpendInr = 24_000.0,
            refundsInr = 0.0,
            netSpendInr = 24_000.0,
            percentUsed = 80.0,
        )
        val already = listOf(
            AlertFiring("2026-03", 50),         // prior month — ignored
            AlertFiring("2026-03", 80),         // prior month — ignored
            AlertFiring("2026-04", 50),         // this month — respected
        )
        AlertStateMachine.thresholdsToFire(snapshot, 80, already) shouldContainExactly
            listOf(80)
    }

    // ---- edge cases ----

    @Test
    fun `zero percent produces no firings`() {
        fire(0.0) shouldBe emptyList()
    }

    @Test
    fun `negative percent (net refund exceeding spend) produces no firings`() {
        fire(-5.0) shouldBe emptyList()
    }

    @Test
    fun `very large percent fires all intermediate thresholds`() {
        val fired = fire(2500.0)
        fired.first() shouldBe 50
        fired shouldContainExactly listOf(50, 80, 100) +
            (110..2500 step 10).toList()
    }
}
