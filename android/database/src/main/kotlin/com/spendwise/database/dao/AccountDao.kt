package com.spendwise.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spendwise.database.entities.AccountEntity
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
}
