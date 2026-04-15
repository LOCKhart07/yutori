package com.spendwise.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Raw-capture layer per schema.md `sms_log`.
 *
 * `android_sms_id` is nullable because the [com.spendwise.parser.Parser]
 * may be called via the broadcast receiver before the content provider
 * has committed the row (see ingestion-spec §7.1). A reconciler fills
 * it in later. Multiple NULLs are allowed by SQLite's unique-index
 * semantics; non-NULL values stay unique.
 */
@Entity(
    tableName = "sms_log",
    indices = [
        Index(value = ["android_sms_id"], unique = true),
        Index(value = ["received_at_ms"]),
        Index(value = ["classification"]),
    ],
)
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "android_sms_id")
    val androidSmsId: Long?,

    val sender: String,

    val body: String,

    @ColumnInfo(name = "received_at_ms")
    val receivedAtMs: Long,

    /** One of [com.spendwise.parser.Classification] values as its name. */
    val classification: String,

    /** Name of the parser rule that fired, or null if UNMATCHED. */
    @ColumnInfo(name = "pattern_matched")
    val patternMatched: String?,

    /** SMS_REALTIME | SMS_IMPORT | STATEMENT_PDF | STATEMENT_CSV | MANUAL. */
    val source: String,

    @ColumnInfo(name = "reparsed_at_ms")
    val reparsedAtMs: Long? = null,
)
