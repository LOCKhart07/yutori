package com.spendwise.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Pace-bucket logic is the visual contract for the traffic-light hero
 * (mocks: mockups/v3-behavioral.html §1). Same `actual_pct` reads as
 * different colours depending on `elapsed_pct`, so a regression here
 * silently misleads users.
 */
class DashboardDerivedTest {

    @Test
    fun `no budget -- always OnTrack (no signal to give)`() {
        DashboardDerived.computePace(
            hasBudget = false, actualPct = 0.0, expectedPct = null,
        ) shouldBe PaceBucket.OnTrack
    }

    @Test
    fun `actual at or above 100 percent is always Over`() {
        DashboardDerived.computePace(
            hasBudget = true, actualPct = 100.0, expectedPct = 90.0,
        ) shouldBe PaceBucket.Over
        DashboardDerived.computePace(
            hasBudget = true, actualPct = 105.0, expectedPct = 50.0,
        ) shouldBe PaceBucket.Over
    }

    @Test
    fun `well under-pace is Under -- day 25 of 30, 32 percent used`() {
        // expected = 25/30*100 = 83.3, actual = 32, delta = -51.3 → Under
        DashboardDerived.computePace(
            hasBudget = true, actualPct = 32.0, expectedPct = 83.3,
        ) shouldBe PaceBucket.Under
    }

    @Test
    fun `on-pace is OnTrack -- day 15 of 30, 50 percent used`() {
        // expected = 50, actual = 50, delta = 0 → OnTrack
        DashboardDerived.computePace(
            hasBudget = true, actualPct = 50.0, expectedPct = 50.0,
        ) shouldBe PaceBucket.OnTrack
    }

    @Test
    fun `same actual reads differently early vs late`() {
        // Day 25 + 32% used → Under (saving)
        val late = DashboardDerived.computePace(
            hasBudget = true, actualPct = 32.0, expectedPct = 83.3,
        )
        // Day 5 + 32% used → OverPace (burning fast)
        val early = DashboardDerived.computePace(
            hasBudget = true, actualPct = 32.0, expectedPct = 16.7,
        )
        late shouldBe PaceBucket.Under
        early shouldBe PaceBucket.OverPace
    }

    @Test
    fun `over-pace but not over-budget mid-month`() {
        // Day 15 + 70% → delta +20 → OverPace
        DashboardDerived.computePace(
            hasBudget = true, actualPct = 70.0, expectedPct = 50.0,
        ) shouldBe PaceBucket.OverPace
    }

    @Test
    fun `egregiously over-pace promotes to Over even under 100`() {
        // Day 5 + 50% → delta +33 → Over
        DashboardDerived.computePace(
            hasBudget = true, actualPct = 50.0, expectedPct = 16.7,
        ) shouldBe PaceBucket.Over
    }

    @Test
    fun `bucket boundary -- exactly minus 10 is Under (boundary tips into the more-favourable bucket)`() {
        DashboardDerived.computePace(
            hasBudget = true, actualPct = 40.0, expectedPct = 50.0,
        ) shouldBe PaceBucket.Under
        // Just inside on the heavy side
        DashboardDerived.computePace(
            hasBudget = true, actualPct = 40.1, expectedPct = 50.0,
        ) shouldBe PaceBucket.OnTrack
    }

    @Test
    fun `bucket boundary -- exactly plus 10 is OnTrack, just past flips OverPace`() {
        DashboardDerived.computePace(
            hasBudget = true, actualPct = 60.0, expectedPct = 50.0,
        ) shouldBe PaceBucket.OnTrack
        DashboardDerived.computePace(
            hasBudget = true, actualPct = 60.1, expectedPct = 50.0,
        ) shouldBe PaceBucket.OverPace
    }
}
