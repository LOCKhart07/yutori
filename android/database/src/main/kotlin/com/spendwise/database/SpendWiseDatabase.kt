package com.spendwise.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
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

        /**
         * v2 adds auto-detected account suggestions. Three new columns
         * on `accounts`; existing rows default to CONFIRMED so routing
         * keeps working untouched.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE accounts ADD COLUMN status TEXT " +
                        "NOT NULL DEFAULT 'CONFIRMED'",
                )
                db.execSQL("ALTER TABLE accounts ADD COLUMN first_seen_ms INTEGER")
                db.execSQL(
                    "ALTER TABLE accounts ADD COLUMN seen_count INTEGER " +
                        "NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * v3 makes `accounts.last4` nullable so UPI-only accounts
         * (Paytm, PhonePe, bank UPI apps) can be registered without a
         * fake last-4 (issue #6). SQLite doesn't support DROP NOT NULL,
         * so we rebuild the table: create new → copy → drop old →
         * rename → recreate the unique index on (issuer, last4).
         *
         * Unique index on (issuer, last4) keeps working — SQLite treats
         * NULLs as distinct in unique indices, so multiple UPI-only
         * accounts per issuer are allowed. Card accounts remain
         * de-duped by (issuer, last4).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE accounts_new (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`issuer` TEXT NOT NULL, " +
                        "`last4` TEXT, " +
                        "`display_name` TEXT, " +
                        "`is_default_spend` INTEGER NOT NULL, " +
                        "`created_at_ms` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL DEFAULT 'CONFIRMED', " +
                        "`first_seen_ms` INTEGER, " +
                        "`seen_count` INTEGER NOT NULL DEFAULT 0" +
                        ")",
                )
                db.execSQL(
                    "INSERT INTO accounts_new (" +
                        "id, kind, issuer, last4, display_name, " +
                        "is_default_spend, created_at_ms, status, " +
                        "first_seen_ms, seen_count" +
                        ") SELECT " +
                        "id, kind, issuer, last4, display_name, " +
                        "is_default_spend, created_at_ms, status, " +
                        "first_seen_ms, seen_count FROM accounts",
                )
                db.execSQL("DROP TABLE accounts")
                db.execSQL("ALTER TABLE accounts_new RENAME TO accounts")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_accounts_issuer_last4` ON `accounts` " +
                        "(`issuer`, `last4`)",
                )
            }
        }
    }
}
