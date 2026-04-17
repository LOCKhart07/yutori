package com.yutori.transactions

import com.yutori.classifier.BudgetEffect
import com.yutori.classifier.ClassificationOutcome
import com.yutori.parser.Category
import com.yutori.parser.Classification
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong

class TransactionBuilderTest {

    private val baseTime = 1_700_000_000_000L
    private val idSeq = AtomicLong(1000)
    private val idAlloc: () -> Long = { idSeq.incrementAndGet() }

    private fun outcome(
        classification: Classification = Classification.UPI_PAYMENT,
        effect: BudgetEffect = BudgetEffect.SPEND,
        amount: Double? = 100.0,
        currency: String = "INR",
        merchant: String? = "VVCMC ONLINE PROPERTY TAX ACCOUNT",
        merchantKey: String? = "vvcmc online property tax account",
        last4: String? = null,
        category: Category? = null,
        classificationOriginal: Classification? = null,
        accountId: Long? = null,
    ) = ClassificationOutcome(
        finalClassification = classification,
        budgetEffect = effect,
        amount = amount,
        currency = currency,
        merchant = merchant,
        merchantKey = merchantKey,
        last4 = last4,
        accountId = accountId,
        category = category,
        classificationOriginal = classificationOriginal,
    )

    private fun event(
        smsLogId: Long,
        outcome: ClassificationOutcome = outcome(),
        pattern: String = "icici_eazypay",
        occurredAtMs: Long = baseTime,
    ) = IncomingEvent(smsLogId, outcome, pattern, occurredAtMs, "2026-04")

    // ---- DROP path ----

    @Test
    fun `DROP event does not create a transaction`() {
        val dropEvent = event(
            smsLogId = 1,
            outcome = outcome(
                classification = Classification.CC_BILL_PAYMENT,
                effect = BudgetEffect.DROP,
            ),
        )
        val result = TransactionBuilder.apply(dropEvent, emptyList(), emptyList(), idAlloc)

        result.transactions shouldBe emptyList()
        result.sources shouldBe emptyList()
        (result.decision is MergeDecision.Dropped) shouldBe true
    }

    @Test
    fun `UNMATCHED event is Dropped`() {
        val unmatched = event(
            smsLogId = 1,
            outcome = outcome(
                classification = Classification.UNMATCHED,
                effect = BudgetEffect.DROP,
                amount = null,
                merchant = null,
                merchantKey = null,
            ),
            pattern = "UNMATCHED",
        )
        val result = TransactionBuilder.apply(unmatched, emptyList(), emptyList(), idAlloc)
        (result.decision is MergeDecision.Dropped) shouldBe true
        result.transactions shouldBe emptyList()
    }

    // ---- CreatedNew path ----

    @Test
    fun `first SPEND event creates a new transaction with primary source`() {
        val evt = event(smsLogId = 42)
        val result = TransactionBuilder.apply(evt, emptyList(), emptyList(), idAlloc)

        val decision = result.decision
        (decision is MergeDecision.CreatedNew) shouldBe true
        decision as MergeDecision.CreatedNew

        result.transactions.size shouldBe 1
        val row = result.transactions.single()
        row.id shouldBe decision.transactionId
        row.classification shouldBe Classification.UPI_PAYMENT
        row.budgetEffect shouldBe BudgetEffect.SPEND
        row.inrAmount shouldBe 100.0
        row.originalAmount.shouldBeNull()
        row.originalCurrency shouldBe "INR"
        row.rateSource.shouldBeNull()

        result.sources.size shouldBe 1
        val src = result.sources.single()
        src.smsLogId shouldBe 42
        src.role shouldBe SourceRole.GATEWAY
        src.isPrimary shouldBe true
    }

    @Test
    fun `forex event creates pending-rate transaction`() {
        val forex = event(
            smsLogId = 7,
            outcome = outcome(
                classification = Classification.CC_TRANSACTION,
                effect = BudgetEffect.SPEND,
                amount = 10.00,
                currency = "USD",
                merchant = "GITHUB, INC",
                merchantKey = "github, inc",
                last4 = "1111",
            ),
            pattern = "axis_cc_spend",
        )
        val result = TransactionBuilder.apply(forex, emptyList(), emptyList(), idAlloc)

        val row = result.transactions.single()
        row.inrAmount.shouldBeNull()
        row.originalAmount shouldBe 10.00
        row.originalCurrency shouldBe "USD"
        row.rateSource shouldBe "pending"
    }

    // ---- §12.3 multi-party merge ----

    @Test
    fun `bank debit arriving after gateway promotes to primary and upgrades merchant`() {
        // §12.3 property-tax scenario. Sequence: gateway fires first
        // (ICICI eazypay) with merchant "VVCMC ONLINE PROPERTY TAX
        // ACCOUNT"; then a synthetic bank debit fires 2 min later with
        // a shorter merchant. Per §4.3: bank wins primary but the
        // merchant should NOT regress to a shorter string.

        val gateway = event(
            smsLogId = 100,
            outcome = outcome(
                merchant = "VVCMC ONLINE PROPERTY TAX ACCOUNT",
                merchantKey = "vvcmc online property tax account",
            ),
            pattern = "icici_eazypay",
            occurredAtMs = baseTime,
        )
        val bank = event(
            smsLogId = 101,
            outcome = outcome(
                merchant = "VVCMC",
                merchantKey = "vvcmc",
                last4 = "0000",
            ),
            pattern = "kotak_upi_debit",
            occurredAtMs = baseTime + 2 * 60_000,
        )

        // Apply gateway first.
        val step1 = TransactionBuilder.apply(gateway, emptyList(), emptyList(), idAlloc)
        // Apply bank on the resulting state.
        val step2 = TransactionBuilder.apply(bank, step1.transactions, step1.sources, idAlloc)

        // Same transaction id — merged, not new.
        step2.transactions.size shouldBe 1
        step1.transactions.single().id shouldBe step2.transactions.single().id

        val decision = step2.decision
        (decision is MergeDecision.MergedInto) shouldBe true
        (decision as MergeDecision.MergedInto).primaryPromoted shouldBe true

        // Two sources now: the ICICI (was primary) and the Kotak (new primary).
        step2.sources.size shouldBe 2
        val primary = step2.sources.single { it.isPrimary }
        primary.smsLogId shouldBe 101
        primary.role shouldBe SourceRole.BANK_DEBIT

        val secondary = step2.sources.single { !it.isPrimary }
        secondary.smsLogId shouldBe 100
        secondary.role shouldBe SourceRole.GATEWAY

        // Merchant was NOT regressed to the shorter "VVCMC".
        step2.transactions.single().merchant shouldBe "VVCMC ONLINE PROPERTY TAX ACCOUNT"
    }

    @Test
    fun `second source with lower-priority role does not promote primary`() {
        val bank = event(
            smsLogId = 200,
            outcome = outcome(last4 = "0000"),
            pattern = "kotak_upi_debit",
            occurredAtMs = baseTime,
        )
        val step1 = TransactionBuilder.apply(bank, emptyList(), emptyList(), idAlloc)

        val gateway = event(
            smsLogId = 201,
            outcome = outcome(last4 = "0000"),  // same amount + last4
            pattern = "icici_eazypay",
            occurredAtMs = baseTime + 60_000,
        )
        val step2 = TransactionBuilder.apply(gateway, step1.transactions, step1.sources, idAlloc)

        val decision = step2.decision
        (decision is MergeDecision.MergedInto) shouldBe true
        (decision as MergeDecision.MergedInto).primaryPromoted shouldBe false

        val primary = step2.sources.single { it.isPrimary }
        primary.smsLogId shouldBe 200
        primary.role shouldBe SourceRole.BANK_DEBIT

        val secondary = step2.sources.single { !it.isPrimary }
        secondary.smsLogId shouldBe 201
        secondary.role shouldBe SourceRole.GATEWAY
    }

    @Test
    fun `three-party sequence yields one transaction with three sources`() {
        val bank = event(
            smsLogId = 1,
            outcome = outcome(last4 = "0000", merchantKey = "vvcmc"),
            pattern = "kotak_upi_debit",
            occurredAtMs = baseTime,
        )
        val gateway = event(
            smsLogId = 2,
            outcome = outcome(last4 = "0000", merchantKey = "vvcmc property tax"),
            pattern = "icici_eazypay",
            occurredAtMs = baseTime + 30_000,
        )
        val ack = event(
            smsLogId = 3,
            outcome = outcome(last4 = null, merchantKey = "vvcmc"),
            pattern = "unknown_merchant_ack",
            occurredAtMs = baseTime + 2 * 60_000,
        )

        var s = TransactionBuilder.apply(bank, emptyList(), emptyList(), idAlloc)
        s = TransactionBuilder.apply(gateway, s.transactions, s.sources, idAlloc)
        s = TransactionBuilder.apply(ack, s.transactions, s.sources, idAlloc)

        s.transactions.size shouldBe 1
        s.sources.size shouldBe 3

        val primary = s.sources.single { it.isPrimary }
        primary.smsLogId shouldBe 1   // BANK_DEBIT wins

        s.sources.map { it.smsLogId } shouldContainExactlyInAnyOrder listOf(1L, 2L, 3L)
    }

    // ---- independent transactions ----

    @Test
    fun `two different amounts do not merge`() {
        val a = event(smsLogId = 1, outcome = outcome(amount = 100.0))
        val b = event(
            smsLogId = 2,
            outcome = outcome(amount = 500.0, merchant = "Other Merchant", merchantKey = "other merchant"),
            occurredAtMs = baseTime + 60_000,
        )

        var s = TransactionBuilder.apply(a, emptyList(), emptyList(), idAlloc)
        s = TransactionBuilder.apply(b, s.transactions, s.sources, idAlloc)

        s.transactions.size shouldBe 2
        s.sources.size shouldBe 2
        s.sources.all { it.isPrimary } shouldBe true
    }

    @Test
    fun `same amount beyond time window does not merge`() {
        val a = event(smsLogId = 1)
        val b = event(smsLogId = 2, occurredAtMs = baseTime + 10 * 60_000)

        var s = TransactionBuilder.apply(a, emptyList(), emptyList(), idAlloc)
        s = TransactionBuilder.apply(b, s.transactions, s.sources, idAlloc)

        s.transactions.size shouldBe 2
    }

    @Test
    fun `SPEND and REFUND of same amount in same window do not merge`() {
        val a = event(
            smsLogId = 1,
            outcome = outcome(
                classification = Classification.UPI_PAYMENT,
                effect = BudgetEffect.SPEND,
                amount = 500.0,
            ),
        )
        val b = event(
            smsLogId = 2,
            outcome = outcome(
                classification = Classification.REFUND,
                effect = BudgetEffect.REFUND,
                amount = 500.0,
            ),
            pattern = "blinkit_refund",
            occurredAtMs = baseTime + 60_000,
        )

        var s = TransactionBuilder.apply(a, emptyList(), emptyList(), idAlloc)
        s = TransactionBuilder.apply(b, s.transactions, s.sources, idAlloc)

        s.transactions.size shouldBe 2
        s.transactions.map { it.budgetEffect } shouldContainExactlyInAnyOrder
            listOf(BudgetEffect.SPEND, BudgetEffect.REFUND)
    }

    // ---- classification_original propagates ----

    @Test
    fun `self-transfer reclassification preserves classification_original on row`() {
        val selfTransfer = event(
            smsLogId = 50,
            outcome = outcome(
                classification = Classification.SELF_TRANSFER,
                effect = BudgetEffect.DROP,
                classificationOriginal = Classification.UPI_PAYMENT,
            ),
        )
        // SELF_TRANSFER is DROP → no transaction row. The classifier
        // already handled the reclassification upstream; we just drop.
        val result = TransactionBuilder.apply(selfTransfer, emptyList(), emptyList(), idAlloc)
        (result.decision is MergeDecision.Dropped) shouldBe true
    }

    @Test
    fun `user-added middleman rule reclassifies UPI to CC_BILL_PAYMENT - drops`() {
        // If a user registers a middleman rule, classifier emits
        // CC_BILL_PAYMENT with classificationOriginal=UPI_PAYMENT.
        // Builder drops it (CC_BILL_PAYMENT → DROP). No row created.
        val evt = event(
            smsLogId = 60,
            outcome = outcome(
                classification = Classification.CC_BILL_PAYMENT,
                effect = BudgetEffect.DROP,
                classificationOriginal = Classification.UPI_PAYMENT,
            ),
            pattern = "kotak_upi_debit",
        )
        val result = TransactionBuilder.apply(evt, emptyList(), emptyList(), idAlloc)
        (result.decision is MergeDecision.Dropped) shouldBe true
    }

    @Test
    fun `spend event with reclassification preserves classification_original`() {
        // Synthetic case: parser said UPI_PAYMENT, user rule reclassified
        // to REFUND (odd but possible for test). Row should retain the
        // classification_original audit field.
        val evt = event(
            smsLogId = 70,
            outcome = outcome(
                classification = Classification.REFUND,
                effect = BudgetEffect.REFUND,
                classificationOriginal = Classification.UPI_PAYMENT,
            ),
        )
        val result = TransactionBuilder.apply(evt, emptyList(), emptyList(), idAlloc)
        val row = result.transactions.single()
        row.classification shouldBe Classification.REFUND
        row.classificationOriginal shouldBe Classification.UPI_PAYMENT
        row.budgetEffect shouldBe BudgetEffect.REFUND
    }

    // ---- invariants ----

    @Test
    fun `after merge exactly one source is primary`() {
        val first = event(smsLogId = 1)
        val second = event(
            smsLogId = 2,
            pattern = "kotak_upi_debit",
            occurredAtMs = baseTime + 30_000,
        )
        var s = TransactionBuilder.apply(first, emptyList(), emptyList(), idAlloc)
        s = TransactionBuilder.apply(second, s.transactions, s.sources, idAlloc)

        s.sources.count { it.isPrimary } shouldBe 1
    }

    @Test
    fun `decision CreatedNew transactionId matches the inserted row id`() {
        val evt = event(smsLogId = 1)
        val result = TransactionBuilder.apply(evt, emptyList(), emptyList(), idAlloc)
        val decision = result.decision as MergeDecision.CreatedNew
        result.transactions.single().id shouldBe decision.transactionId
    }
}
