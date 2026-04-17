package com.yutori.transactions.forex

import com.yutori.classifier.BudgetEffect
import com.yutori.parser.Classification
import com.yutori.transactions.TransactionRow
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ForexConverterTest {

    private fun pending(
        currency: String = "USD",
        originalAmount: Double? = 10.00,
    ) = TransactionRow(
        id = 1,
        classification = Classification.CC_TRANSACTION,
        classificationOriginal = null,
        budgetEffect = BudgetEffect.SPEND,
        inrAmount = null,
        originalAmount = originalAmount,
        originalCurrency = currency,
        rateSource = "pending",
        merchant = "GITHUB, INC",
        merchantKey = "github inc",
        category = null,
        accountId = null,
        last4 = "1111",
        occurredAtMs = 1_700_000_000_000L,
        monthKey = "2026-04",
    )

    @Test
    fun `apply fills in inrAmount, exchangeRate, and rateSource`() {
        val row = pending()
        val converted = ForexConverter.apply(row, 83.5, "exchangerate-api.com")
        converted.inrAmount!! shouldBe (10.00 * 83.5 plusOrMinus 1e-9)
        converted.exchangeRate shouldBe 83.5
        converted.rateSource shouldBe "exchangerate-api.com"
        converted.originalAmount shouldBe 10.00
        converted.originalCurrency shouldBe "USD"
    }

    @Test
    fun `apply preserves other fields unchanged`() {
        val row = pending()
        val converted = ForexConverter.apply(row, 83.5, "exchangerate-api.com")
        converted.id shouldBe row.id
        converted.occurredAtMs shouldBe row.occurredAtMs
        converted.classification shouldBe row.classification
        converted.budgetEffect shouldBe row.budgetEffect
        converted.merchant shouldBe row.merchant
        converted.last4 shouldBe row.last4
        converted.monthKey shouldBe row.monthKey
    }

    @Test
    fun `apply rejects INR row with IllegalArgument`() {
        val row = pending(currency = "INR")
        assertThrows<IllegalArgumentException> {
            ForexConverter.apply(row, 83.5, "exchangerate-api.com")
        }
    }

    @Test
    fun `apply rejects null originalAmount with IllegalArgument`() {
        val row = pending(originalAmount = null)
        assertThrows<IllegalArgumentException> {
            ForexConverter.apply(row, 83.5, "exchangerate-api.com")
        }
    }

    @Test
    fun `apply rejects non-positive rate`() {
        val row = pending()
        assertThrows<IllegalArgumentException> { ForexConverter.apply(row, 0.0, "src") }
        assertThrows<IllegalArgumentException> { ForexConverter.apply(row, -1.0, "src") }
    }

    @Test
    fun `apply rejects NaN and infinite rates`() {
        val row = pending()
        assertThrows<IllegalArgumentException> { ForexConverter.apply(row, Double.NaN, "src") }
        assertThrows<IllegalArgumentException> { ForexConverter.apply(row, Double.POSITIVE_INFINITY, "src") }
    }

    @Test
    fun `apply with manual rateSource records manual provenance`() {
        val row = pending()
        val converted = ForexConverter.apply(row, 83.5, "manual")
        converted.rateSource shouldBe "manual"
    }

    // ---- isPending ----

    @Test
    fun `isPending returns true for INR-null rateSource=pending`() {
        ForexConverter.isPending(pending()) shouldBe true
    }

    @Test
    fun `isPending returns false for resolved forex`() {
        val resolved = pending().copy(
            inrAmount = 1000.0,
            exchangeRate = 83.5,
            rateSource = "exchangerate-api.com",
        )
        ForexConverter.isPending(resolved) shouldBe false
    }

    @Test
    fun `isPending returns false for INR transactions`() {
        val inr = pending().copy(
            originalCurrency = "INR",
            originalAmount = null,
            inrAmount = 500.0,
            rateSource = null,
        )
        ForexConverter.isPending(inr) shouldBe false
    }
}
