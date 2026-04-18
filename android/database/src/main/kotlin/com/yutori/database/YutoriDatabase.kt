package com.yutori.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yutori.database.dao.AccountDao
import com.yutori.database.dao.BudgetAlertStateDao
import com.yutori.database.dao.BudgetDao
import com.yutori.database.dao.RecipientRuleDao
import com.yutori.database.dao.RuleSuggestionDao
import com.yutori.database.dao.SmsLogDao
import com.yutori.database.dao.TransactionDao
import com.yutori.database.dao.TransactionSourceDao
import com.yutori.database.entities.AccountEntity
import com.yutori.database.entities.BudgetAlertStateEntity
import com.yutori.database.entities.BudgetEntity
import com.yutori.database.entities.RecipientRuleEntity
import com.yutori.database.entities.RuleSuggestionEntity
import com.yutori.database.entities.SmsLogEntity
import com.yutori.database.entities.TransactionEntity
import com.yutori.database.entities.TransactionSourceEntity

/**
 * The Yutori Room database. Version 1 — every future schema change
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
        RuleSuggestionEntity::class,
        BudgetEntity::class,
        BudgetAlertStateEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(EnumConverters::class)
abstract class YutoriDatabase : RoomDatabase() {
    abstract fun smsLogDao(): SmsLogDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transactionSourceDao(): TransactionSourceDao
    abstract fun accountDao(): AccountDao
    abstract fun recipientRuleDao(): RecipientRuleDao
    abstract fun ruleSuggestionDao(): RuleSuggestionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun budgetAlertStateDao(): BudgetAlertStateDao

    companion object {
        const val NAME = "yutori.db"

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
        /**
         * v4 adds the `rule_suggestions` table for the heuristic rule-suggestion
         * surface (suggestions-spec.md / #64 part 1). Additive — new empty table
         * on upgrade, no data transformation.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rule_suggestions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`merchant_key` TEXT NOT NULL, " +
                        "`pattern` TEXT NOT NULL, " +
                        "`pattern_kind` TEXT NOT NULL, " +
                        "`inferred_classification` TEXT, " +
                        "`inferred_account_id` INTEGER, " +
                        "`reason_code` TEXT NOT NULL, " +
                        "`match_count` INTEGER NOT NULL, " +
                        "`total_inr` REAL NOT NULL, " +
                        "`first_seen_ms` INTEGER NOT NULL, " +
                        "`last_scanned_ms` INTEGER NOT NULL, " +
                        "`dismissed_at_ms` INTEGER, " +
                        "FOREIGN KEY(`inferred_account_id`) REFERENCES `accounts`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE SET NULL" +
                        ")",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_rule_suggestions_merchant_key` " +
                        "ON `rule_suggestions` (`merchant_key`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_rule_suggestions_dismissed_at_ms` " +
                        "ON `rule_suggestions` (`dismissed_at_ms`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_rule_suggestions_inferred_account_id` " +
                        "ON `rule_suggestions` (`inferred_account_id`)",
                )
            }
        }

        /**
         * v5 adds category-override support:
         * - `recipient_rules.assigned_category` optional rule-level category.
         * - `transactions.category_override` per-row manual-override flag.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipient_rules ADD COLUMN assigned_category TEXT")
                db.execSQL(
                    "ALTER TABLE transactions " +
                        "ADD COLUMN category_override INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

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
