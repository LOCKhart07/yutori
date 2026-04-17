package com.yutori.ingestion

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneId

/**
 * Covers the [IngestionCoordinator.dispatchAlertsIfNeeded] branch that
 * suppresses past-month notifications (business-logic-spec §7.6). The
 * pipeline still records fires in `budget_alert_state`, but the
 * coordinator must not call [AlertNotifier.notify] when the SMS lands
 * in a prior device-local month.
 */
class IngestionCoordinatorTest {

    private lateinit var smsLog: FakeSmsLogDao
    private lateinit var transactions: FakeTransactionDao
    private lateinit var sources: FakeTransactionSourceDao
    private lateinit var accounts: FakeAccountDao
    private lateinit var rules: FakeRecipientRuleDao
    private lateinit var budgets: FakeBudgetDao
    private lateinit var alertState: FakeBudgetAlertStateDao
    private lateinit var notifier: RecordingAlertNotifier

    private val zone = ZoneId.of("Asia/Kolkata")
    private val marchNow = 1_774_918_800_000L    // 2026-03-27 09:30 IST

    @BeforeEach
    fun setUp() {
        smsLog = FakeSmsLogDao()
        transactions = FakeTransactionDao()
        sources = FakeTransactionSourceDao()
        accounts = FakeAccountDao()
        rules = FakeRecipientRuleDao()
        budgets = FakeBudgetDao()
        alertState = FakeBudgetAlertStateDao()
        notifier = RecordingAlertNotifier()
    }

    private fun buildCoordinator(impactCfg: ImpactConfig = ImpactConfig.OFF): IngestionCoordinator {
        val pipeline = IngestionPipeline(
            smsLogDao = smsLog,
            transactionDao = transactions,
            transactionSourceDao = sources,
            accountDao = accounts,
            recipientRuleDao = rules,
            budgetDao = budgets,
            budgetAlertStateDao = alertState,
            zone = zone,
            nowMs = { marchNow },
            impactConfigProvider = { impactCfg },
        )
        return IngestionCoordinator(pipeline = pipeline, alertNotifier = notifier)
    }

    @Test
    fun `current-month threshold crossing dispatches one notify per fired threshold`() = runTest {
        budgets.upsert(
            com.yutori.database.entities.BudgetEntity(
                monthKey = "2026-03",
                limitInr = 10_000.0,
                thresholdWarnPct = 80,
                createdAtMs = 0, updatedAtMs = 0,
            ),
        )
        val coordinator = buildCoordinator()
        coordinator.ingestAndNotify(
            RawSms(
                androidSmsId = null,
                sender = "JD-KOTAKB-S",
                body = "Sent Rs.5500.00 from Kotak Bank AC X0000 to a@b on 27-03-26.UPI Ref 1. Not you, URL",
                receivedAtMs = marchNow,
                source = SmsSource.SMS_REALTIME,
            ),
        )
        notifier.notifyCalls.map { it.thresholdPct } shouldBe listOf(50)
    }

    @Test
    fun `past-month crossing stamps state but skips notify`() = runTest {
        budgets.upsert(
            com.yutori.database.entities.BudgetEntity(
                monthKey = "2026-01",
                limitInr = 10_000.0,
                thresholdWarnPct = 80,
                createdAtMs = 0, updatedAtMs = 0,
            ),
        )
        val coordinator = buildCoordinator()
        coordinator.ingestAndNotify(
            RawSms(
                androidSmsId = null,
                sender = "JD-KOTAKB-S",
                body = "Sent Rs.12000.00 from Kotak Bank AC X0000 to a@b on 15-01-26.UPI Ref 1. Not you, URL",
                receivedAtMs = 1_768_587_300_000L,    // 2026-01-16 IST
                source = SmsSource.SMS_IMPORT,
            ),
        )
        notifier.notifyCalls shouldBe emptyList()
        alertState.all().map { it.monthKey to it.thresholdPct } shouldBe listOf(
            "2026-01" to 50,
            "2026-01" to 80,
            "2026-01" to 100,
            "2026-01" to 110,
            "2026-01" to 120,
        )
    }

    @Test
    fun `past-month SPEND does not fire impact notification`() = runTest {
        budgets.upsert(
            com.yutori.database.entities.BudgetEntity(
                monthKey = "2026-01", limitInr = 10_000.0,
                createdAtMs = 0, updatedAtMs = 0,
            ),
        )
        val coordinator = buildCoordinator(
            impactCfg = ImpactConfig(enabled = true, thresholdPct = 10),
        )
        coordinator.ingestAndNotify(
            RawSms(
                androidSmsId = null,
                sender = "JD-KOTAKB-S",
                body = "Sent Rs.1500.00 from Kotak Bank AC X0000 to merchant@oksbi on 15-01-26.UPI Ref 1. Not you, URL",
                receivedAtMs = 1_768_587_300_000L,
                source = SmsSource.SMS_IMPORT,
            ),
        )
        notifier.impactCalls shouldBe emptyList()
    }

    @Test
    fun `current-month SPEND clears the impact threshold and fires impact notify`() = runTest {
        budgets.upsert(
            com.yutori.database.entities.BudgetEntity(
                monthKey = "2026-03", limitInr = 10_000.0,
                createdAtMs = 0, updatedAtMs = 0,
            ),
        )
        val coordinator = buildCoordinator(
            impactCfg = ImpactConfig(enabled = true, thresholdPct = 10),
        )
        coordinator.ingestAndNotify(
            RawSms(
                androidSmsId = null,
                sender = "JD-KOTAKB-S",
                body = "Sent Rs.1500.00 from Kotak Bank AC X0000 to merchant@oksbi on 27-03-26.UPI Ref 1. Not you, URL",
                receivedAtMs = marchNow,
                source = SmsSource.SMS_REALTIME,
            ),
        )
        notifier.impactCalls.size shouldBe 1
        notifier.impactCalls.single().percentOfBudget shouldBe 15.0
    }
}

private class RecordingAlertNotifier : AlertNotifier {
    data class NotifyCall(val thresholdPct: Int, val evaluation: AlertEvaluation)

    val notifyCalls: MutableList<NotifyCall> = mutableListOf()
    val impactCalls: MutableList<ImpactNotification> = mutableListOf()

    override fun notify(thresholdPct: Int, evaluation: AlertEvaluation) {
        notifyCalls += NotifyCall(thresholdPct, evaluation)
    }

    override fun notifyImpact(impact: ImpactNotification) {
        impactCalls += impact
    }
}
