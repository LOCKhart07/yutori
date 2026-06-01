package com.yutori.ui

import com.yutori.budget.MonthNet
import com.yutori.budget.SpendSuggestion
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The visible-decision logic behind the #15 suggestion chip is extracted
 * into pure functions (the composable itself is just rendering). These
 * cover when the chip shows, the lead-in wording, and the caption.
 */
class BudgetSuggestionTest {

    private fun suggestion(median: Double, vararg months: Pair<String, Double>) =
        SpendSuggestion(
            median = median,
            months = months.map { MonthNet(it.first, it.second) },
        )

    // ---- shouldShowSuggestion ----

    @Test
    fun `hidden when there is no suggestion`() {
        shouldShowSuggestion(null, currentLimitText = "", dismissed = false) shouldBe false
    }

    @Test
    fun `hidden once dismissed`() {
        val s = suggestion(42_300.0, "2026-05" to 42_300.0)
        shouldShowSuggestion(s, currentLimitText = "", dismissed = true) shouldBe false
    }

    @Test
    fun `hidden when the field already equals the suggestion`() {
        val s = suggestion(42_300.0, "2026-05" to 42_300.0)
        // No point offering a number the field already holds.
        shouldShowSuggestion(s, currentLimitText = "42300", dismissed = false) shouldBe false
        // ...and whitespace around the field value is tolerated.
        shouldShowSuggestion(s, currentLimitText = " 42300 ", dismissed = false) shouldBe false
    }

    @Test
    fun `shown when the field differs from the suggestion`() {
        val s = suggestion(42_300.0, "2026-05" to 42_300.0)
        // Empty field (first budget) and a different pre-fill both show it.
        shouldShowSuggestion(s, currentLimitText = "", dismissed = false) shouldBe true
        shouldShowSuggestion(s, currentLimitText = "45000", dismissed = false) shouldBe true
    }

    // ---- suggestionLeadIn ----

    @Test
    fun `lead-in is Suggested for an empty field and Or use when pre-filled`() {
        suggestionLeadIn(hasPrefill = false) shouldBe "Suggested"
        suggestionLeadIn(hasPrefill = true) shouldBe "Or use"
    }

    // ---- suggestionBasisText ----

    @Test
    fun `basis text lists months chronologically with k-rounded amounts`() {
        // Held newest-first; the caption must read oldest-to-newest.
        val s = suggestion(
            42_300.0,
            "2026-05" to 42_300.0,
            "2026-04" to 45_000.0,
            "2026-03" to 40_000.0,
        )
        suggestionBasisText(s) shouldBe
            "median of your last 3 months · Mar 40k · Apr 45k · May 42k"
    }

    @Test
    fun `basis text uses the singular for a single month`() {
        val s = suggestion(20_000.0, "2026-05" to 20_000.0)
        suggestionBasisText(s) shouldBe "median of your last 1 month · May 20k"
    }
}
