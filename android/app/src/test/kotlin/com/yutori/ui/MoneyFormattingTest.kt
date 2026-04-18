package com.yutori.ui

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.text.NumberFormat
import java.util.Locale
import org.junit.jupiter.api.Test

/**
 * Dashboard / drill-down surfaces must not mix `.00` and `.83` in the
 * same column — see issue #23. `compact = true` rounds to a whole
 * rupee so the hero row, category rows, card strip, and tx list all
 * scan cleanly. `compact = false` keeps two-decimal precision for
 * TransactionDetail where the exact amount matters.
 */
class MoneyFormattingTest {

    private val inr: NumberFormat = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())

    @Test
    fun `compact strips decimals on whole-rupee values`() {
        inr.formatAmount(21386.0, compact = true) shouldBe "₹21,386"
    }

    @Test
    fun `compact rounds fractional values to nearest rupee`() {
        inr.formatAmount(9255.83, compact = true) shouldBe "₹9,256"
        inr.formatAmount(288.33, compact = true) shouldBe "₹288"
        inr.formatAmount(1155.49, compact = true) shouldBe "₹1,155"
        // Half-way values use banker's rounding (kotlin.math.round →
        // half-to-even). 1155.50 → 1156 (even), 12345678.50 → 12345678
        // (even). This is fine for display — sub-rupee drift at the
        // boundary is invisible to the user.
        inr.formatAmount(1155.50, compact = true) shouldBe "₹1,156"
    }

    @Test
    fun `compact output never contains decimals`() {
        // Spot-check the column-scan invariant from issue #23.
        val values = listOf(21386.0, 9255.83, 1155.0, 288.33, 0.01, 999999.99)
        values.forEach { v ->
            inr.formatAmount(v, compact = true) shouldNotContain "."
        }
    }

    @Test
    fun `non-compact keeps two decimals on whole values`() {
        inr.formatAmount(21386.0) shouldBe "₹21,386.00"
    }

    @Test
    fun `non-compact keeps two decimals on fractional values`() {
        inr.formatAmount(9255.83) shouldBe "₹9,255.83"
        inr.formatAmount(288.33) shouldBe "₹288.33"
    }

    @Test
    fun `default argument is compact false`() {
        // Calling without the flag must behave as compact = false.
        inr.formatAmount(9255.83) shouldBe inr.formatAmount(9255.83, compact = false)
        inr.formatAmount(21386.0) shouldBe "₹21,386.00"
    }

    @Test
    fun `zero formats consistently in both modes`() {
        inr.formatAmount(0.0, compact = true) shouldBe "₹0"
        inr.formatAmount(0.0, compact = false) shouldBe "₹0.00"
    }

    @Test
    fun `negative values format with sign`() {
        // Locale-dependent currency sign placement; assert the digits
        // are present and the decimal rule is honoured.
        val compactNeg = inr.formatAmount(-1234.56, compact = true)
        compactNeg shouldNotContain "."
        compactNeg.filter { it.isDigit() } shouldBe "1235"

        val nonCompactNeg = inr.formatAmount(-1234.56, compact = false)
        nonCompactNeg.filter { it.isDigit() } shouldBe "123456"
    }

    @Test
    fun `very large values round and strip decimals in compact mode`() {
        // Grouping separators are locale/JDK-dependent — we only assert
        // the digit sequence and the absence of a decimal tail.
        val compact = inr.formatAmount(12345678.49, compact = true)
        compact shouldNotContain "."
        compact.filter { it.isDigit() } shouldBe "12345678"

        // 12345678.50 → 12345678 under banker's rounding (even).
        val compactHalf = inr.formatAmount(12345678.50, compact = true)
        compactHalf shouldNotContain "."
        compactHalf.filter { it.isDigit() } shouldBe "12345678"

        val compactOddHalf = inr.formatAmount(12345679.50, compact = true)
        compactOddHalf.filter { it.isDigit() } shouldBe "12345680"

        val nonCompact = inr.formatAmount(12345678.0, compact = false)
        nonCompact.filter { it.isDigit() } shouldBe "1234567800"
    }
}
