package com.yutori.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yutori.database.entities.BudgetAlertStateEntity

@Dao
interface BudgetAlertStateDao {

    /** IGNORE on conflict — the §7.2 "fires at most once" invariant. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordFiring(row: BudgetAlertStateEntity)

    @Query("SELECT * FROM budget_alert_state WHERE month_key = :monthKey")
    suspend fun findByMonth(monthKey: String): List<BudgetAlertStateEntity>

    @Query(
        """
        SELECT threshold_pct FROM budget_alert_state
         WHERE month_key = :monthKey
        """,
    )
    suspend fun firedThresholdsForMonth(monthKey: String): List<Int>
}
