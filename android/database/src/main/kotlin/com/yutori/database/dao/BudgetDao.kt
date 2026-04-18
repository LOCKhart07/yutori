package com.yutori.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yutori.database.entities.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    /** Upsert — replace is fine for budgets since the user edits the same row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: BudgetEntity)

    @Update
    suspend fun update(row: BudgetEntity)

    @Delete
    suspend fun delete(row: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE month_key = :monthKey")
    suspend fun getByMonth(monthKey: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE month_key = :monthKey")
    fun observeByMonth(monthKey: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets ORDER BY month_key")
    suspend fun getAll(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE month_key < :monthKey ORDER BY month_key")
    suspend fun getAllBefore(monthKey: String): List<BudgetEntity>

    /**
     * Nearest prior budget row. Used for #14 — when a month has no
     * explicit row, its effective limit inherits from the most recent
     * prior row. Returns null if there is no prior row at all.
     */
    @Query(
        "SELECT * FROM budgets WHERE month_key < :monthKey " +
            "ORDER BY month_key DESC LIMIT 1",
    )
    suspend fun getLatestBefore(monthKey: String): BudgetEntity?
}
