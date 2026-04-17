package com.yutori.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join row linking a [TransactionEntity] to its contributing
 * [SmsLogEntity] rows, per schema.md `transaction_sms_sources`.
 *
 * Composite primary key (transaction_id, sms_log_id). For each
 * transaction, exactly one source has `is_primary = true`
 * (business-logic-spec §9.3 invariant, enforced in DAO/tests).
 */
@Entity(
    tableName = "transaction_sms_sources",
    primaryKeys = ["transaction_id", "sms_log_id"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SmsLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["sms_log_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["sms_log_id"]),
    ],
)
data class TransactionSourceEntity(
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,

    @ColumnInfo(name = "sms_log_id")
    val smsLogId: Long,

    /** BANK_DEBIT | GATEWAY | CC_PAYMENT_RECEIPT | MERCHANT_ACK | DUPLICATE_NOTIF. */
    val role: String,

    @ColumnInfo(name = "is_primary")
    val isPrimary: Boolean,
)
