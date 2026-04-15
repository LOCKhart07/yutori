package com.spendwise.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spendwise.database.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: TransactionEntity): Long

    @Update
    suspend fun update(row: TransactionEntity)

    @Delete
    suspend fun delete(row: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
         WHERE month_key = :monthKey
         ORDER BY occurred_at_ms DESC
        """,
    )
    fun observeByMonth(monthKey: String): Flow<List<TransactionEntity>>

    /**
     * All money-moving transactions from months strictly before
     * [monthKey]. Needed by BudgetCalculator.carryOver to compute per-
     * prior-month surplus/deficit. Drops and incomes don't affect
     * carry so we filter them at the query to keep the payload small.
     */
    @Query(
        """
        SELECT * FROM transactions
         WHERE month_key < :monthKey
           AND budget_effect IN ('SPEND', 'REFUND')
        """,
    )
    suspend fun getBeforeMonth(monthKey: String): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
         WHERE month_key = :monthKey
           AND account_id = :accountId
         ORDER BY occurred_at_ms DESC
        """,
    )
    fun observeByMonthAndAccount(
        monthKey: String,
        accountId: Long,
    ): Flow<List<TransactionEntity>>

    /**
     * Dashboard's per-category bucketing coalesces null category → OTHER
     * (legacy rows from before the Categorizer always assigned a non-
     * null bucket). Mirror that here so drill-down totals match the
     * parent tile: when [category] is 'OTHER', include category IS NULL.
     */
    @Query(
        """
        SELECT * FROM transactions
         WHERE month_key = :monthKey
           AND (
             category = :category OR
             (:category = 'OTHER' AND category IS NULL)
           )
           AND budget_effect IN ('SPEND', 'REFUND')
         ORDER BY occurred_at_ms DESC
        """,
    )
    fun observeByMonthAndCategory(
        monthKey: String,
        category: String,
    ): Flow<List<TransactionEntity>>

    /**
     * Dedup-window candidates for §4.2. Returns transactions within
     * [windowMs] of [occurredAtMs] whose INR amount is within
     * [tolerance] of [inrAmount] and whose budget effect matches.
     */
    @Query(
        """
        SELECT * FROM transactions
         WHERE budget_effect = :effect
           AND inr_amount IS NOT NULL
           AND ABS(inr_amount - :inrAmount) <= :tolerance
           AND ABS(occurred_at_ms - :occurredAtMs) <= :windowMs
        """,
    )
    suspend fun findDedupCandidates(
        effect: String,
        inrAmount: Double,
        tolerance: Double,
        occurredAtMs: Long,
        windowMs: Long,
    ): List<TransactionEntity>

    /** Rows still awaiting forex rate fetch. */
    @Query("SELECT * FROM transactions WHERE rate_source = 'pending'")
    fun observePendingForex(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(inr_amount), 0.0) FROM transactions
         WHERE month_key = :monthKey
           AND budget_effect = 'SPEND'
           AND inr_amount IS NOT NULL
        """,
    )
    suspend fun sumSpendForMonth(monthKey: String): Double

    @Query(
        """
        SELECT COALESCE(SUM(inr_amount), 0.0) FROM transactions
         WHERE month_key = :monthKey
           AND budget_effect = 'REFUND'
           AND inr_amount IS NOT NULL
        """,
    )
    suspend fun sumRefundsForMonth(monthKey: String): Double

    /**
     * Rows whose recorded `merchant` matches exactly and whose
     * current classification is one of the money-movement kinds that
     * a SELF_TRANSFER rule would reclassify (§2.3). Used by the
     * proactive-reclassify pass when a user adds a UPI handle to one
     * of their own accounts.
     */
    @Query(
        """
        SELECT * FROM transactions
         WHERE merchant = :merchant
           AND classification IN ('UPI_PAYMENT', 'INCOMING_CREDIT')
        """,
    )
    suspend fun findBySelfTransferCandidateMerchant(
        merchant: String,
    ): List<TransactionEntity>
}
