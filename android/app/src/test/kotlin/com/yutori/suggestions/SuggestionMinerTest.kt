package com.yutori.suggestions

import com.yutori.database.dao.AccountDao
import com.yutori.database.dao.MerchantAggregateRow
import com.yutori.database.dao.RecipientRuleDao
import com.yutori.database.dao.RuleSuggestionDao
import com.yutori.database.dao.TransactionDao
import com.yutori.database.entities.AccountEntity
import com.yutori.database.entities.RecipientRuleEntity
import com.yutori.database.entities.RuleSuggestionEntity
import com.yutori.database.entities.SmsLogEntity
import com.yutori.database.entities.TransactionEntity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SuggestionMinerTest {

    private val fakeAccount = AccountEntity(
        id = 1,
        kind = "SAVINGS",
        issuer = "Kotak",
        last4 = "0000",
        displayName = "Primary",
        isDefaultSpend = true,
        createdAtMs = 0,
    )
    private val nowMs = 1_700_000_000_000L

    @Test
    fun `inserts a new suggestion for an uncovered repeat merchant`() = runTest {
        val txDao = FakeTxDao(
            candidates = listOf(
                row("cheq@axisbank", matchCount = 4, totalInr = 12500.0),
            ),
        )
        val ruleDao = FakeRuleDao(enabledRules = emptyList())
        val accountDao = FakeAccountDao(listOf(fakeAccount))
        val sugDao = FakeSuggestionDao()

        val miner = SuggestionMiner(txDao, sugDao, ruleDao, accountDao)
        val report = miner.runOnce(nowMs)

        report.inserted shouldBe 1
        report.updated shouldBe 0
        sugDao.rows.values.single().apply {
            merchantKey shouldBe "cheq@axisbank"
            inferredClassification shouldBe "CC_BILL_PAYMENT"
            reasonCode shouldBe "KEYWORD_MIDDLEMAN"
            matchCount shouldBe 4
            totalInr shouldBe 12500.0
            firstSeenMs shouldBe nowMs
            lastScannedMs shouldBe nowMs
            dismissedAtMs.shouldBeNull()
        }
    }

    @Test
    fun `skips merchants already covered by an enabled rule`() = runTest {
        val existingRule = RecipientRuleEntity(
            id = 1,
            pattern = "cheq@axisbank",
            patternKind = "LITERAL",
            reclassifyAs = "CC_BILL_PAYMENT",
            source = "USER",
            isEnabled = true,
        )
        val txDao = FakeTxDao(
            candidates = listOf(row("cheq@axisbank", 4, 12500.0)),
        )
        val ruleDao = FakeRuleDao(enabledRules = listOf(existingRule))
        val accountDao = FakeAccountDao(listOf(fakeAccount))
        val sugDao = FakeSuggestionDao()

        val miner = SuggestionMiner(txDao, sugDao, ruleDao, accountDao)
        val report = miner.runOnce(nowMs)

        report.inserted shouldBe 0
        sugDao.rows.values.shouldBe(emptyList())
    }

    @Test
    fun `updates an existing active suggestion without touching first_seen_ms`() = runTest {
        val originalFirstSeen = nowMs - 86_400_000L
        val seeded = RuleSuggestionEntity(
            id = 1,
            merchantKey = "cheq@axisbank",
            pattern = "cheq@axisbank",
            patternKind = "LITERAL",
            inferredClassification = "CC_BILL_PAYMENT",
            inferredAccountId = null,
            reasonCode = "KEYWORD_MIDDLEMAN",
            matchCount = 3,
            totalInr = 5000.0,
            firstSeenMs = originalFirstSeen,
            lastScannedMs = originalFirstSeen,
            dismissedAtMs = null,
        )
        val sugDao = FakeSuggestionDao(initial = listOf(seeded))
        val txDao = FakeTxDao(candidates = listOf(row("cheq@axisbank", 5, 18000.0)))
        val ruleDao = FakeRuleDao(enabledRules = emptyList())

        val miner = SuggestionMiner(txDao, sugDao, ruleDao, FakeAccountDao())
        val report = miner.runOnce(nowMs)

        report.updated shouldBe 1
        report.inserted shouldBe 0
        val after = sugDao.rows.values.single()
        after.matchCount shouldBe 5
        after.totalInr shouldBe 18000.0
        after.lastScannedMs shouldBe nowMs
        after.firstSeenMs shouldBe originalFirstSeen
        after.dismissedAtMs.shouldBeNull()
    }

    @Test
    fun `dismissed suggestion stays frozen when count has not doubled`() = runTest {
        val dismissedAt = nowMs - 86_400_000L
        val dismissed = baseEntity(
            matchCount = 4,
            dismissedAtMs = dismissedAt,
        )
        val sugDao = FakeSuggestionDao(initial = listOf(dismissed))
        // match_count climbed 4 → 6, but 6 < 2*4 = 8. Stay dismissed.
        val txDao = FakeTxDao(candidates = listOf(row("cheq@axisbank", 6, 20_000.0)))

        val miner = SuggestionMiner(txDao, sugDao, FakeRuleDao(), FakeAccountDao())
        val report = miner.runOnce(nowMs)

        report.inserted shouldBe 0
        report.updated shouldBe 0
        report.resurfaced shouldBe 0
        report.skippedDismissed shouldBe 1

        val after = sugDao.rows.values.single()
        after.matchCount shouldBe 4
        after.dismissedAtMs shouldBe dismissedAt
    }

    @Test
    fun `dismissed suggestion resurfaces when match_count reaches 2x threshold`() = runTest {
        val dismissedAt = nowMs - 86_400_000L
        val dismissed = baseEntity(
            matchCount = 4,
            dismissedAtMs = dismissedAt,
        )
        val sugDao = FakeSuggestionDao(initial = listOf(dismissed))
        // 8 >= 2*4 → resurface.
        val txDao = FakeTxDao(candidates = listOf(row("cheq@axisbank", 8, 30_000.0)))

        val miner = SuggestionMiner(txDao, sugDao, FakeRuleDao(), FakeAccountDao())
        val report = miner.runOnce(nowMs)

        report.resurfaced shouldBe 1
        val after = sugDao.rows.values.single()
        after.dismissedAtMs.shouldBeNull()
        after.matchCount shouldBe 8
    }

    @Test
    fun `pruning runs before upserts and uses the 90-day cutoff`() = runTest {
        val sugDao = FakeSuggestionDao()
        val miner = SuggestionMiner(FakeTxDao(), sugDao, FakeRuleDao(), FakeAccountDao())

        miner.runOnce(nowMs)

        sugDao.pruneCalls.single() shouldBe (nowMs - SuggestionMiner.STALE_PRUNE_MS)
    }

    @Test
    fun `candidate aggregate uses the 60-day cutoff, threshold of 3, and limit of 50`() = runTest {
        val txDao = FakeTxDao()
        val miner = SuggestionMiner(txDao, FakeSuggestionDao(), FakeRuleDao(), FakeAccountDao())

        miner.runOnce(nowMs)

        txDao.aggregateCalls.single() shouldBe Triple(
            nowMs - SuggestionMiner.CANDIDATE_WINDOW_MS,
            SuggestionMiner.MIN_MATCH_COUNT,
            SuggestionMiner.CANDIDATE_LIMIT,
        )
    }

    @Test
    fun `unsure inference writes null classification and REPEAT_NO_DEFAULT`() = runTest {
        val txDao = FakeTxDao(candidates = listOf(row("swiggy-payments@paytm", 9, 6420.0)))
        val sugDao = FakeSuggestionDao()
        val miner = SuggestionMiner(txDao, sugDao, FakeRuleDao(), FakeAccountDao())

        miner.runOnce(nowMs)

        val stored = sugDao.rows.values.single()
        stored.inferredClassification.shouldBeNull()
        stored.reasonCode shouldBe "REPEAT_NO_DEFAULT"
    }

    private fun row(key: String, matchCount: Int, totalInr: Double) =
        MerchantAggregateRow(key, matchCount, totalInr)

    private fun baseEntity(
        id: Long = 1,
        matchCount: Int = 4,
        totalInr: Double = 12500.0,
        dismissedAtMs: Long? = null,
    ) = RuleSuggestionEntity(
        id = id,
        merchantKey = "cheq@axisbank",
        pattern = "cheq@axisbank",
        patternKind = "LITERAL",
        inferredClassification = "CC_BILL_PAYMENT",
        inferredAccountId = null,
        reasonCode = "KEYWORD_MIDDLEMAN",
        matchCount = matchCount,
        totalInr = totalInr,
        firstSeenMs = nowMs - 172_800_000L,
        lastScannedMs = nowMs - 86_400_000L,
        dismissedAtMs = dismissedAtMs,
    )

    // --- Fakes -------------------------------------------------------

    private class FakeTxDao(
        private val candidates: List<MerchantAggregateRow> = emptyList(),
    ) : TransactionDao {
        val aggregateCalls = mutableListOf<Triple<Long, Int, Int>>()

        override suspend fun aggregateSuggestionCandidates(
            cutoffMs: Long,
            threshold: Int,
            limit: Int,
        ): List<MerchantAggregateRow> {
            aggregateCalls += Triple(cutoffMs, threshold, limit)
            return candidates
        }

        // Unused by the miner — minimal stubs.
        override suspend fun insert(row: TransactionEntity) = 0L
        override suspend fun update(row: TransactionEntity) = Unit
        override suspend fun delete(row: TransactionEntity) = Unit
        override suspend fun getById(id: Long): TransactionEntity? = null
        override suspend fun updateNote(id: Long, note: String?): Int = 0
        override fun observeByMonth(monthKey: String): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun getBeforeMonth(monthKey: String): List<TransactionEntity> = emptyList()
        override fun observeByMonthAndAccount(monthKey: String, accountId: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun observeByMonthAndCategory(monthKey: String, category: String): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun findDedupCandidates(
            effect: String,
            inrAmount: Double,
            tolerance: Double,
            occurredAtMs: Long,
            windowMs: Long,
        ): List<TransactionEntity> = emptyList()
        override fun observePendingForex(): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun observeEarliestMonthKey(): Flow<String?> = flowOf(null)
        override suspend fun sumSpendForMonth(monthKey: String): Double = 0.0
        override suspend fun sumRefundsForMonth(monthKey: String): Double = 0.0
        override suspend fun findBySelfTransferCandidateMerchant(merchant: String): List<TransactionEntity> = emptyList()
        override suspend fun findByMerchantKey(merchantKey: String): List<TransactionEntity> = emptyList()
    }

    private class FakeSuggestionDao(
        initial: List<RuleSuggestionEntity> = emptyList(),
    ) : RuleSuggestionDao {
        val rows: MutableMap<Long, RuleSuggestionEntity> =
            initial.associateBy { it.id }.toMutableMap()
        val pruneCalls = mutableListOf<Long>()
        private var nextId: Long = (initial.maxOfOrNull { it.id } ?: 0L) + 1

        override suspend fun insert(row: RuleSuggestionEntity): Long {
            require(rows.values.none { it.merchantKey == row.merchantKey }) {
                "UNIQUE constraint on merchant_key violated"
            }
            val assigned = if (row.id == 0L) nextId++ else row.id
            rows[assigned] = row.copy(id = assigned)
            return assigned
        }

        override suspend fun getByMerchantKey(key: String): RuleSuggestionEntity? =
            rows.values.firstOrNull { it.merchantKey == key }

        override suspend fun getById(id: Long): RuleSuggestionEntity? = rows[id]

        override suspend fun updateOnRescan(
            id: Long,
            pattern: String,
            patternKind: String,
            inferredClassification: String?,
            inferredAccountId: Long?,
            reasonCode: String,
            matchCount: Int,
            totalInr: Double,
            lastScannedMs: Long,
        ) {
            rows[id] = rows.getValue(id).copy(
                pattern = pattern,
                patternKind = patternKind,
                inferredClassification = inferredClassification,
                inferredAccountId = inferredAccountId,
                reasonCode = reasonCode,
                matchCount = matchCount,
                totalInr = totalInr,
                lastScannedMs = lastScannedMs,
            )
        }

        override suspend fun markDismissed(id: Long, nowMs: Long) {
            rows[id] = rows.getValue(id).copy(dismissedAtMs = nowMs)
        }

        override suspend fun clearDismissed(id: Long) {
            rows[id] = rows.getValue(id).copy(dismissedAtMs = null)
        }

        override suspend fun deleteById(id: Long) {
            rows.remove(id)
        }

        override fun observeActive(): Flow<List<RuleSuggestionEntity>> =
            flowOf(rows.values.filter { it.dismissedAtMs == null })

        override suspend fun getActive(): List<RuleSuggestionEntity> =
            rows.values.filter { it.dismissedAtMs == null }

        override suspend fun pruneStaleActive(cutoffMs: Long) {
            pruneCalls += cutoffMs
            rows.entries.removeAll { (_, v) -> v.dismissedAtMs == null && v.lastScannedMs < cutoffMs }
        }

        override suspend fun countActive(): Int =
            rows.values.count { it.dismissedAtMs == null }
    }

    private class FakeRuleDao(
        val enabledRules: List<RecipientRuleEntity> = emptyList(),
    ) : RecipientRuleDao {
        override suspend fun insert(row: RecipientRuleEntity): Long = 0
        override suspend fun update(row: RecipientRuleEntity) = Unit
        override suspend fun delete(row: RecipientRuleEntity) = Unit
        override suspend fun getById(id: Long): RecipientRuleEntity? = null
        override fun observeAll(): Flow<List<RecipientRuleEntity>> = flowOf(enabledRules)
        override suspend fun getEnabled(): List<RecipientRuleEntity> = enabledRules.filter { it.isEnabled }
        override suspend fun findByAccountId(accountId: Long): List<RecipientRuleEntity> = emptyList()
    }

    private class FakeAccountDao(
        private val accounts: List<AccountEntity> = emptyList(),
    ) : AccountDao {
        override suspend fun insert(row: AccountEntity): Long = 0
        override suspend fun update(row: AccountEntity) = Unit
        override suspend fun delete(row: AccountEntity) = Unit
        override suspend fun getById(id: Long): AccountEntity? = null
        override fun observeAll(): Flow<List<AccountEntity>> = flowOf(accounts)
        override suspend fun getAll(): List<AccountEntity> = accounts
        override suspend fun findByLast4(last4: String): List<AccountEntity> = emptyList()
        override suspend fun findByIssuerAndLast4(issuer: String, last4: String): AccountEntity? = null
        override fun observeCountByStatus(status: String): Flow<Int> = flowOf(0)
        override suspend fun bumpSeenCount(id: Long) = Unit
        override suspend fun setStatus(id: Long, status: String) = Unit
    }

    @Suppress("unused")
    private val unusedSmsLog: SmsLogEntity? = null
}
