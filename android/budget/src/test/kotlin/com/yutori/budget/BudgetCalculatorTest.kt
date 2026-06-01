package com.yutori.budget

import com.yutori.classifier.BudgetEffect
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BudgetCalculatorTest {

    // Fixture helper: build a transaction for a given month with the
    // given effect and amount. Defaults keep test bodies terse.
    private fun tx(
        id: Long = nextId(),
        monthKey: String = "2026-04",
        effect: BudgetEffect = BudgetEffect.SPEND,
        amount: Double? = 100.0,
        occurredAtMs: Long = 1_700_000_000_000,
    ) = Transaction(id, monthKey, amount, effect, occurredAtMs)

    private var counter = 0L
    private fun nextId(): Long {
        counter += 1
        return counter
    }

    // ---- monthSpend ----

    @Test
    fun `monthSpend sums only SPEND transactions in the target month`() {
        val txs = listOf(
            tx(effect = BudgetEffect.SPEND, amount = 100.0),
            tx(effect = BudgetEffect.SPEND, amount = 250.5),
            tx(effect = BudgetEffect.REFUND, amount = 50.0),
            tx(effect = BudgetEffect.DROP, amount = 9999.0),
            tx(effect = BudgetEffect.INCOME, amount = 310000.0),
            tx(monthKey = "2026-03", effect = BudgetEffect.SPEND, amount = 999.0),
        )
        BudgetCalculator.monthSpend(txs, "2026-04") shouldBe (350.5 plusOrMinus 1e-9)
    }

    @Test
    fun `monthSpend excludes pending-FX transactions`() {
        val txs = listOf(
            tx(effect = BudgetEffect.SPEND, amount = 100.0),
            tx(effect = BudgetEffect.SPEND, amount = null),  // pending
            tx(effect = BudgetEffect.SPEND, amount = 50.0),
        )
        BudgetCalculator.monthSpend(txs, "2026-04") shouldBe (150.0 plusOrMinus 1e-9)
    }

    @Test
    fun `monthSpend returns 0 when no transactions in month`() {
        BudgetCalculator.monthSpend(emptyList(), "2026-04") shouldBe 0.0
    }

    // ---- monthRefunds ----

    @Test
    fun `monthRefunds sums only REFUND transactions in the target month`() {
        val txs = listOf(
            tx(effect = BudgetEffect.SPEND, amount = 100.0),
            tx(effect = BudgetEffect.REFUND, amount = 75.0),
            tx(effect = BudgetEffect.REFUND, amount = 25.0),
            tx(monthKey = "2026-03", effect = BudgetEffect.REFUND, amount = 500.0),
        )
        BudgetCalculator.monthRefunds(txs, "2026-04") shouldBe (100.0 plusOrMinus 1e-9)
    }

    @Test
    fun `monthRefunds excludes pending-FX transactions`() {
        val txs = listOf(
            tx(effect = BudgetEffect.REFUND, amount = null),
            tx(effect = BudgetEffect.REFUND, amount = 20.0),
        )
        BudgetCalculator.monthRefunds(txs, "2026-04") shouldBe (20.0 plusOrMinus 1e-9)
    }

    // ---- carryOver ----

    @Test
    fun `carryOver is zero when no prior budgets exist`() {
        val budgets = listOf(Budget("2026-04", 30_000.0))
        BudgetCalculator.carryOver(emptyList(), budgets, "2026-04") shouldBe 0.0
    }

    @Test
    fun `carryOver accumulates surplus from single prior month`() {
        // March: limit 30K, spent 20K → surplus +10K
        val budgets = listOf(
            Budget("2026-03", 30_000.0),
            Budget("2026-04", 30_000.0),
        )
        val txs = listOf(
            tx(monthKey = "2026-03", effect = BudgetEffect.SPEND, amount = 20_000.0),
        )
        BudgetCalculator.carryOver(txs, budgets, "2026-04") shouldBe
            (10_000.0 plusOrMinus 1e-9)
    }

    @Test
    fun `carryOver accumulates deficit from single prior month`() {
        // March: limit 30K, spent 40K → deficit -10K
        val budgets = listOf(
            Budget("2026-03", 30_000.0),
            Budget("2026-04", 30_000.0),
        )
        val txs = listOf(
            tx(monthKey = "2026-03", effect = BudgetEffect.SPEND, amount = 40_000.0),
        )
        BudgetCalculator.carryOver(txs, budgets, "2026-04") shouldBe
            (-10_000.0 plusOrMinus 1e-9)
    }

    @Test
    fun `carryOver adds refunds back to surplus`() {
        // March: limit 30K, spent 25K, refund 5K → effective consumption 20K
        // → surplus vs 30K limit = +10K
        val budgets = listOf(
            Budget("2026-03", 30_000.0),
            Budget("2026-04", 30_000.0),
        )
        val txs = listOf(
            tx(monthKey = "2026-03", effect = BudgetEffect.SPEND, amount = 25_000.0),
            tx(monthKey = "2026-03", effect = BudgetEffect.REFUND, amount = 5_000.0),
        )
        BudgetCalculator.carryOver(txs, budgets, "2026-04") shouldBe
            (10_000.0 plusOrMinus 1e-9)
    }

    @Test
    fun `carryOver combines alternating surplus and deficit across months`() {
        // Jan: 30K limit, spent 25K → +5K
        // Feb: 30K limit, spent 40K → -10K
        // Mar: 30K limit, spent 20K → +10K
        // Apr carryOver = 5 - 10 + 10 = +5K
        val budgets = listOf(
            Budget("2026-01", 30_000.0),
            Budget("2026-02", 30_000.0),
            Budget("2026-03", 30_000.0),
            Budget("2026-04", 30_000.0),
        )
        val txs = listOf(
            tx(monthKey = "2026-01", effect = BudgetEffect.SPEND, amount = 25_000.0),
            tx(monthKey = "2026-02", effect = BudgetEffect.SPEND, amount = 40_000.0),
            tx(monthKey = "2026-03", effect = BudgetEffect.SPEND, amount = 20_000.0),
        )
        BudgetCalculator.carryOver(txs, budgets, "2026-04") shouldBe
            (5_000.0 plusOrMinus 1e-9)
    }

    @Test
    fun `carryOver ignores transactions in months without a Budget row`() {
        // The user spent in February but never set a Feb budget; those
        // transactions don't flow into carry-over.
        val budgets = listOf(
            Budget("2026-01", 30_000.0),
            Budget("2026-04", 30_000.0),
        )
        val txs = listOf(
            tx(monthKey = "2026-01", effect = BudgetEffect.SPEND, amount = 25_000.0),
            tx(monthKey = "2026-02", effect = BudgetEffect.SPEND, amount = 50_000.0),
        )
        // Only Jan contributes: 30K - 25K = +5K.
        BudgetCalculator.carryOver(txs, budgets, "2026-04") shouldBe
            (5_000.0 plusOrMinus 1e-9)
    }

    @Test
    fun `carryOver excludes the target month's own budget`() {
        // If we included it, carryOver would depend on itself — broken.
        val budgets = listOf(
            Budget("2026-04", 30_000.0),
        )
        BudgetCalculator.carryOver(emptyList(), budgets, "2026-04") shouldBe 0.0
    }

    // ---- snapshot ----

    @Test
    fun `snapshot for month with no transactions yet`() {
        val budgets = listOf(Budget("2026-04", 30_000.0))
        val s = BudgetCalculator.snapshot(emptyList(), budgets, "2026-04")
        s.limitInr shouldBe 30_000.0
        s.carryOverInr shouldBe 0.0
        s.effectiveBudgetInr shouldBe 30_000.0
        s.grossSpendInr shouldBe 0.0
        s.refundsInr shouldBe 0.0
        s.netSpendInr shouldBe 0.0
        s.percentUsed shouldBe 0.0
    }

    @Test
    fun `snapshot with spend at 60pct of effective`() {
        val budgets = listOf(Budget("2026-04", 30_000.0))
        val txs = listOf(
            tx(effect = BudgetEffect.SPEND, amount = 18_000.0),
        )
        val s = BudgetCalculator.snapshot(txs, budgets, "2026-04")
        s.grossSpendInr shouldBe (18_000.0 plusOrMinus 1e-9)
        s.netSpendInr shouldBe (18_000.0 plusOrMinus 1e-9)
        s.percentUsed shouldBe (60.0 plusOrMinus 1e-9)
    }

    @Test
    fun `snapshot includes carryOver in effective budget and percent`() {
        val budgets = listOf(
            Budget("2026-03", 30_000.0),
            Budget("2026-04", 30_000.0),
        )
        val txs = listOf(
            // Mar: 20K spent → +10K carry
            tx(monthKey = "2026-03", effect = BudgetEffect.SPEND, amount = 20_000.0),
            // Apr: 20K spent of 40K effective → 50%
            tx(monthKey = "2026-04", effect = BudgetEffect.SPEND, amount = 20_000.0),
        )
        val s = BudgetCalculator.snapshot(txs, budgets, "2026-04")
        s.limitInr shouldBe 30_000.0
        s.carryOverInr shouldBe (10_000.0 plusOrMinus 1e-9)
        s.effectiveBudgetInr shouldBe (40_000.0 plusOrMinus 1e-9)
        s.percentUsed shouldBe (50.0 plusOrMinus 1e-9)
    }

    @Test
    fun `snapshot percent uses net spend so refunds lower it`() {
        val budgets = listOf(Budget("2026-04", 30_000.0))
        val txs = listOf(
            tx(effect = BudgetEffect.SPEND, amount = 30_000.0),
            tx(effect = BudgetEffect.REFUND, amount = 9_000.0),
        )
        val s = BudgetCalculator.snapshot(txs, budgets, "2026-04")
        s.grossSpendInr shouldBe (30_000.0 plusOrMinus 1e-9)
        s.refundsInr shouldBe (9_000.0 plusOrMinus 1e-9)
        s.netSpendInr shouldBe (21_000.0 plusOrMinus 1e-9)
        s.percentUsed shouldBe (70.0 plusOrMinus 1e-9)
    }

    @Test
    fun `snapshot percent can exceed 100 on overspend`() {
        val budgets = listOf(Budget("2026-04", 30_000.0))
        val txs = listOf(tx(effect = BudgetEffect.SPEND, amount = 36_000.0))
        val s = BudgetCalculator.snapshot(txs, budgets, "2026-04")
        s.percentUsed shouldBe (120.0 plusOrMinus 1e-9)
    }

    @Test
    fun `snapshot with zero effective budget returns percent 0`() {
        // No budget set → limitInr 0 → divide-by-zero guard
        val s = BudgetCalculator.snapshot(
            transactions = listOf(tx(effect = BudgetEffect.SPEND, amount = 5_000.0)),
            budgets = emptyList(),
            monthKey = "2026-04",
        )
        s.effectiveBudgetInr shouldBe 0.0
        s.percentUsed shouldBe 0.0
        s.netSpendInr shouldBe 5_000.0
    }

    @Test
    fun `snapshot excludes pending-FX transactions from all sums`() {
        val budgets = listOf(Budget("2026-04", 30_000.0))
        val txs = listOf(
            tx(effect = BudgetEffect.SPEND, amount = 10_000.0),
            tx(effect = BudgetEffect.SPEND, amount = null),   // pending
            tx(effect = BudgetEffect.REFUND, amount = null),  // pending
            tx(effect = BudgetEffect.REFUND, amount = 2_000.0),
        )
        val s = BudgetCalculator.snapshot(txs, budgets, "2026-04")
        s.grossSpendInr shouldBe (10_000.0 plusOrMinus 1e-9)
        s.refundsInr shouldBe (2_000.0 plusOrMinus 1e-9)
        s.netSpendInr shouldBe (8_000.0 plusOrMinus 1e-9)
    }

    // ---- #14: budgets roll forward ----

    @Test
    fun `snapshot uses currentMonthLimit override when provided`() {
        // April has no explicit budget row — only March does. The
        // caller resolves the inherited limit and passes it directly.
        val priorBudgets = listOf(Budget("2026-03", 30_000.0))
        val txs = listOf(
            // March: spent 20K of 30K → +10K carry.
            tx(monthKey = "2026-03", effect = BudgetEffect.SPEND, amount = 20_000.0),
            // April: 15K spent of 40K effective (30K inherited + 10K carry).
            tx(monthKey = "2026-04", effect = BudgetEffect.SPEND, amount = 15_000.0),
        )
        val s = BudgetCalculator.snapshot(
            transactions = txs,
            budgets = priorBudgets,
            monthKey = "2026-04",
            currentMonthLimit = 30_000.0,
        )
        s.limitInr shouldBe 30_000.0
        s.carryOverInr shouldBe (10_000.0 plusOrMinus 1e-9)
        s.effectiveBudgetInr shouldBe (40_000.0 plusOrMinus 1e-9)
        s.netSpendInr shouldBe (15_000.0 plusOrMinus 1e-9)
        s.percentUsed shouldBe (37.5 plusOrMinus 1e-9)
    }

    @Test
    fun `snapshot inherited month does not add a fresh carry-over contribution`() {
        // Guards the load-bearing invariant from the #14 spec: a month
        // with no explicit row must not contribute (inheritedLimit − net)
        // as its own carry-over term, or every subsequent month would
        // inflate forever.
        //
        // Viewing May (inherited from March). April has no row, no txs.
        // Expected: carry = (March 30K − 20K spent) = 10K. Nothing more.
        val priorBudgets = listOf(Budget("2026-03", 30_000.0))
        val txs = listOf(
            tx(monthKey = "2026-03", effect = BudgetEffect.SPEND, amount = 20_000.0),
        )
        val s = BudgetCalculator.snapshot(
            transactions = txs,
            budgets = priorBudgets,
            monthKey = "2026-05",
            currentMonthLimit = 30_000.0,
        )
        s.carryOverInr shouldBe (10_000.0 plusOrMinus 1e-9)
        s.effectiveBudgetInr shouldBe (40_000.0 plusOrMinus 1e-9)
    }

    @Test
    fun `snapshot currentMonthLimit wins over a matching row in budgets list`() {
        // Defence-in-depth: if a caller passes BOTH the explicit row
        // inside budgets AND a currentMonthLimit override, the override
        // should win (the caller has already resolved what they want).
        val budgets = listOf(Budget("2026-04", 30_000.0))
        val s = BudgetCalculator.snapshot(
            transactions = emptyList(),
            budgets = budgets,
            monthKey = "2026-04",
            currentMonthLimit = 50_000.0,
        )
        s.limitInr shouldBe 50_000.0
        s.effectiveBudgetInr shouldBe 50_000.0
    }

    // ---- medianPriorNetSpend (#15) ----

    @Test
    fun `medianPriorNetSpend returns the median of three prior months`() {
        val txs = listOf(
            tx(monthKey = "2026-03", amount = 10_000.0),
            tx(monthKey = "2026-04", amount = 20_000.0),
            tx(monthKey = "2026-05", amount = 30_000.0),
        )
        val r = BudgetCalculator.medianPriorNetSpend(txs, "2026-06")
        r!!.median shouldBe (20_000.0 plusOrMinus 1e-9)
        // Newest-first, so the chip can render the breakdown in order.
        r.months.map { it.monthKey } shouldBe listOf("2026-05", "2026-04", "2026-03")
    }

    @Test
    fun `medianPriorNetSpend with one qualifying month is that month`() {
        val txs = listOf(tx(monthKey = "2026-05", amount = 20_000.0))
        BudgetCalculator.medianPriorNetSpend(txs, "2026-06")!!
            .median shouldBe (20_000.0 plusOrMinus 1e-9)
    }

    @Test
    fun `medianPriorNetSpend averages the two middles for an even count`() {
        val txs = listOf(
            tx(monthKey = "2026-02", amount = 10_000.0),
            tx(monthKey = "2026-03", amount = 20_000.0),
            tx(monthKey = "2026-04", amount = 30_000.0),
            tx(monthKey = "2026-05", amount = 40_000.0),
        )
        // median of [10k,20k,30k,40k] = (20k + 30k) / 2.
        BudgetCalculator.medianPriorNetSpend(txs, "2026-06", priorMonths = 4)!!
            .median shouldBe (25_000.0 plusOrMinus 1e-9)
    }

    @Test
    fun `medianPriorNetSpend takes the most recent months, not the oldest`() {
        val txs = listOf(
            tx(monthKey = "2026-02", amount = 100_000.0), // outside the 3-window
            tx(monthKey = "2026-03", amount = 10_000.0),
            tx(monthKey = "2026-04", amount = 20_000.0),
            tx(monthKey = "2026-05", amount = 30_000.0),
        )
        val r = BudgetCalculator.medianPriorNetSpend(txs, "2026-06")
        r!!.median shouldBe (20_000.0 plusOrMinus 1e-9) // Feb's 100k must not count
        r.months.map { it.monthKey } shouldBe listOf("2026-05", "2026-04", "2026-03")
    }

    @Test
    fun `medianPriorNetSpend nets a month's refunds against its spend`() {
        val txs = listOf(
            tx(monthKey = "2026-05", effect = BudgetEffect.SPEND, amount = 30_000.0),
            tx(monthKey = "2026-05", effect = BudgetEffect.REFUND, amount = 5_000.0),
        )
        BudgetCalculator.medianPriorNetSpend(txs, "2026-06")!!
            .median shouldBe (25_000.0 plusOrMinus 1e-9)
    }

    @Test
    fun `medianPriorNetSpend skips refund-only months and reaches past them`() {
        val txs = listOf(
            tx(monthKey = "2026-04", effect = BudgetEffect.SPEND, amount = 20_000.0),
            tx(monthKey = "2026-05", effect = BudgetEffect.REFUND, amount = 5_000.0),
        )
        val r = BudgetCalculator.medianPriorNetSpend(txs, "2026-06")
        // 2026-05 has no SPEND → not qualifying; window reaches 2026-04.
        r!!.median shouldBe (20_000.0 plusOrMinus 1e-9)
        r.months.map { it.monthKey } shouldBe listOf("2026-04")
    }

    @Test
    fun `medianPriorNetSpend excludes the current and future months`() {
        val txs = listOf(
            tx(monthKey = "2026-06", amount = 99_000.0), // current — excluded
            tx(monthKey = "2026-07", amount = 88_000.0), // future — excluded
            tx(monthKey = "2026-05", amount = 20_000.0),
        )
        BudgetCalculator.medianPriorNetSpend(txs, "2026-06")!!
            .median shouldBe (20_000.0 plusOrMinus 1e-9)
    }

    @Test
    fun `medianPriorNetSpend excludes pending-FX rows from the net`() {
        val txs = listOf(
            tx(monthKey = "2026-05", effect = BudgetEffect.SPEND, amount = null),
            tx(monthKey = "2026-05", effect = BudgetEffect.SPEND, amount = 20_000.0),
        )
        BudgetCalculator.medianPriorNetSpend(txs, "2026-06")!!
            .median shouldBe (20_000.0 plusOrMinus 1e-9)
    }

    @Test
    fun `medianPriorNetSpend is null when no prior month has spend`() {
        BudgetCalculator.medianPriorNetSpend(emptyList(), "2026-06") shouldBe null
        // A refund-only history has no qualifying month either.
        val refundOnly = listOf(
            tx(monthKey = "2026-05", effect = BudgetEffect.REFUND, amount = 5_000.0),
        )
        BudgetCalculator.medianPriorNetSpend(refundOnly, "2026-06") shouldBe null
    }
}
