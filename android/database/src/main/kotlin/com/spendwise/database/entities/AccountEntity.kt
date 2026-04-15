package com.spendwise.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-registered account per schema.md `accounts` and plan §12.2.
 * Seeds shipped in code (settings-spec §2.3); user additions land here.
 */
@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["issuer", "last4"], unique = true),
    ],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** SAVINGS | CREDIT_CARD | INVESTMENT | OTHER. */
    val kind: String,

    val issuer: String,

    val last4: String,

    @ColumnInfo(name = "display_name")
    val displayName: String? = null,

    @ColumnInfo(name = "is_default_spend")
    val isDefaultSpend: Boolean = false,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,

    /** CONFIRMED | SUGGESTED | DISMISSED. CONFIRMED is the pre-v2 default. */
    @ColumnInfo(name = "status", defaultValue = "CONFIRMED")
    val status: String = "CONFIRMED",

    @ColumnInfo(name = "first_seen_ms")
    val firstSeenMs: Long? = null,

    @ColumnInfo(name = "seen_count", defaultValue = "0")
    val seenCount: Int = 0,
)
