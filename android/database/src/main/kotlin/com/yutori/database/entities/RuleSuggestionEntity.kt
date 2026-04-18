package com.yutori.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Heuristic rule-creation candidate per suggestions-spec.md §2.1.
 *
 * Mined from `transactions` by [com.yutori.transactions.suggestions.SuggestionMiner].
 * Upsert is keyed on [merchantKey]; rescans bump counts without losing
 * [firstSeenMs] or [dismissedAtMs]. Accepting a row writes a
 * `recipient_rules` entry with `source = LEARNED` and deletes the
 * suggestion.
 */
@Entity(
    tableName = "rule_suggestions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["inferred_account_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["merchant_key"], unique = true),
        Index(value = ["dismissed_at_ms"]),
        Index(value = ["inferred_account_id"]),
    ],
)
data class RuleSuggestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "merchant_key")
    val merchantKey: String,

    val pattern: String,

    /** LITERAL | PREFIX | REGEX. Inference only ever emits LITERAL in v1. */
    @ColumnInfo(name = "pattern_kind")
    val patternKind: String,

    /** Null when no heuristic fired — UI renders this as an "unsure" card. */
    @ColumnInfo(name = "inferred_classification")
    val inferredClassification: String?,

    @ColumnInfo(name = "inferred_account_id")
    val inferredAccountId: Long? = null,

    /** OWN_HANDLE_SHAPE | KEYWORD_MIDDLEMAN | REPEAT_NO_DEFAULT. */
    @ColumnInfo(name = "reason_code")
    val reasonCode: String,

    @ColumnInfo(name = "match_count")
    val matchCount: Int,

    /** Sum of `transactions.inr_amount` for matching rows; 0.0 if all were null. */
    @ColumnInfo(name = "total_inr")
    val totalInr: Double,

    @ColumnInfo(name = "first_seen_ms")
    val firstSeenMs: Long,

    @ColumnInfo(name = "last_scanned_ms")
    val lastScannedMs: Long,

    /** Null = active; non-null = dismissed. Dismissed rows persist across rescans. */
    @ColumnInfo(name = "dismissed_at_ms")
    val dismissedAtMs: Long? = null,
)
