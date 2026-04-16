package com.spendwise.ui

import com.spendwise.database.dao.AccountDao

/**
 * How the CardDrill nav target resolves to a transactions Flow.
 *
 * Since issue #6, the canonical identifier is [AccountEntity.id] when
 * available — it's the only id that can represent a UPI-only account
 * (no last-4). The nav layer still keeps `last4` as a fallback for
 * unregistered-card chips surfaced by the dashboard's grouping.
 */
sealed interface CardDrillResolution {
    val last4: String?
    val issuer: String?

    /** Scoped query by account id — covers registered cards and UPI-only accounts. */
    data class ByAccount(
        val accountId: Long,
        override val last4: String?,
        override val issuer: String?,
    ) : CardDrillResolution

    /** Month-wide query filtered by last-4 — for unregistered-card chips. */
    data class ByLast4(
        override val last4: String,
        override val issuer: String?,
    ) : CardDrillResolution

    /** Nothing to query — both identifiers were null (shouldn't reach UI). */
    data object Empty : CardDrillResolution {
        override val last4: String? = null
        override val issuer: String? = null
    }
}

/**
 * Resolve a [CardDrillResolution] from the nav args.
 *
 * Rules:
 *   1. If [accountId] is non-null, use it directly and enrich with the
 *      account's stored last-4 + issuer for the header.
 *   2. Else if [last4] is non-null, look up an account by last-4. If
 *      one is registered, use its id (account-scoped query). Else
 *      fall back to a month-filter-by-last4.
 *   3. Both null → [CardDrillResolution.Empty].
 */
suspend fun resolveCardDrill(
    accountId: Long?,
    last4: String?,
    accountDao: AccountDao,
): CardDrillResolution {
    if (accountId != null) {
        val entity = accountDao.getById(accountId)
        return CardDrillResolution.ByAccount(
            accountId = accountId,
            last4 = entity?.last4 ?: last4,
            issuer = entity?.issuer,
        )
    }
    if (last4 != null) {
        val match = accountDao.findByLast4(last4).firstOrNull()
        return if (match != null) {
            CardDrillResolution.ByAccount(
                accountId = match.id,
                last4 = match.last4 ?: last4,
                issuer = match.issuer,
            )
        } else {
            CardDrillResolution.ByLast4(
                last4 = last4,
                issuer = null,
            )
        }
    }
    return CardDrillResolution.Empty
}
