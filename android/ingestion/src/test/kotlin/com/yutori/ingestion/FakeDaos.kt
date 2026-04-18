package com.yutori.ingestion

import android.database.sqlite.SQLiteConstraintException
import com.yutori.database.dao.AccountDao
import com.yutori.database.dao.BudgetAlertStateDao
import com.yutori.database.dao.BudgetDao
import com.yutori.database.dao.RecipientRuleDao
import com.yutori.database.dao.SmsLogDao
import com.yutori.database.dao.TransactionDao
import com.yutori.database.dao.TransactionSourceDao
import com.yutori.database.entities.AccountEntity
import com.yutori.database.entities.BudgetAlertStateEntity
import com.yutori.database.entities.BudgetEntity
import com.yutori.database.entities.RecipientRuleEntity
import com.yutori.database.entities.SmsLogEntity
import com.yutori.database.entities.TransactionEntity
import com.yutori.database.entities.TransactionSourceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * In-memory fakes for the Room DAOs. Cover only the methods the
 * [IngestionPipeline] actually calls in tests. If a method is added
 * to the DAO interface, these will fail to compile — which is the
 * desired forcing function.
 */

class FakeSmsLogDao : SmsLogDao {
    val all = mutableListOf<SmsLogEntity>()
    private var seq = 0L

    override suspend fun insert(row: SmsLogEntity): Long {
        if (row.androidSmsId != null && all.any { it.androidSmsId == row.androidSmsId }) {
            throw SQLiteConstraintException(
                "UNIQUE android_sms_id=${row.androidSmsId}",
            )
        }
        seq += 1
        val withId = row.copy(id = seq)
        all += withId
        return seq
    }

    override suspend fun update(row: SmsLogEntity) {
        all.replaceAll { if (it.id == row.id) row else it }
    }
    override suspend fun delete(row: SmsLogEntity) { all.removeIf { it.id == row.id } }
    override suspend fun getById(id: Long): SmsLogEntity? = all.firstOrNull { it.id == id }
    override suspend fun findByAndroidSmsId(androidSmsId: Long): SmsLogEntity? =
        all.firstOrNull { it.androidSmsId == androidSmsId }

    override suspend fun findByContentWithin(
        sender: String,
        body: String,
        minMs: Long,
        maxMs: Long,
    ): SmsLogEntity? = all.firstOrNull {
        it.sender == sender && it.body == body && it.receivedAtMs in minMs..maxMs
    }

    override suspend fun findRowsMissingAndroidSmsId(): List<SmsLogEntity> =
        all.filter { it.androidSmsId == null }

    override fun observeUnmatched(): Flow<List<SmsLogEntity>> =
        MutableStateFlow(all.filter { it.classification == "UNMATCHED" }).asStateFlow()

    override suspend fun findInRange(startMs: Long, endMs: Long): List<SmsLogEntity> =
        all.filter { it.receivedAtMs in startMs..endMs }

    override suspend fun findPurgeEligibleByAge(olderThanMs: Long): List<SmsLogEntity> =
        all.filter {
            it.receivedAtMs < olderThanMs &&
                it.classification in setOf("NON_FINANCIAL", "OTP", "BALANCE_ALERT")
        }

    override suspend fun count(): Int = all.size
    override suspend fun countByClassification(classification: String): Int =
        all.count { it.classification == classification }

    override suspend fun latestReceivedAtMs(): Long? =
        all.maxOfOrNull { it.receivedAtMs }
}

class FakeTransactionDao : TransactionDao {
    val all = mutableListOf<TransactionEntity>()
    private var seq = 0L

    override suspend fun insert(row: TransactionEntity): Long {
        seq += 1
        val withId = row.copy(id = seq)
        all += withId
        return seq
    }

    override suspend fun update(row: TransactionEntity) {
        all.replaceAll { if (it.id == row.id) row else it }
    }
    override suspend fun delete(row: TransactionEntity) { all.removeIf { it.id == row.id } }
    override suspend fun getById(id: Long): TransactionEntity? = all.firstOrNull { it.id == id }
    override suspend fun updateNote(id: Long, note: String?): Int {
        val idx = all.indexOfFirst { it.id == id }
        if (idx < 0) return 0
        all[idx] = all[idx].copy(notes = note)
        return 1
    }

    override fun observeByMonth(monthKey: String) = flowOf(
        all.filter { it.monthKey == monthKey }.sortedByDescending { it.occurredAtMs }
    )
    override suspend fun getBeforeMonth(monthKey: String): List<TransactionEntity> =
        all.filter { it.monthKey < monthKey && it.budgetEffect in setOf("SPEND", "REFUND") }
    override fun observeByMonthAndAccount(monthKey: String, accountId: Long) = flowOf(
        all.filter { it.monthKey == monthKey && it.accountId == accountId }
    )
    override fun observeByMonthAndCategory(monthKey: String, category: String) = flowOf(
        all.filter {
            it.monthKey == monthKey && it.category == category &&
                it.budgetEffect in setOf("SPEND", "REFUND")
        }
    )
    override fun observePendingForex() = flowOf(all.filter { it.rateSource == "pending" })
    override fun observeEarliestMonthKey() = flowOf(all.minOfOrNull { it.monthKey })

    override suspend fun findDedupCandidates(
        effect: String,
        inrAmount: Double,
        tolerance: Double,
        occurredAtMs: Long,
        windowMs: Long,
    ): List<TransactionEntity> =
        all.filter {
            it.budgetEffect == effect &&
                it.inrAmount != null &&
                abs(it.inrAmount!! - inrAmount) <= tolerance &&
                abs(it.occurredAtMs - occurredAtMs) <= windowMs
        }

    override suspend fun sumSpendForMonth(monthKey: String): Double =
        all.filter {
            it.monthKey == monthKey && it.budgetEffect == "SPEND" && it.inrAmount != null
        }.sumOf { it.inrAmount!! }

    override suspend fun sumRefundsForMonth(monthKey: String): Double =
        all.filter {
            it.monthKey == monthKey && it.budgetEffect == "REFUND" && it.inrAmount != null
        }.sumOf { it.inrAmount!! }

    override suspend fun findBySelfTransferCandidateMerchant(merchant: String) =
        all.filter {
            it.merchant == merchant &&
                it.classification in setOf("UPI_PAYMENT", "INCOMING_CREDIT")
        }

    override suspend fun aggregateSuggestionCandidates(
        cutoffMs: Long, threshold: Int, limit: Int,
    ): List<com.yutori.database.dao.MerchantAggregateRow> = emptyList()

    override suspend fun findByMerchantKey(merchantKey: String): List<TransactionEntity> =
        all.filter { it.merchantKey == merchantKey }

    override suspend fun findRecentUpiMerchants(limit: Int): List<String> =
        all.filter { it.classification == "UPI_PAYMENT" && it.merchant != null }
            .sortedByDescending { it.occurredAtMs }
            .mapNotNull { it.merchant }
            .distinct()
            .take(limit)

    private fun <T> flowOf(value: T): Flow<T> = MutableStateFlow(value).asStateFlow()
}

class FakeTransactionSourceDao : TransactionSourceDao {
    val all = mutableListOf<TransactionSourceEntity>()

    override suspend fun insert(row: TransactionSourceEntity) { all += row }
    override suspend fun update(row: TransactionSourceEntity) {
        all.replaceAll {
            if (it.transactionId == row.transactionId && it.smsLogId == row.smsLogId) {
                row
            } else {
                it
            }
        }
    }
    override suspend fun findByTransactionId(transactionId: Long) =
        all.filter { it.transactionId == transactionId }
    override suspend fun findBySmsLogId(smsLogId: Long) =
        all.filter { it.smsLogId == smsLogId }
    override suspend fun findPrimary(transactionId: Long) =
        all.firstOrNull { it.transactionId == transactionId && it.isPrimary }
    override suspend fun clearPrimary(transactionId: Long) {
        all.replaceAll {
            if (it.transactionId == transactionId && it.isPrimary) it.copy(isPrimary = false)
            else it
        }
    }
    override suspend fun deleteAllForTransaction(transactionId: Long) {
        all.removeIf { it.transactionId == transactionId }
    }
}

class FakeAccountDao : AccountDao {
    val all = mutableListOf<AccountEntity>()
    private var seq = 0L

    override suspend fun insert(row: AccountEntity): Long {
        seq += 1
        all += row.copy(id = seq)
        return seq
    }
    override suspend fun update(row: AccountEntity) {
        all.replaceAll { if (it.id == row.id) row else it }
    }
    override suspend fun delete(row: AccountEntity) { all.removeIf { it.id == row.id } }
    override suspend fun getById(id: Long) = all.firstOrNull { it.id == id }
    override fun observeAll(): Flow<List<AccountEntity>> =
        MutableStateFlow(all.toList()).asStateFlow()
    override suspend fun getAll(): List<AccountEntity> = all.toList()
    override suspend fun findByLast4(last4: String) = all.filter { it.last4 == last4 }
    override suspend fun findByIssuerAndLast4(issuer: String, last4: String) =
        all.firstOrNull {
            it.issuer.equals(issuer, ignoreCase = true) &&
                it.last4.equals(last4, ignoreCase = true)
        }
    override fun observeCountByStatus(status: String): Flow<Int> =
        MutableStateFlow(all.count { it.status == status }).asStateFlow()
    override suspend fun bumpSeenCount(id: Long) {
        all.replaceAll {
            if (it.id == id) it.copy(seenCount = it.seenCount + 1) else it
        }
    }
    override suspend fun setStatus(id: Long, status: String) {
        all.replaceAll { if (it.id == id) it.copy(status = status) else it }
    }
}

class FakeBudgetDao(
    private val rows: MutableList<BudgetEntity> = mutableListOf(),
) : BudgetDao {
    override suspend fun upsert(row: BudgetEntity) {
        rows.removeIf { it.monthKey == row.monthKey }
        rows += row
    }
    override suspend fun update(row: BudgetEntity) {
        rows.replaceAll { if (it.monthKey == row.monthKey) row else it }
    }
    override suspend fun delete(row: BudgetEntity) { rows.removeIf { it.monthKey == row.monthKey } }
    override suspend fun getByMonth(monthKey: String) =
        rows.firstOrNull { it.monthKey == monthKey }
    override fun observeByMonth(monthKey: String) =
        MutableStateFlow(rows.firstOrNull { it.monthKey == monthKey }).asStateFlow()
    override suspend fun getAll() = rows.sortedBy { it.monthKey }
    override suspend fun getAllBefore(monthKey: String) =
        rows.filter { it.monthKey < monthKey }.sortedBy { it.monthKey }
    override suspend fun getLatestBefore(monthKey: String) =
        rows.filter { it.monthKey < monthKey }.maxByOrNull { it.monthKey }
}

class FakeBudgetAlertStateDao(
    private val rows: MutableList<BudgetAlertStateEntity> = mutableListOf(),
) : BudgetAlertStateDao {
    override suspend fun recordFiring(row: BudgetAlertStateEntity) {
        val exists = rows.any {
            it.monthKey == row.monthKey && it.thresholdPct == row.thresholdPct
        }
        if (!exists) rows += row
    }
    override suspend fun findByMonth(monthKey: String) =
        rows.filter { it.monthKey == monthKey }
    override suspend fun firedThresholdsForMonth(monthKey: String) =
        rows.filter { it.monthKey == monthKey }.map { it.thresholdPct }

    /** For tests: inspect what's been recorded. */
    fun all(): List<BudgetAlertStateEntity> = rows.toList()
}

class FakeRecipientRuleDao : RecipientRuleDao {
    val all = mutableListOf<RecipientRuleEntity>()
    private var seq = 0L

    override suspend fun insert(row: RecipientRuleEntity): Long {
        if (row.id != 0L) {
            all += row
            return row.id
        }
        seq += 1
        all += row.copy(id = seq)
        return seq
    }
    override suspend fun update(row: RecipientRuleEntity) {
        all.replaceAll { if (it.id == row.id) row else it }
    }
    override suspend fun delete(row: RecipientRuleEntity) { all.removeIf { it.id == row.id } }
    override suspend fun getById(id: Long) = all.firstOrNull { it.id == id }
    override fun observeAll(): Flow<List<RecipientRuleEntity>> =
        MutableStateFlow(all.toList()).asStateFlow()
    override suspend fun getEnabled(): List<RecipientRuleEntity> =
        all.filter { it.isEnabled }
    override suspend fun findByAccountId(accountId: Long) =
        all.filter { it.accountId == accountId }
}
