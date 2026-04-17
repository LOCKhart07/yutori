package com.yutori.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yutori.database.entities.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: AccountEntity): Long

    @Update
    suspend fun update(row: AccountEntity)

    @Delete
    suspend fun delete(row: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY kind, issuer, last4")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY kind, issuer, last4")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE last4 = :last4")
    suspend fun findByLast4(last4: String): List<AccountEntity>

    @Query(
        "SELECT * FROM accounts " +
            "WHERE lower(issuer) = lower(:issuer) AND lower(last4) = lower(:last4) " +
            "LIMIT 1",
    )
    suspend fun findByIssuerAndLast4(issuer: String, last4: String): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts WHERE status = :status")
    fun observeCountByStatus(status: String): Flow<Int>

    @Query("UPDATE accounts SET seen_count = seen_count + 1 WHERE id = :id")
    suspend fun bumpSeenCount(id: Long)

    @Query("UPDATE accounts SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)
}
