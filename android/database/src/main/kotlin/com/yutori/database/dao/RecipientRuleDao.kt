package com.yutori.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yutori.database.entities.RecipientRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipientRuleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: RecipientRuleEntity): Long

    @Update
    suspend fun update(row: RecipientRuleEntity)

    @Delete
    suspend fun delete(row: RecipientRuleEntity)

    @Query("SELECT * FROM recipient_rules WHERE id = :id")
    suspend fun getById(id: Long): RecipientRuleEntity?

    @Query("SELECT * FROM recipient_rules ORDER BY source, id")
    fun observeAll(): Flow<List<RecipientRuleEntity>>

    @Query("SELECT * FROM recipient_rules WHERE is_enabled = 1 ORDER BY source, id")
    suspend fun getEnabled(): List<RecipientRuleEntity>

    @Query("SELECT * FROM recipient_rules WHERE account_id = :accountId")
    suspend fun findByAccountId(accountId: Long): List<RecipientRuleEntity>
}
