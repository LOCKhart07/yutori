package com.yutori.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yutori.database.entities.TransactionSourceEntity

@Dao
interface TransactionSourceDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: TransactionSourceEntity)

    @Update
    suspend fun update(row: TransactionSourceEntity)

    @Query(
        """
        SELECT * FROM transaction_sms_sources
         WHERE transaction_id = :transactionId
        """,
    )
    suspend fun findByTransactionId(transactionId: Long): List<TransactionSourceEntity>

    @Query(
        """
        SELECT * FROM transaction_sms_sources
         WHERE sms_log_id = :smsLogId
        """,
    )
    suspend fun findBySmsLogId(smsLogId: Long): List<TransactionSourceEntity>

    @Query(
        """
        SELECT * FROM transaction_sms_sources
         WHERE transaction_id = :transactionId
           AND is_primary = 1
         LIMIT 1
        """,
    )
    suspend fun findPrimary(transactionId: Long): TransactionSourceEntity?

    /** Reset primary flag — used when promoting a new source. */
    @Query(
        """
        UPDATE transaction_sms_sources
           SET is_primary = 0
         WHERE transaction_id = :transactionId
        """,
    )
    suspend fun clearPrimary(transactionId: Long)

    @Query(
        """
        DELETE FROM transaction_sms_sources
         WHERE transaction_id = :transactionId
        """,
    )
    suspend fun deleteAllForTransaction(transactionId: Long)
}
