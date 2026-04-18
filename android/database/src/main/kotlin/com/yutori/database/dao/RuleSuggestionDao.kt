package com.yutori.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yutori.database.entities.RuleSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleSuggestionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: RuleSuggestionEntity): Long

    @Query("SELECT * FROM rule_suggestions WHERE merchant_key = :key")
    suspend fun getByMerchantKey(key: String): RuleSuggestionEntity?

    @Query("SELECT * FROM rule_suggestions WHERE id = :id")
    suspend fun getById(id: Long): RuleSuggestionEntity?

    /**
     * Rescan update. Preserves [RuleSuggestionEntity.firstSeenMs] and
     * [RuleSuggestionEntity.dismissedAtMs] — callers compose clearDismissed /
     * markDismissed separately when resurfacing.
     */
    @Query(
        """
        UPDATE rule_suggestions
        SET pattern = :pattern,
            pattern_kind = :patternKind,
            inferred_classification = :inferredClassification,
            inferred_account_id = :inferredAccountId,
            reason_code = :reasonCode,
            match_count = :matchCount,
            total_inr = :totalInr,
            last_scanned_ms = :lastScannedMs
        WHERE id = :id
        """,
    )
    suspend fun updateOnRescan(
        id: Long,
        pattern: String,
        patternKind: String,
        inferredClassification: String?,
        inferredAccountId: Long?,
        reasonCode: String,
        matchCount: Int,
        totalInr: Double,
        lastScannedMs: Long,
    )

    @Query("UPDATE rule_suggestions SET dismissed_at_ms = :nowMs WHERE id = :id")
    suspend fun markDismissed(id: Long, nowMs: Long)

    @Query("UPDATE rule_suggestions SET dismissed_at_ms = NULL WHERE id = :id")
    suspend fun clearDismissed(id: Long)

    @Query("DELETE FROM rule_suggestions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "SELECT * FROM rule_suggestions " +
            "WHERE dismissed_at_ms IS NULL " +
            "ORDER BY total_inr DESC, match_count DESC, id ASC",
    )
    fun observeActive(): Flow<List<RuleSuggestionEntity>>

    @Query(
        "SELECT * FROM rule_suggestions " +
            "WHERE dismissed_at_ms IS NULL " +
            "ORDER BY total_inr DESC, match_count DESC, id ASC",
    )
    suspend fun getActive(): List<RuleSuggestionEntity>

    /** Pruned on every rescan; dismissed rows are kept regardless of age. */
    @Query(
        "DELETE FROM rule_suggestions " +
            "WHERE dismissed_at_ms IS NULL AND last_scanned_ms < :cutoffMs",
    )
    suspend fun pruneStaleActive(cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM rule_suggestions WHERE dismissed_at_ms IS NULL")
    suspend fun countActive(): Int
}
