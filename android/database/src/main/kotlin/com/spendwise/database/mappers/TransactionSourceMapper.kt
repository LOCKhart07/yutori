package com.spendwise.database.mappers

import com.spendwise.database.entities.TransactionSourceEntity
import com.spendwise.transactions.SourceRole
import com.spendwise.transactions.TransactionSource

object TransactionSourceMapper {

    fun toDomain(entity: TransactionSourceEntity): TransactionSource =
        TransactionSource(
            transactionId = entity.transactionId,
            smsLogId = entity.smsLogId,
            role = SourceRole.valueOf(entity.role),
            isPrimary = entity.isPrimary,
        )

    fun toEntity(source: TransactionSource): TransactionSourceEntity =
        TransactionSourceEntity(
            transactionId = source.transactionId,
            smsLogId = source.smsLogId,
            role = source.role.name,
            isPrimary = source.isPrimary,
        )
}
