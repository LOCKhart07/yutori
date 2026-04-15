package com.spendwise.database.mappers

import com.spendwise.classifier.Account
import com.spendwise.classifier.AccountKind
import com.spendwise.classifier.AccountStatus
import com.spendwise.database.entities.AccountEntity

object AccountMapper {

    fun toDomain(entity: AccountEntity): Account = Account(
        id = entity.id,
        kind = AccountKind.valueOf(entity.kind),
        issuer = entity.issuer,
        last4 = entity.last4,
        displayName = entity.displayName,
        isDefaultSpend = entity.isDefaultSpend,
        status = runCatching { AccountStatus.valueOf(entity.status) }
            .getOrDefault(AccountStatus.CONFIRMED),
    )

    fun toEntity(account: Account, createdAtMs: Long): AccountEntity = AccountEntity(
        id = account.id,
        kind = account.kind.name,
        issuer = account.issuer,
        last4 = account.last4,
        displayName = account.displayName,
        isDefaultSpend = account.isDefaultSpend,
        createdAtMs = createdAtMs,
        status = account.status.name,
    )
}
