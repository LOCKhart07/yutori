package com.spendwise.transactions.internal

import com.spendwise.classifier.BudgetEffect
import com.spendwise.classifier.ClassificationOutcome
import com.spendwise.parser.Classification
import com.spendwise.transactions.IncomingEvent
import com.spendwise.transactions.TransactionRow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DedupMatcherTest {

    private val baseTime = 1_700_000_000_000L

    private fun outcome(
        classification: Classification = Classification.UPI_PAYMENT,
        effect: BudgetEffect = BudgetEffect.SPEND,
        amount: Double? = 100.0,
        currency: String = "INR",
        merchant: String? = "VVCMC Property Tax",
        merchantKey: String? = "vvcmc property tax",
        last4: String? = null,
    ) = ClassificationOutcome(
        finalClassification = classification,
        budgetEffect = effect,
        amount = amount,
        currency = currency,
        merchant = merchant,
        merchantKey = merchantKey,
        last4 = last4,
        accountId = null,
        category = null,
        classificationOriginal = null,
    )

    private fun event(
        smsLogId: Long = 1,
        outcome: ClassificationOutcome = outcome(),
        pattern: String = "icici_eazypay",
        occurredAtMs: Long = baseTime,
        monthKey: String = "2026-04",
    ) = IncomingEvent(smsLogId, outcome, pattern, occurredAtMs, monthKey)

    private fun row(
        id: Long = 1,
        classification: Classification = Classification.UPI_PAYMENT,
        effect: BudgetEffect = BudgetEffect.SPEND,
        inrAmount: Double? = 100.0,
        merchant: String? = "VVCMC",
        merchantKey: String? = "vvcmc",
        last4: String? = null,
        occurredAtMs: Long = baseTime,
    ) = TransactionRow(
        id = id,
        classification = classification,
        classificationOriginal = null,
        budgetEffect = effect,
        inrAmount = inrAmount,
        originalAmount = null,
        originalCurrency = "INR",
        merchant = merchant,
        merchantKey = merchantKey,
        category = null,
        accountId = null,
        last4 = last4,
        occurredAtMs = occurredAtMs,
        monthKey = "2026-04",
    )

    // ---- no match cases ----

    @Test
    fun `empty transactions list returns null`() {
        DedupMatcher.findCandidate(event(), emptyList()).shouldBeNull()
    }

    @Test
    fun `null amount on event returns null`() {
        val evt = event(outcome = outcome(amount = null))
        DedupMatcher.findCandidate(evt, listOf(row())).shouldBeNull()
    }

    @Test
    fun `forex event returns null - merged downstream after rate fetch`() {
        val evt = event(outcome = outcome(amount = 10.0, currency = "USD"))
        DedupMatcher.findCandidate(evt, listOf(row())).shouldBeNull()
    }

    @Test
    fun `existing row with null inrAmount (pending forex) is not a candidate`() {
        val rows = listOf(row(inrAmount = null))
        DedupMatcher.findCandidate(event(), rows).shouldBeNull()
    }

    // ---- amount tolerance ----

    @Test
    fun `exact amount match within token overlap candidates`() {
        val rows = listOf(row(inrAmount = 100.0))
        DedupMatcher.findCandidate(event(), rows).shouldNotBeNull()
    }

    @Test
    fun `amount within 0_5 INR tolerance matches`() {
        val rows = listOf(row(inrAmount = 100.49))
        DedupMatcher.findCandidate(event(), rows).shouldNotBeNull()
    }

    @Test
    fun `amount beyond 0_5 INR tolerance does not match`() {
        val rows = listOf(row(inrAmount = 2255.0))
        DedupMatcher.findCandidate(event(), rows).shouldBeNull()
    }

    // ---- time window ----

    @Test
    fun `occurredAt within 5 minutes matches`() {
        val rows = listOf(row(occurredAtMs = baseTime - 4 * 60_000))
        DedupMatcher.findCandidate(event(), rows).shouldNotBeNull()
    }

    @Test
    fun `occurredAt at exactly 5 minutes matches (inclusive)`() {
        val rows = listOf(row(occurredAtMs = baseTime - 5 * 60_000))
        DedupMatcher.findCandidate(event(), rows).shouldNotBeNull()
    }

    @Test
    fun `occurredAt beyond 5 minutes does not match`() {
        val rows = listOf(row(occurredAtMs = baseTime - 5 * 60_000 - 1))
        DedupMatcher.findCandidate(event(), rows).shouldBeNull()
    }

    // ---- effect must match ----

    @Test
    fun `SPEND event cannot merge into REFUND row`() {
        val rows = listOf(row(effect = BudgetEffect.REFUND))
        DedupMatcher.findCandidate(event(), rows).shouldBeNull()
    }

    @Test
    fun `SPEND event cannot merge into INCOME row`() {
        val rows = listOf(row(effect = BudgetEffect.INCOME))
        DedupMatcher.findCandidate(event(), rows).shouldBeNull()
    }

    @Test
    fun `REFUND event can merge into REFUND row`() {
        val evt = event(outcome = outcome(
            classification = Classification.REFUND,
            effect = BudgetEffect.REFUND,
        ))
        val rows = listOf(row(effect = BudgetEffect.REFUND))
        DedupMatcher.findCandidate(evt, rows).shouldNotBeNull()
    }

    @Test
    fun `DROP event is not mergeable`() {
        val evt = event(outcome = outcome(effect = BudgetEffect.DROP))
        val rows = listOf(row(effect = BudgetEffect.DROP))
        DedupMatcher.findCandidate(evt, rows).shouldBeNull()
    }

    // ---- last4 vs merchant-token match ----

    @Test
    fun `matching last4 alone is sufficient even without merchant overlap`() {
        val evt = event(outcome = outcome(
            merchant = null, merchantKey = null, last4 = "0000",
        ))
        val rows = listOf(row(merchant = null, merchantKey = null, last4 = "0000"))
        DedupMatcher.findCandidate(evt, rows).shouldNotBeNull()
    }

    @Test
    fun `merchant token overlap alone is sufficient without last4`() {
        val evt = event(outcome = outcome(
            last4 = null, merchantKey = "vvcmc online property tax",
        ))
        val rows = listOf(row(last4 = null, merchantKey = "vvcmc"))
        DedupMatcher.findCandidate(evt, rows).shouldNotBeNull()
    }

    @Test
    fun `no last4 match and no token overlap does not match`() {
        val evt = event(outcome = outcome(
            merchantKey = "completely different", last4 = "1234",
        ))
        val rows = listOf(row(merchantKey = "vvcmc", last4 = "5678"))
        DedupMatcher.findCandidate(evt, rows).shouldBeNull()
    }

    @Test
    fun `single-character tokens do not count as overlap`() {
        // If both merchants contained "a" we don't want them merging.
        val evt = event(outcome = outcome(merchantKey = "a x"))
        val rows = listOf(row(merchantKey = "a y"))
        DedupMatcher.findCandidate(evt, rows).shouldBeNull()
    }

    // ---- disambiguation ----

    @Test
    fun `same last4 preferred over merchant-token-only match when both match`() {
        // Event and both candidates overlap on "vvcmc"; one candidate also
        // shares the last4. Disambiguation picks the same-last4 match.
        val evt = event(outcome = outcome(
            last4 = "0000", merchantKey = "vvcmc property tax",
        ))
        val rowWithLast4 = row(id = 1, last4 = "0000", merchantKey = "vvcmc receipt")
        val rowWithToken = row(id = 2, last4 = null, merchantKey = "vvcmc bill")
        val picked = DedupMatcher.findCandidate(evt, listOf(rowWithToken, rowWithLast4))
        picked?.id shouldBe 1
    }

    // ---- §12.5 over-merge regression ----

    @Test
    fun `distinct UPI payments same account same amount different VPAs do NOT merge`() {
        // The scenario observed on-device 2026-04-15: two ₹999 payments
        // from Kotak X0000 to DIFFERENT recipients within the 5-min
        // window. Pre-refinement these collapsed because `last4` alone
        // satisfied rule 4. Refined: both sides have merchants that
        // don't token-overlap, so they stay distinct.
        val evt = event(outcome = outcome(
            merchantKey = "freshtest@okaxis",
            last4 = "0000",
            amount = 999.0,
        ))
        val rows = listOf(row(
            merchantKey = "issuertest@okaxis",
            last4 = "0000",
            inrAmount = 999.0,
            occurredAtMs = baseTime - 30_000,
        ))
        DedupMatcher.findCandidate(evt, rows).shouldBeNull()
    }

    @Test
    fun `§12_3 bank-side merchant + gateway merchant still merge on token overlap`() {
        // Preserves the original §12.3 property-tax merge: gateway has
        // a verbose merchant, bank has a VPA merchant, both contain the
        // "vvcmc" token.
        val evt = event(outcome = outcome(
            merchantKey = "vvcmc online property tax account",
            last4 = null,     // gateway (eazypay) doesn't report last4
            amount = 100.0,
        ))
        val rows = listOf(row(
            merchantKey = "vvcmc bill@okicici",
            last4 = "0000",
            inrAmount = 100.0,
        ))
        DedupMatcher.findCandidate(evt, rows).shouldNotBeNull()
    }

    @Test
    fun `last4 match with one side merchant-null still merges`() {
        // Preserves the §12.3 case where a bank-debit SMS carries a
        // merchant but a CC-payment-receipt SMS does not. Both share
        // the source-account last4 so we merge on that alone.
        val evt = event(outcome = outcome(
            merchantKey = "vpa@bank",
            last4 = "2222",
        ))
        val rows = listOf(row(
            merchantKey = null,
            last4 = "2222",
        ))
        DedupMatcher.findCandidate(evt, rows).shouldNotBeNull()
    }

    @Test
    fun `earliest occurredAt wins when multiple candidates still tie`() {
        // Both match by token only; pick the earlier one.
        val early = row(id = 10, merchantKey = "vvcmc", occurredAtMs = baseTime - 60_000)
        val late = row(id = 11, merchantKey = "vvcmc", occurredAtMs = baseTime)
        val picked = DedupMatcher.findCandidate(event(), listOf(late, early))
        picked?.id shouldBe 10
    }
}
