package com.spendwise.ui

import com.spendwise.database.entities.TransactionEntity
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * `applyTxSort` is pure — no mocks. Tests are written to fail if the
 * function is replaced with `return txs` or `return emptyList()`.
 *
 * Regression gate for issue #5: pending-forex txs (null `inrAmount`)
 * must NOT sort as if they were 0. They go to the end of both Amount
 * orderings.
 */
class CategoryDrillDownSortTest {

    private fun tx(
        id: Long,
        inr: Double?,
        occurredAtMs: Long = 0L,
        budgetEffect: String = "SPEND",
    ): TransactionEntity = TransactionEntity(
        id = id,
        classification = "UPI_PAYMENT",
        budgetEffect = budgetEffect,
        inrAmount = inr,
        originalAmount = inr,
        originalCurrency = "INR",
        merchant = null,
        merchantKey = null,
        category = null,
        accountId = null,
        last4 = null,
        occurredAtMs = occurredAtMs,
        monthKey = "2026-04",
    )

    @Test
    fun `AmountDesc -- largest first, pending-forex nulls at end`() {
        val small = tx(id = 1, inr = 100.0)
        val large = tx(id = 2, inr = 9_000.0)
        val mid = tx(id = 3, inr = 500.0)
        val pending = tx(id = 4, inr = null, occurredAtMs = 10)

        val sorted = applyTxSort(listOf(small, large, mid, pending), TxSort.AmountDesc)

        sorted.map { it.id } shouldBe listOf(2L, 3L, 1L, 4L)
    }

    @Test
    fun `AmountAsc -- smallest first, pending-forex nulls still at end (not top)`() {
        val small = tx(id = 1, inr = 100.0)
        val large = tx(id = 2, inr = 9_000.0)
        val mid = tx(id = 3, inr = 500.0)
        val pending = tx(id = 4, inr = null, occurredAtMs = 10)

        val sorted = applyTxSort(listOf(small, large, mid, pending), TxSort.AmountAsc)

        // If the old `?: 0.0` code were still in place, `pending` would lead.
        sorted.map { it.id } shouldBe listOf(1L, 3L, 2L, 4L)
    }

    @Test
    fun `AmountDesc -- multiple pending nulls ordered by occurredAtMs DESC`() {
        val known = tx(id = 1, inr = 500.0)
        val pendingOld = tx(id = 2, inr = null, occurredAtMs = 1_000L)
        val pendingNew = tx(id = 3, inr = null, occurredAtMs = 2_000L)

        val sorted = applyTxSort(listOf(known, pendingOld, pendingNew), TxSort.AmountDesc)

        sorted.map { it.id } shouldBe listOf(1L, 3L, 2L)
    }

    @Test
    fun `DateDesc -- newest occurredAtMs first`() {
        val oldest = tx(id = 1, inr = 100.0, occurredAtMs = 1_000L)
        val newest = tx(id = 2, inr = 100.0, occurredAtMs = 3_000L)
        val middle = tx(id = 3, inr = 100.0, occurredAtMs = 2_000L)

        val sorted = applyTxSort(listOf(oldest, newest, middle), TxSort.DateDesc)

        sorted.map { it.id } shouldBe listOf(2L, 3L, 1L)
    }

    @Test
    fun `DateAsc -- oldest occurredAtMs first`() {
        val oldest = tx(id = 1, inr = 100.0, occurredAtMs = 1_000L)
        val newest = tx(id = 2, inr = 100.0, occurredAtMs = 3_000L)
        val middle = tx(id = 3, inr = 100.0, occurredAtMs = 2_000L)

        val sorted = applyTxSort(listOf(oldest, newest, middle), TxSort.DateAsc)

        sorted.map { it.id } shouldBe listOf(1L, 3L, 2L)
    }

    @Test
    fun `SPEND and REFUND with same magnitude sort by magnitude (refund sign is not an issue)`() {
        // Both store positive amounts; budgetEffect distinguishes. The
        // sort should treat them uniformly by magnitude.
        val spend500 = tx(id = 1, inr = 500.0, budgetEffect = "SPEND")
        val refund500 = tx(id = 2, inr = 500.0, budgetEffect = "REFUND")
        val spend100 = tx(id = 3, inr = 100.0, budgetEffect = "SPEND")
        val refund900 = tx(id = 4, inr = 900.0, budgetEffect = "REFUND")

        val desc = applyTxSort(listOf(spend500, refund500, spend100, refund900), TxSort.AmountDesc)

        // 900, then the two 500s (stable), then 100. Neither 500 should
        // be pushed around because it's a refund.
        desc.map { it.inrAmount } shouldBe listOf(900.0, 500.0, 500.0, 100.0)
        desc.first().id shouldBe 4L
        desc.last().id shouldBe 3L
    }

    @Test
    fun `TxSort next cycles AmountDesc - AmountAsc - DateDesc - DateAsc - AmountDesc`() {
        val start = TxSort.AmountDesc
        val s1 = start.next()
        val s2 = s1.next()
        val s3 = s2.next()
        val s4 = s3.next()

        s1 shouldBe TxSort.AmountAsc
        s2 shouldBe TxSort.DateDesc
        s3 shouldBe TxSort.DateAsc
        s4 shouldBe TxSort.AmountDesc
    }

    @Test
    fun `empty input returns empty for all sorts`() {
        val empty = emptyList<TransactionEntity>()
        applyTxSort(empty, TxSort.AmountDesc) shouldBe empty
        applyTxSort(empty, TxSort.AmountAsc) shouldBe empty
        applyTxSort(empty, TxSort.DateDesc) shouldBe empty
        applyTxSort(empty, TxSort.DateAsc) shouldBe empty
    }

    @Test
    fun `all-pending list keeps occurredAtMs DESC order under both Amount sorts`() {
        val a = tx(id = 1, inr = null, occurredAtMs = 1_000L)
        val b = tx(id = 2, inr = null, occurredAtMs = 2_000L)
        val c = tx(id = 3, inr = null, occurredAtMs = 3_000L)

        applyTxSort(listOf(a, b, c), TxSort.AmountDesc).map { it.id } shouldBe
            listOf(3L, 2L, 1L)
        applyTxSort(listOf(a, b, c), TxSort.AmountAsc).map { it.id } shouldBe
            listOf(3L, 2L, 1L)
    }
}
