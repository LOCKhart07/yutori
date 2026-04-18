package com.yutori.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Reclassification rule per schema.md `recipient_rules` + plan §12.2 / §12.4.
 *
 * Four built-in CC-bill-middleman seeds ship with the app; user
 * additions append to the same table. `source` distinguishes.
 */
@Entity(
    tableName = "recipient_rules",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["pattern", "pattern_kind"], unique = true),
        Index(value = ["account_id"]),
    ],
)
data class RecipientRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val pattern: String,

    /** LITERAL | PREFIX | REGEX. */
    @ColumnInfo(name = "pattern_kind")
    val patternKind: String,

    /** Target [com.yutori.parser.Classification] name. */
    @ColumnInfo(name = "reclassify_as")
    val reclassifyAs: String,

    @ColumnInfo(name = "assigned_category")
    val assignedCategory: String? = null,

    @ColumnInfo(name = "account_id")
    val accountId: Long? = null,

    /** SEED | USER | LEARNED. */
    val source: String,

    val note: String? = null,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,
)
