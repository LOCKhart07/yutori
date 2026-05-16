package com.yutori.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Pure logic behind the budget-setup carry-over card: when it shows,
 * and that Effective = limit + carryOver (business-logic-spec §6.1).
 */
class BudgetBreakdownTest {

    @Test
    fun `zero carry-over yields null so the card stays hidden`() {
        budgetBreakdown(limitInr = 60_000.0, carryOverInr = 0.0) shouldBe null
    }

    @Test
    fun `null limit yields null`() {
        budgetBreakdown(limitInr = null, carryOverInr = 44_917.0) shouldBe null
    }

    @Test
    fun `negative limit yields null`() {
        budgetBreakdown(limitInr = -1.0, carryOverInr = 44_917.0) shouldBe null
    }

    @Test
    fun `surplus adds carry-over on top of the limit`() {
        val b = budgetBreakdown(limitInr = 60_000.0, carryOverInr = 44_917.0)

        b shouldBe BudgetBreakdown(
            limitInr = 60_000.0,
            carryOverInr = 44_917.0,
            effectiveInr = 104_917.0,
        )
    }

    @Test
    fun `deficit lowers the effective budget below the limit`() {
        val b = budgetBreakdown(limitInr = 60_000.0, carryOverInr = -12_000.0)

        b shouldBe BudgetBreakdown(
            limitInr = 60_000.0,
            carryOverInr = -12_000.0,
            effectiveInr = 48_000.0,
        )
    }

    @Test
    fun `zero limit with carry-over still shows, effective equals carry-over`() {
        val b = budgetBreakdown(limitInr = 0.0, carryOverInr = 44_917.0)

        b shouldBe BudgetBreakdown(
            limitInr = 0.0,
            carryOverInr = 44_917.0,
            effectiveInr = 44_917.0,
        )
    }
}
