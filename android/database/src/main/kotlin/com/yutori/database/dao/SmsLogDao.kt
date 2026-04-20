package com.yutori.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yutori.database.entities.SmsLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {

    /**
     * Insert and return the generated row id. Use with `ABORT` so a
     * duplicate `android_sms_id` surfaces as a [android.database.sqlite
     * .SQLiteConstraintException] for the ingestion layer to handle as
     * "already stored, drop silently" per ingestion-spec §5.3.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: SmsLogEntity): Long

    @Update
    suspend fun update(row: SmsLogEntity)

    @Delete
    suspend fun delete(row: SmsLogEntity)

    @Query("SELECT * FROM sms_log WHERE id = :id")
    suspend fun getById(id: Long): SmsLogEntity?

    @Query("SELECT * FROM sms_log WHERE android_sms_id = :androidSmsId LIMIT 1")
    suspend fun findByAndroidSmsId(androidSmsId: Long): SmsLogEntity?

    /**
     * Content-level dedup: the live receiver and the historical-import
     * worker can both pick up the same SMS (one with a null androidSmsId,
     * one with a real id). A ±10 minute window on receivedAtMs avoids
     * collapsing genuinely-repeated merchant messages that differ only
     * by timestamp / UPI ref.
     */
    @Query(
        """
        SELECT * FROM sms_log
         WHERE sender = :sender
           AND body = :body
           AND received_at_ms BETWEEN :minMs AND :maxMs
         LIMIT 1
        """,
    )
    suspend fun findByContentWithin(
        sender: String,
        body: String,
        minMs: Long,
        maxMs: Long,
    ): SmsLogEntity?

    @Query(
        """
        SELECT * FROM sms_log
         WHERE android_sms_id IS NULL
         ORDER BY received_at_ms ASC
        """,
    )
    suspend fun findRowsMissingAndroidSmsId(): List<SmsLogEntity>

    @Query("SELECT * FROM sms_log WHERE classification = 'UNMATCHED' ORDER BY received_at_ms DESC")
    fun observeUnmatched(): Flow<List<SmsLogEntity>>

    @Query(
        """
        SELECT * FROM sms_log
         ORDER BY received_at_ms DESC
         LIMIT :limit
        """,
    )
    fun observeLatest(limit: Int): Flow<List<SmsLogEntity>>

    @Query(
        """
        SELECT * FROM sms_log
         WHERE received_at_ms >= :startMs
           AND received_at_ms <= :endMs
         ORDER BY received_at_ms ASC
        """,
    )
    suspend fun findInRange(startMs: Long, endMs: Long): List<SmsLogEntity>

    /** Eligible-for-purge candidates — see PurgeEligibility. */
    @Query(
        """
        SELECT * FROM sms_log
         WHERE received_at_ms < :olderThanMs
           AND classification IN ('NON_FINANCIAL', 'OTP', 'BALANCE_ALERT')
        """,
    )
    suspend fun findPurgeEligibleByAge(olderThanMs: Long): List<SmsLogEntity>

    @Query("SELECT COUNT(*) FROM sms_log")
    suspend fun count(): Int

    /**
     * Newest `received_at_ms` across all stored rows, or null if empty.
     * Used by the startup catch-up pass to set its query floor.
     */
    @Query("SELECT MAX(received_at_ms) FROM sms_log")
    suspend fun latestReceivedAtMs(): Long?

    @Query("SELECT COUNT(*) FROM sms_log WHERE classification = :classification")
    suspend fun countByClassification(classification: String): Int
}
