package com.spendwise.ingestion

import android.database.sqlite.SQLiteConstraintException
import com.spendwise.budget.AlertStateMachine
import com.spendwise.budget.Budget
import com.spendwise.budget.BudgetCalculator
import com.spendwise.budget.MonthSnapshot
import com.spendwise.budget.Transaction as BudgetTx
import com.spendwise.classifier.AccountAutoDetector
import com.spendwise.classifier.BudgetEffect
import com.spendwise.classifier.Classifier
import com.spendwise.database.dao.AccountDao
import com.spendwise.database.dao.BudgetAlertStateDao
import com.spendwise.database.dao.BudgetDao
import com.spendwise.database.dao.RecipientRuleDao
import com.spendwise.database.dao.SmsLogDao
import com.spendwise.database.dao.TransactionDao
import com.spendwise.database.dao.TransactionSourceDao
import com.spendwise.database.entities.BudgetAlertStateEntity
import com.spendwise.database.entities.SmsLogEntity
import com.spendwise.database.mappers.AccountMapper
import com.spendwise.database.mappers.RecipientRuleMapper
import com.spendwise.database.mappers.TransactionMapper
import com.spendwise.database.mappers.TransactionSourceMapper
import com.spendwise.parser.Parser
import com.spendwise.parser.SmsInput
import com.spendwise.transactions.IncomingEvent
import com.spendwise.transactions.IssuerDeriver
import com.spendwise.transactions.MergeDecision
import com.spendwise.transactions.MonthKeyComputer
import com.spendwise.transactions.TransactionBuilder
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * End-to-end ingestion of one [RawSms].
 *
 * Orchestrates the pure-JVM domain layers against the Room DAOs.
 * Still a thin coordinator — all real logic lives in [Parser],
 * [Classifier], [TransactionBuilder], [BudgetCalculator],
 * [AlertStateMachine]. This class handles DB reads/writes and glue.
 *
 * Single public method: [ingest]. Designed to run on a background
 * dispatcher (the caller's concern).
 */
class IngestionPipeline(
    private val smsLogDao: SmsLogDao,
    private val transactionDao: TransactionDao,
    private val transactionSourceDao: TransactionSourceDao,
    private val accountDao: AccountDao,
    private val recipientRuleDao: RecipientRuleDao,
    private val budgetDao: BudgetDao,
    private val budgetAlertStateDao: BudgetAlertStateDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun ingest(raw: RawSms): IngestionOutcome {
        raw.androidSmsId?.let { id ->
            smsLogDao.findByAndroidSmsId(id)?.let { existing ->
                return IngestionOutcome.Duplicate(existingSmsLogId = existing.id)
            }
        }

        val parseResult = Parser.parse(SmsInput(sender = raw.sender, body = raw.body))

        val smsLogRow = SmsLogEntity(
            androidSmsId = raw.androidSmsId,
            sender = raw.sender,
            body = raw.body,
            receivedAtMs = raw.receivedAtMs,
            classification = parseResult.classification.name,
            patternMatched = parseResult.pattern.takeUnless { it == "UNMATCHED" },
            source = raw.source.name,
        )
        val smsLogId = try {
            smsLogDao.insert(smsLogRow)
        } catch (e: SQLiteConstraintException) {
            val existing = raw.androidSmsId?.let { smsLogDao.findByAndroidSmsId(it) }
            if (existing != null) {
                return IngestionOutcome.Duplicate(existingSmsLogId = existing.id)
            }
            throw e
        }

        val accounts = accountDao.getAll().map(AccountMapper::toDomain)
        val rules = recipientRuleDao.getEnabled().map(RecipientRuleMapper::toDomain)

        // Auto-detect: if this SMS references an account we don't know
        // yet, stage a SUGGESTED row. Never blocks the pipeline and
        // never changes the classifier's view of the world (resolver
        // ignores non-CONFIRMED rows).
        applyAutoDetect(raw.sender, parseResult, accounts)

        val classified = Classifier.classify(parseResult, accounts, rules)

        val monthKeyOfEvent = MonthKeyComputer.of(raw.receivedAtMs, zone)

        val decision = runTransactionBuilder(
            smsLogId = smsLogId,
            parseResult = parseResult,
            classified = classified,
            occurredAtMs = raw.receivedAtMs,
            monthKey = monthKeyOfEvent,
            issuer = IssuerDeriver.fromSender(raw.sender),
        )

        // Only SPEND and REFUND change the month's spend total and thus
        // potentially cross thresholds. INCOME / DROP never fire alerts.
        // INCOMING_CREDIT is INCOME → no alert evaluation.
        val alertEvaluation = if (
            decision != null &&
            decision !is MergeDecision.Dropped &&
            (classified.budgetEffect == BudgetEffect.SPEND ||
                classified.budgetEffect == BudgetEffect.REFUND)
        ) {
            evaluateAlerts(monthKeyOfEvent)
        } else {
            null
        }

        return IngestionOutcome.Ingested(
            smsLogId = smsLogId,
            classifiedOutcome = classified,
            transactionDecision = decision,
            alertEvaluation = alertEvaluation,
        )
    }

    // --- transaction builder path ---------------------------------------

    private suspend fun runTransactionBuilder(
        smsLogId: Long,
        parseResult: com.spendwise.parser.ParseResult,
        classified: com.spendwise.classifier.ClassificationOutcome,
        occurredAtMs: Long,
        monthKey: String,
        issuer: String?,
    ): MergeDecision? {
        if (classified.budgetEffect == BudgetEffect.DROP) return null

        val event = IncomingEvent(
            smsLogId = smsLogId,
            outcome = classified,
            parserPattern = parseResult.pattern,
            occurredAtMs = occurredAtMs,
            monthKey = monthKey,
            issuer = issuer,
        )

        val inrAmount = classified.amount ?: return null
        if (classified.currency != "INR") {
            return applyBuilderAndPersist(event, existingTransactions = emptyList())
        }

        val candidates = transactionDao.findDedupCandidates(
            effect = classified.budgetEffect.name,
            inrAmount = inrAmount,
            tolerance = DEDUP_AMOUNT_TOLERANCE_INR,
            occurredAtMs = occurredAtMs,
            windowMs = DEDUP_WINDOW_MS,
        ).map(TransactionMapper::toDomain)

        return applyBuilderAndPersist(event, existingTransactions = candidates)
    }

    private suspend fun applyBuilderAndPersist(
        event: IncomingEvent,
        existingTransactions: List<com.spendwise.transactions.TransactionRow>,
    ): MergeDecision {
        val candidateIds = existingTransactions.map { it.id }
        val existingSources = candidateIds
            .flatMap { transactionSourceDao.findByTransactionId(it) }
            .map(TransactionSourceMapper::toDomain)

        val idAllocator: () -> Long = { NEW_ID }
        val result = TransactionBuilder.apply(
            event = event,
            transactions = existingTransactions,
            sources = existingSources,
            idAllocator = idAllocator,
        )

        when (val decision = result.decision) {
            is MergeDecision.CreatedNew -> {
                val row = result.transactions.single { it.id == NEW_ID }
                val entity = TransactionMapper.toEntity(row.copy(id = 0))
                val realId = transactionDao.insert(entity)
                val source = result.sources.single { it.transactionId == NEW_ID }
                    .copy(transactionId = realId)
                transactionSourceDao.insert(TransactionSourceMapper.toEntity(source))
            }
            is MergeDecision.MergedInto -> {
                if (decision.primaryPromoted) {
                    transactionSourceDao.clearPrimary(decision.transactionId)
                    val updatedRow = result.transactions.single {
                        it.id == decision.transactionId
                    }
                    transactionDao.update(TransactionMapper.toEntity(updatedRow))
                }
                val newSource = result.sources.single {
                    it.transactionId == decision.transactionId &&
                        it.smsLogId == event.smsLogId
                }
                transactionSourceDao.insert(TransactionSourceMapper.toEntity(newSource))
            }
            is MergeDecision.Dropped -> Unit
        }
        return result.decision
    }

    // --- alert evaluation ------------------------------------------------

    private suspend fun evaluateAlerts(monthKey: String): AlertEvaluation {
        // 1. Pull this month's transactions (for the snapshot math).
        val thisMonthTxs = transactionDao.observeByMonth(monthKey)
            .firstOrNullSnapshot()
            ?: emptyList()
        val thisMonth: List<BudgetTx> = thisMonthTxs.map { it.toBudgetTx() }

        // 2. Load all prior-month budgets for carry-over (§6.1).
        val priorBudgets = budgetDao.getAllBefore(monthKey)
            .map { Budget(it.monthKey, it.limitInr, it.thresholdWarnPct) }
        val currentBudgetEntity = budgetDao.getByMonth(monthKey)
        val allBudgets = priorBudgets + listOfNotNull(
            currentBudgetEntity?.let { Budget(it.monthKey, it.limitInr, it.thresholdWarnPct) },
        )

        // 3. Pull prior-month transactions that affect carry-over.
        //    Simple v1 approach: sum prior transactions by month via the
        //    calculator, reading all rows per prior month. Small scale.
        val priorTxs: MutableList<BudgetTx> = mutableListOf()
        for (b in priorBudgets) {
            val rows = transactionDao.observeByMonth(b.monthKey)
                .firstOrNullSnapshot()
                ?: emptyList()
            priorTxs += rows.map { it.toBudgetTx() }
        }

        val snapshot: MonthSnapshot = BudgetCalculator.snapshot(
            transactions = priorTxs + thisMonth,
            budgets = allBudgets,
            monthKey = monthKey,
        )

        // 4. Alert evaluation — only fires when a budget exists. If no
        //    budget is set this month, percent=0 and nothing crosses.
        val warnPct = currentBudgetEntity?.thresholdWarnPct ?: 80
        val alreadyFired = budgetAlertStateDao.firedThresholdsForMonth(monthKey).toSet()
        val newlyFired = AlertStateMachine.thresholdsToFire(
            percentUsed = snapshot.percentUsed,
            warnThresholdPct = warnPct,
            alreadyFiredPcts = alreadyFired,
        )

        // 5. Record the firings (even for past months — silent-fired per §7.6).
        val firedAtMs = nowMs()
        for (pct in newlyFired) {
            budgetAlertStateDao.recordFiring(
                BudgetAlertStateEntity(
                    monthKey = monthKey,
                    thresholdPct = pct,
                    firedAtMs = firedAtMs,
                ),
            )
        }

        val isCurrent = monthKey == MonthKeyComputer.of(firedAtMs, zone)

        return AlertEvaluation(
            monthKey = monthKey,
            snapshot = snapshot,
            warnThresholdPct = warnPct,
            newlyFired = newlyFired,
            isCurrentMonth = isCurrent,
        )
    }

    private fun com.spendwise.database.entities.TransactionEntity.toBudgetTx(): BudgetTx =
        BudgetTx(
            id = id,
            monthKey = monthKey,
            inrAmount = inrAmount,
            budgetEffect = BudgetEffect.valueOf(budgetEffect),
            occurredAtMs = occurredAtMs,
        )

    /**
     * Pull a single snapshot from a Flow. Fine here — we're inside a
     * suspend function and the underlying Room query emits
     * immediately on collection.
     */
    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstOrNullSnapshot(): T? =
        try {
            this.first()
        } catch (_: NoSuchElementException) {
            null
        }

    private suspend fun applyAutoDetect(
        sender: String,
        parseResult: com.spendwise.parser.ParseResult,
        accounts: List<com.spendwise.classifier.Account>,
    ) {
        val action = AccountAutoDetector.detect(
            sender = sender,
            parserLast4 = parseResult.last4,
            classification = parseResult.classification,
            existingAccounts = accounts,
        ) ?: return

        when (action) {
            is AccountAutoDetector.Action.Create -> {
                val now = nowMs()
                try {
                    accountDao.insert(
                        com.spendwise.database.entities.AccountEntity(
                            kind = action.kind.name,
                            issuer = action.issuer,
                            last4 = action.last4,
                            isDefaultSpend = false,
                            createdAtMs = now,
                            status = "SUGGESTED",
                            firstSeenMs = now,
                            seenCount = 1,
                        ),
                    )
                } catch (_: SQLiteConstraintException) {
                    // Race: another SMS beat us to insert. Ignore.
                }
            }
            is AccountAutoDetector.Action.BumpSeen -> {
                accountDao.bumpSeenCount(action.accountId)
            }
        }
    }

    companion object {
        private const val NEW_ID = -1L
        private const val DEDUP_WINDOW_MS: Long = 5L * 60 * 1000L
        private const val DEDUP_AMOUNT_TOLERANCE_INR: Double = 0.5
    }
}
