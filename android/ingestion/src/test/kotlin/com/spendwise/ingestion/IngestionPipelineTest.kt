package com.spendwise.ingestion

import com.spendwise.classifier.BudgetEffect
import com.spendwise.database.entities.RecipientRuleEntity
import com.spendwise.parser.Classification
import com.spendwise.transactions.MergeDecision
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneId

class IngestionPipelineTest {

    private lateinit var smsLog: FakeSmsLogDao
    private lateinit var transactions: FakeTransactionDao
    private lateinit var sources: FakeTransactionSourceDao
    private lateinit var accounts: FakeAccountDao
    private lateinit var rules: FakeRecipientRuleDao
    private lateinit var budgets: FakeBudgetDao
    private lateinit var alertState: FakeBudgetAlertStateDao
    private lateinit var pipeline: IngestionPipeline

    @BeforeEach
    fun setUp() {
        smsLog = FakeSmsLogDao()
        transactions = FakeTransactionDao()
        sources = FakeTransactionSourceDao()
        accounts = FakeAccountDao()
        rules = FakeRecipientRuleDao()
        budgets = FakeBudgetDao()
        alertState = FakeBudgetAlertStateDao()
        pipeline = IngestionPipeline(
            smsLogDao = smsLog,
            transactionDao = transactions,
            transactionSourceDao = sources,
            accountDao = accounts,
            recipientRuleDao = rules,
            budgetDao = budgets,
            budgetAlertStateDao = alertState,
            zone = ZoneId.of("Asia/Kolkata"),
            nowMs = { 1_774_918_800_000L },   // 2026-03-27 09:30 IST
        )
    }

    // --- happy path: a real Kotak UPI debit ingests end-to-end ---

    @Test
    fun `Kotak UPI debit creates one sms_log, one transaction, one source`() = runTest {
        val raw = rawSms(
            sender = "JD-KOTAKB-S",
            body = "Sent Rs.100.00 from Kotak Bank AC X0000 to " +
                "0000000000000000.bqr@kotak on 01-01-26.UPI Ref 000000000000. " +
                "Not you, URL",
        )

        val outcome = pipeline.ingest(raw)

        outcome.shouldBeInstanceOf<IngestionOutcome.Ingested>()
        outcome.classifiedOutcome.finalClassification shouldBe Classification.UPI_PAYMENT
        outcome.classifiedOutcome.budgetEffect shouldBe BudgetEffect.SPEND

        smsLog.all.size shouldBe 1
        val smsRow = smsLog.all.single()
        smsRow.classification shouldBe "UPI_PAYMENT"
        smsRow.patternMatched shouldBe "kotak_upi_debit"

        transactions.all.size shouldBe 1
        transactions.all.single().inrAmount shouldBe 100.0

        sources.all.size shouldBe 1
        sources.all.single().isPrimary shouldBe true
    }

    // --- DROP effect: CC_BILL_PAYMENT through CRED middleman ---

    @Test
    fun `CRED middleman debit stores sms_log but no transaction`() = runTest {
        val raw = rawSms(
            sender = "JD-KOTAKB-S",
            body = "Sent Rs.1000.00 from Kotak Bank AC X0000 to " +
                "cred.club@axisb on 01-01-26.UPI Ref 000000000000. " +
                "Not you, URL",
        )

        val outcome = pipeline.ingest(raw)

        outcome.shouldBeInstanceOf<IngestionOutcome.Ingested>()
        outcome.classifiedOutcome.finalClassification shouldBe
            Classification.CC_BILL_PAYMENT
        outcome.classifiedOutcome.budgetEffect shouldBe BudgetEffect.DROP
        outcome.transactionDecision.shouldBeNull()

        smsLog.all.size shouldBe 1
        transactions.all.size shouldBe 0
        sources.all.size shouldBe 0
    }

    // --- dedup: same android_sms_id is rejected before classify ---

    @Test
    fun `duplicate android_sms_id short-circuits as Duplicate`() = runTest {
        val first = rawSms(
            androidSmsId = 42L,
            sender = "JD-KOTAKB-S",
            body = "Sent Rs.100.00 from Kotak Bank AC X0000 to a@b on 01-04-26.UPI Ref 1. Not you, URL",
        )
        pipeline.ingest(first)

        val dup = first.copy(receivedAtMs = first.receivedAtMs + 5)
        val outcome = pipeline.ingest(dup)

        outcome.shouldBeInstanceOf<IngestionOutcome.Duplicate>()
        smsLog.all.size shouldBe 1    // didn't re-insert
        transactions.all.size shouldBe 1
    }

    @Test
    fun `live-then-import of the same SMS dedups on content`() = runTest {
        // Live path: SMS_RECEIVED fired with no provider id yet.
        val live = rawSms(
            androidSmsId = null,
            sender = "AX-KOTAKB-S",
            body = "Sent Rs.1.00 from Kotak Bank AC X0000 to " +
                "friend-1@okaxis on 01-01-26.UPI Ref 000000000000. Not you, URL",
        )
        pipeline.ingest(live)

        // Import path: a few seconds later the provider has assigned
        // an id and the import worker picks up the same physical SMS.
        val import = live.copy(
            androidSmsId = 7760L,
            receivedAtMs = live.receivedAtMs + 5_000,
        )
        val outcome = pipeline.ingest(import)

        outcome.shouldBeInstanceOf<IngestionOutcome.Duplicate>()
        smsLog.all.size shouldBe 1
        transactions.all.size shouldBe 1
        sources.all.size shouldBe 1
    }

    // --- UNMATCHED: personal SMS lands in sms_log with no transaction ---

    @Test
    fun `UNMATCHED personal SMS stores with DROP outcome`() = runTest {
        val raw = rawSms(
            sender = "+919876543210",
            body = "Hey, running late, see you in 10",
        )
        val outcome = pipeline.ingest(raw)

        outcome.shouldBeInstanceOf<IngestionOutcome.Ingested>()
        outcome.classifiedOutcome.finalClassification shouldBe Classification.UNMATCHED
        outcome.classifiedOutcome.budgetEffect shouldBe BudgetEffect.DROP
        smsLog.all.single().patternMatched.shouldBeNull()
        transactions.all.size shouldBe 0
    }

    // --- self-transfer: user's own UPI VPA via a registered rule ---

    @Test
    fun `UPI to registered own VPA becomes SELF_TRANSFER and drops`() = runTest {
        // Register own VPA as SELF_TRANSFER.
        rules.insert(
            RecipientRuleEntity(
                id = 1,
                pattern = """examplename-\d+@oksbi""",
                patternKind = "REGEX",
                reclassifyAs = "SELF_TRANSFER",
                accountId = null,
                source = "USER",
                note = null,
                isEnabled = true,
            ),
        )

        val raw = rawSms(
            sender = "JD-KOTAKB-S",
            body = "Sent Rs.100.00 from Kotak Bank AC X0000 to " +
                "examplename-4@oksbi on 01-01-26.UPI Ref 1. Not you, URL",
        )

        val outcome = pipeline.ingest(raw)

        outcome.shouldBeInstanceOf<IngestionOutcome.Ingested>()
        outcome.classifiedOutcome.finalClassification shouldBe Classification.SELF_TRANSFER
        outcome.classifiedOutcome.budgetEffect shouldBe BudgetEffect.DROP
        transactions.all.size shouldBe 0
    }

    // --- merge: two SMSes for same property-tax payment merge ---

    @Test
    fun `bank debit arriving after gateway merges and promotes primary`() = runTest {
        // First: eazypay gateway SMS.
        val gateway = rawSms(
            sender = "JD-ICICIT-S",
            body = "Dear Sir/Madam, you have made a payment of Rs. 100.00 " +
                "to VVCMC ONLINE PROPERTY TAX ACCOUNT vide ICICI Bank eazypay " +
                "reference ID 000000000000000.",
            receivedAtMs = 1_774_918_800_000L,
        )
        val firstOut = pipeline.ingest(gateway)
        (firstOut as IngestionOutcome.Ingested).transactionDecision
            .shouldBeInstanceOf<MergeDecision.CreatedNew>()

        // Second: a synthetic Kotak UPI debit 2 minutes later (mergeable).
        val bank = rawSms(
            sender = "JD-KOTAKB-S",
            body = "Sent Rs.100.00 from Kotak Bank AC X0000 to " +
                "vvcmc-bill@okicici on 01-01-26.UPI Ref 1. Not you, URL",
            receivedAtMs = gateway.receivedAtMs + 2 * 60_000L,
        )
        val secondOut = pipeline.ingest(bank)

        val decision = (secondOut as IngestionOutcome.Ingested).transactionDecision
        decision.shouldBeInstanceOf<MergeDecision.MergedInto>()
        decision.primaryPromoted shouldBe true

        transactions.all.size shouldBe 1
        sources.all.size shouldBe 2
        sources.all.single { it.isPrimary }.role shouldBe "BANK_DEBIT"
    }

    // --- forex: creates pending-rate transaction, no merge ---

    @Test
    fun `forex SMS creates pending transaction with null inr_amount`() = runTest {
        val raw = rawSms(
            sender = "AX-AXISBK-S",
            body = "Spent USD 10.00\nAxis Bank Card no. XX1111\n" +
                "01-01-26 12:00:00 IST\nGITHUB, INC\nAvl Limit: INR 100000.00",
        )
        val outcome = pipeline.ingest(raw)

        outcome.shouldBeInstanceOf<IngestionOutcome.Ingested>()
        val tx = transactions.all.single()
        tx.inrAmount.shouldBeNull()
        tx.originalAmount shouldBe 10.00
        tx.originalCurrency shouldBe "USD"
        tx.rateSource shouldBe "pending"
    }

    // --- alert evaluation -------------------------------------------------

    @Test
    fun `no alerts when no budget is set for the month`() = runTest {
        // No budget row → percent=0 → no thresholds cross.
        val raw = rawSms(
            sender = "JD-KOTAKB-S",
            body = "Sent Rs.9999.00 from Kotak Bank AC X0000 to a@b on 27-03-26.UPI Ref 1. Not you, URL",
        )
        val outcome = pipeline.ingest(raw) as IngestionOutcome.Ingested
        outcome.alertEvaluation?.newlyFired shouldBe emptyList()
        alertState.all() shouldBe emptyList()
    }

    @Test
    fun `50pct threshold fires on current-month crossing with isCurrentMonth=true`() = runTest {
        budgets.upsert(
            com.spendwise.database.entities.BudgetEntity(
                monthKey = "2026-03",
                limitInr = 10_000.0,
                thresholdWarnPct = 80,
                createdAtMs = 0, updatedAtMs = 0,
            ),
        )
        val raw = rawSms(
            sender = "JD-KOTAKB-S",
            body = "Sent Rs.5500.00 from Kotak Bank AC X0000 to a@b on 27-03-26.UPI Ref 1. Not you, URL",
            receivedAtMs = 1_774_918_800_000L,
        )

        val outcome = pipeline.ingest(raw) as IngestionOutcome.Ingested
        val eval = outcome.alertEvaluation!!
        eval.newlyFired shouldBe listOf(50)
        eval.isCurrentMonth shouldBe true
        alertState.all().map { it.thresholdPct } shouldBe listOf(50)
    }

    @Test
    fun `threshold fires exactly once then does not re-fire`() = runTest {
        budgets.upsert(
            com.spendwise.database.entities.BudgetEntity(
                monthKey = "2026-03",
                limitInr = 10_000.0,
                thresholdWarnPct = 80,
                createdAtMs = 0, updatedAtMs = 0,
            ),
        )
        pipeline.ingest(
            rawSms(
                sender = "JD-KOTAKB-S",
                body = "Sent Rs.5500.00 from Kotak Bank AC X0000 to a@b on 27-03-26.UPI Ref 1. Not you, URL",
                receivedAtMs = 1_774_918_800_000L,
            ),
        )
        val second = pipeline.ingest(
            rawSms(
                sender = "JD-KOTAKB-S",
                body = "Sent Rs.1000.00 from Kotak Bank AC X0000 to c@d on 27-03-26.UPI Ref 2. Not you, URL",
                receivedAtMs = 1_774_918_800_000L + 60_000L,
            ),
        ) as IngestionOutcome.Ingested
        // Spend is now 6500/10000 = 65% — still over 50 but 50 already fired.
        second.alertEvaluation!!.newlyFired shouldBe emptyList()
        alertState.all().map { it.thresholdPct } shouldBe listOf(50)
    }

    @Test
    fun `multiple thresholds fire together when spend jumps past several at once`() = runTest {
        budgets.upsert(
            com.spendwise.database.entities.BudgetEntity(
                monthKey = "2026-03",
                limitInr = 10_000.0,
                thresholdWarnPct = 80,
                createdAtMs = 0, updatedAtMs = 0,
            ),
        )
        val raw = rawSms(
            sender = "JD-KOTAKB-S",
            body = "Sent Rs.12000.00 from Kotak Bank AC X0000 to a@b on 27-03-26.UPI Ref 1. Not you, URL",
            receivedAtMs = 1_774_918_800_000L,
        )
        val outcome = pipeline.ingest(raw) as IngestionOutcome.Ingested
        outcome.alertEvaluation!!.newlyFired shouldBe listOf(50, 80, 100, 110, 120)
    }

    @Test
    fun `past-month transaction records fires with isCurrentMonth=false`() = runTest {
        // Budget for an old month, SMS occurred in that old month.
        budgets.upsert(
            com.spendwise.database.entities.BudgetEntity(
                monthKey = "2026-01",
                limitInr = 10_000.0,
                thresholdWarnPct = 80,
                createdAtMs = 0, updatedAtMs = 0,
            ),
        )
        val oldJanSms = rawSms(
            sender = "JD-KOTAKB-S",
            body = "Sent Rs.5500.00 from Kotak Bank AC X0000 to a@b on 15-01-26.UPI Ref 1. Not you, URL",
            receivedAtMs = 1_768_587_300_000L,    // 2026-01-16 in IST
        )
        val outcome = pipeline.ingest(oldJanSms) as IngestionOutcome.Ingested
        val eval = outcome.alertEvaluation!!
        eval.newlyFired shouldBe listOf(50)
        eval.isCurrentMonth shouldBe false
        // Firing still recorded — §7.6 silent-fired semantics.
        alertState.all().map { it.monthKey to it.thresholdPct } shouldBe
            listOf("2026-01" to 50)
    }

    @Test
    fun `refund does not re-arm a previously fired threshold`() = runTest {
        budgets.upsert(
            com.spendwise.database.entities.BudgetEntity(
                monthKey = "2026-03",
                limitInr = 10_000.0,
                thresholdWarnPct = 80,
                createdAtMs = 0, updatedAtMs = 0,
            ),
        )
        // Cross 50% threshold.
        pipeline.ingest(
            rawSms(
                sender = "JD-KOTAKB-S",
                body = "Sent Rs.6000.00 from Kotak Bank AC X0000 to a@b on 27-03-26.UPI Ref 1. Not you, URL",
                receivedAtMs = 1_774_918_800_000L,
            ),
        )
        // Refund arrives → percent drops; 50 stays fired, no new fires.
        val refundOutcome = pipeline.ingest(
            rawSms(
                sender = "JK-blnkit-S",
                body = "We have initiated a refund of Rs.3000.00 for the cancelled order ORD0000000 into your UPI after applying a cancellation fee of Rs 20.00. -blinkit",
                receivedAtMs = 1_774_918_800_000L + 60_000L,
            ),
        ) as IngestionOutcome.Ingested
        refundOutcome.alertEvaluation!!.newlyFired shouldBe emptyList()
        alertState.all().map { it.thresholdPct } shouldBe listOf(50)
    }

    // --- helper -----------------------------------------------------------

    private fun rawSms(
        androidSmsId: Long? = null,
        sender: String,
        body: String,
        receivedAtMs: Long = 1_774_918_800_000L,   // 2026-03-27 09:30 IST
        source: SmsSource = SmsSource.SMS_REALTIME,
    ) = RawSms(androidSmsId, sender, body, receivedAtMs, source)
}
