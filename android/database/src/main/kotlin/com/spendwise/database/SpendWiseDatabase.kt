package com.spendwise.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.spendwise.database.dao.AccountDao
import com.spendwise.database.dao.BudgetAlertStateDao
import com.spendwise.database.dao.BudgetDao
import com.spendwise.database.dao.RecipientRuleDao
import com.spendwise.database.dao.SmsLogDao
import com.spendwise.database.dao.TransactionDao
import com.spendwise.database.dao.TransactionSourceDao
import com.spendwise.database.entities.AccountEntity
import com.spendwise.database.entities.BudgetAlertStateEntity
import com.spendwise.database.entities.BudgetEntity
import com.spendwise.database.entities.RecipientRuleEntity
import com.spendwise.database.entities.SmsLogEntity
import com.spendwise.database.entities.TransactionEntity
import com.spendwise.database.entities.TransactionSourceEntity

/**
 * The SpendWise Room database. Version 1 — every future schema change
 * ships with a numbered migration (schema.md Migration posture).
 *
 * Schema JSON is exported to `database/schemas/` via the `room.schemaLocation`
 * KSP argument in [build.gradle.kts]. Keep those files under version control.
 */
@Database(
    entities = [
        SmsLogEntity::class,
        TransactionEntity::class,
        TransactionSourceEntity::class,
        AccountEntity::class,
        RecipientRuleEntity::class,
        BudgetEntity::class,
        BudgetAlertStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(EnumConverters::class)
abstract class SpendWiseDatabase : RoomDatabase() {
    abstract fun smsLogDao(): SmsLogDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transactionSourceDao(): TransactionSourceDao
    abstract fun accountDao(): AccountDao
    abstract fun recipientRuleDao(): RecipientRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun budgetAlertStateDao(): BudgetAlertStateDao

    companion object {
        const val NAME = "spendwise.db"
    }
}
