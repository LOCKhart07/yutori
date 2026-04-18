package com.yutori.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies Room migrations against the exported schemas under
 * `database/schemas/`. Runs on device/emulator — needs
 * `./gradlew :database:connectedAndroidTest`.
 *
 * Issue #6: the v2 → v3 migration must preserve existing rows and
 * make `accounts.last4` nullable so UPI-only accounts can be
 * inserted. We also verify that the unique index on
 * (issuer, last4) is rebuilt.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        YutoriDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate_2_to_3_preserves_existing_accounts_and_allows_null_last4() {
        // --- Arrange: seed a v2 DB with a row that has a non-null last4.
        helper.createDatabase(testDbName, 2).apply {
            execSQL(
                "INSERT INTO accounts " +
                    "(id, kind, issuer, last4, display_name, is_default_spend, " +
                    " created_at_ms, status, first_seen_ms, seen_count) " +
                    "VALUES (1, 'SAVINGS', 'Kotak', 'XX0000', NULL, 1, 0, " +
                    " 'CONFIRMED', NULL, 0)",
            )
            close()
        }

        // --- Act: run the v2 → v3 migration.
        val db = helper.runMigrationsAndValidate(
            testDbName,
            3,
            /* validateDroppedTables = */ true,
            YutoriDatabase.MIGRATION_2_3,
        )

        // --- Assert: existing row survives unchanged.
        db.query(
            "SELECT id, issuer, last4, is_default_spend, status, seen_count " +
                "FROM accounts WHERE id = 1",
        ).use { cur ->
            assert(cur.moveToFirst())
            assertEquals(1L, cur.getLong(0))
            assertEquals("Kotak", cur.getString(1))
            assertEquals("XX0000", cur.getString(2))
            assertEquals(1, cur.getInt(3))
            assertEquals("CONFIRMED", cur.getString(4))
            assertEquals(0, cur.getInt(5))
        }

        // --- Assert: last4 is now nullable — UPI-only insert succeeds.
        db.execSQL(
            "INSERT INTO accounts " +
                "(id, kind, issuer, last4, display_name, is_default_spend, " +
                " created_at_ms, status, first_seen_ms, seen_count) " +
                "VALUES (2, 'SAVINGS', 'Paytm', NULL, 'UPI wallet', 0, 0, " +
                " 'CONFIRMED', NULL, 0)",
        )
        db.query("SELECT last4 FROM accounts WHERE id = 2").use { cur ->
            assert(cur.moveToFirst())
            assertNull(cur.getString(0))
        }

        // --- Assert: unique index (issuer, last4) is present.
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND " +
                "name='index_accounts_issuer_last4'",
        ).use { cur ->
            assert(cur.moveToFirst()) { "Unique index on (issuer, last4) missing" }
        }
        db.close()
    }

    @Test
    fun migrate_3_to_4_adds_rule_suggestions_table_with_indices() {
        helper.createDatabase(testDbName, 3).close()

        val db = helper.runMigrationsAndValidate(
            testDbName,
            4,
            /* validateDroppedTables = */ true,
            YutoriDatabase.MIGRATION_3_4,
        )

        // Insert + round-trip proves column set, types, and defaults.
        db.execSQL(
            "INSERT INTO rule_suggestions " +
                "(merchant_key, pattern, pattern_kind, inferred_classification, " +
                " inferred_account_id, reason_code, match_count, total_inr, " +
                " first_seen_ms, last_scanned_ms, dismissed_at_ms) " +
                "VALUES ('cheq@axisbank', 'cheq@axisbank', 'LITERAL', " +
                " 'CC_BILL_PAYMENT', NULL, 'KEYWORD_MIDDLEMAN', 4, 12500.0, " +
                " 1713420000000, 1713420000000, NULL)",
        )
        db.query(
            "SELECT merchant_key, inferred_classification, match_count, total_inr " +
                "FROM rule_suggestions",
        ).use { cur ->
            assert(cur.moveToFirst())
            assertEquals("cheq@axisbank", cur.getString(0))
            assertEquals("CC_BILL_PAYMENT", cur.getString(1))
            assertEquals(4, cur.getInt(2))
            assertEquals(12500.0, cur.getDouble(3), 0.0001)
        }

        // Unique index on merchant_key — second insert with the same key must fail.
        var collided = false
        try {
            db.execSQL(
                "INSERT INTO rule_suggestions " +
                    "(merchant_key, pattern, pattern_kind, inferred_classification, " +
                    " inferred_account_id, reason_code, match_count, total_inr, " +
                    " first_seen_ms, last_scanned_ms, dismissed_at_ms) " +
                    "VALUES ('cheq@axisbank', 'cheq@axisbank', 'LITERAL', NULL, " +
                    " NULL, 'REPEAT_NO_DEFAULT', 1, 100.0, 0, 0, NULL)",
            )
        } catch (t: Throwable) {
            collided = true
        }
        assert(collided) { "Expected unique-index collision on merchant_key" }
        db.close()
    }

    @Test
    fun migrate_4_to_5_adds_category_override_columns() {
        helper.createDatabase(testDbName, 4).close()

        val db = helper.runMigrationsAndValidate(
            testDbName,
            5,
            /* validateDroppedTables = */ true,
            YutoriDatabase.MIGRATION_4_5,
        )

        db.execSQL(
            "INSERT INTO recipient_rules " +
                "(pattern, pattern_kind, reclassify_as, assigned_category, source, is_enabled) " +
                "VALUES ('swiggy@paytm', 'LITERAL', 'UPI_PAYMENT', 'FOOD_DINING', 'USER', 1)",
        )
        db.query("SELECT assigned_category FROM recipient_rules").use { cur ->
            assert(cur.moveToFirst())
            assertEquals("FOOD_DINING", cur.getString(0))
        }

        db.execSQL(
            "INSERT INTO transactions " +
                "(classification, budget_effect, inr_amount, original_amount, original_currency, " +
                "merchant, merchant_key, category, account_id, last4, issuer, occurred_at_ms, " +
                "month_key, is_manual_entry, manually_adjusted, notes, category_override) " +
                "VALUES ('UPI_PAYMENT', 'SPEND', 100.0, NULL, 'INR', 'x@ybl', 'x@ybl', " +
                "'OTHER', NULL, NULL, NULL, 1, '2026-04', 0, 0, NULL, 1)",
        )
        db.query("SELECT category_override FROM transactions").use { cur ->
            assert(cur.moveToFirst())
            assertEquals(1, cur.getInt(0))
        }
        db.close()
    }

    @Test
    fun opening_v5_database_after_all_migrations_works() {
        // End-to-end smoke: build the Room wrapper and let it validate
        // schema identity against the exported v5 JSON.
        helper.createDatabase(testDbName, 2).close()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.databaseBuilder(ctx, YutoriDatabase::class.java, testDbName)
            .addMigrations(
                YutoriDatabase.MIGRATION_1_2,
                YutoriDatabase.MIGRATION_2_3,
                YutoriDatabase.MIGRATION_3_4,
                YutoriDatabase.MIGRATION_4_5,
            )
            .build()
        db.openHelper.writableDatabase
        db.close()
    }
}
