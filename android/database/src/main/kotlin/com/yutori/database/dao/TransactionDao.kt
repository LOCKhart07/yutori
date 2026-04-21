package com.yutori.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yutori.database.entities.TransactionEntity
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

    @Query("UPDATE transactions SET notes = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?): Int

    @Query(
        "UPDATE transactions " +
            "SET category = :category, category_override = :isOverridden " +
            "WHERE id = :id",
    )
    suspend fun updateCategory(id: Long, category: String?, isOverridden: Boolean): Int

    /**
     * Per-tx classification override. `budgetEffect` is recomputed by
     * the caller (UI) since it lives in the classifier module's
     * BudgetEffectMapper, not here.
     */
    @Query(
        "UPDATE transactions " +
            "SET classification = :classification, " +
            "    budget_effect = :budgetEffect, " +
            "    classification_override = :isOverridden " +
            "WHERE id = :id",
    )
    suspend fun updateClassification(
        id: Long,
        classification: String,
        budgetEffect: String,
        isOverridden: Boolean,
    ): Int

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

    /**
     * Earliest `month_key` across all transactions, or null if the
     * table is empty. Drives the dashboard pager's past-edge (#21).
     */
    @Query("SELECT MIN(month_key) FROM transactions")
    fun observeEarliestMonthKey(): Flow<String?>

    /** Lifetime transaction count (easter-egg stats, #79). */
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countAll(): Int

    /** Number of distinct months with at least one transaction (#79). */
    @Query("SELECT COUNT(DISTINCT month_key) FROM transactions")
    suspend fun countDistinctMonths(): Int

    /** Sum of SPEND-effect `inr_amount` across all time. 0.0 if empty (#79). */
    @Query(
        """
        SELECT COALESCE(SUM(inr_amount), 0.0) FROM transactions
         WHERE budget_effect = 'SPEND'
           AND inr_amount IS NOT NULL
        """,
    )
    suspend fun sumLifetimeSpend(): Double

    /**
     * Every SPEND row's `occurred_at_ms`. Used for the lifetime zero-
     * spend-days count (#79) — compared in Kotlin against SMS receipt
     * timestamps to find days with activity but no spend.
     */
    @Query(
        """
        SELECT occurred_at_ms FROM transactions
         WHERE budget_effect = 'SPEND'
        """,
    )
    suspend fun allSpendOccurredAtMs(): List<Long>

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

    /**
     * Rule-suggestion miner source (suggestions-spec §3.2). Groups recent
     * mis-/un-classified transactions plus repeat category-gap UPI merchants
     * (SPEND/REFUND rows currently in OTHER/UNCATEGORIZED with no manual
     * category- or classification-override) by [merchant_key] and returns
     * only groups whose count meets [threshold]. Rows the user has
     * explicitly overridden are excluded — they're already handled by hand.
     * Already-covered filtering happens in Kotlin against the enabled-rule
     * list.
     */
    @Query(
        """
        SELECT merchant_key            AS merchant_key,
               COUNT(*)                AS match_count,
               COALESCE(SUM(inr_amount), 0.0) AS total_inr
          FROM transactions
         WHERE occurred_at_ms >= :cutoffMs
            AND merchant_key IS NOT NULL
            AND classification_override = 0
            AND (
              classification = 'UNMATCHED'
              OR (
                classification = 'UPI_PAYMENT'
                AND
                budget_effect IN ('SPEND', 'REFUND')
                AND category IN ('OTHER', 'UNCATEGORIZED')
                AND category_override = 0
              )
            )
         GROUP BY merchant_key
        HAVING COUNT(*) >= :threshold
         ORDER BY total_inr DESC, match_count DESC
         LIMIT :limit
        """,
    )
    suspend fun aggregateSuggestionCandidates(
        cutoffMs: Long,
        threshold: Int,
        limit: Int,
    ): List<MerchantAggregateRow>

    /**
     * Backs the suggestion review sheet's "would match these N transactions"
     * preview. Ordered newest-first.
     */
    @Query(
        """
        SELECT * FROM transactions
         WHERE merchant_key = :merchantKey
         ORDER BY occurred_at_ms DESC
        """,
    )
    suspend fun findByMerchantKey(merchantKey: String): List<TransactionEntity>

    /**
     * Source for the add/edit rule screen's Test panel
     * (settings-spec §3.6). Most-recent UPI recipients, deduped.
     */
    @Query(
        """
        SELECT DISTINCT merchant FROM transactions
         WHERE classification = 'UPI_PAYMENT'
           AND merchant IS NOT NULL
         ORDER BY occurred_at_ms DESC
         LIMIT :limit
        """,
    )
    suspend fun findRecentUpiMerchants(limit: Int): List<String>
}
