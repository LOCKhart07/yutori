package com.yutori.database.mappers

import com.yutori.database.entities.TransactionSourceEntity
import com.yutori.transactions.SourceRole
import com.yutori.transactions.TransactionSource

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
