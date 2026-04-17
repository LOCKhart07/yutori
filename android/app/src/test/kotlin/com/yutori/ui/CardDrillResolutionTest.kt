package com.yutori.ui

import com.yutori.database.dao.AccountDao
import com.yutori.database.entities.AccountEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Resolution logic behind the dashboard's account drill-down (issue #6).
 * The three branches — by accountId, by last4 (registered), by last4
 * (unregistered fallback) — must each route to the right query and the
 * "both null" edge must not crash.
 */
class CardDrillResolutionTest {

    @Test
    fun `accountId present returns ByAccount with stored last4 and issuer`() = runTest {
        val dao = FakeAccountDao(
            listOf(acc(id = 7L, issuer = "Kotak", last4 = "XX0000")),
        )
        val res = resolveCardDrill(accountId = 7L, last4 = null, accountDao = dao)
        res.shouldBeInstanceOf<CardDrillResolution.ByAccount>()
        res.accountId shouldBe 7L
        res.last4 shouldBe "XX0000"
        res.issuer shouldBe "Kotak"
    }

    @Test
    fun `accountId present but account has no last4 yields ByAccount with null last4`() =
        runTest {
            val dao = FakeAccountDao(
                listOf(acc(id = 3L, issuer = "Paytm", last4 = null)),
            )
            val res = resolveCardDrill(accountId = 3L, last4 = null, accountDao = dao)
            res.shouldBeInstanceOf<CardDrillResolution.ByAccount>()
            res.accountId shouldBe 3L
            res.last4 shouldBe null
            res.issuer shouldBe "Paytm"
        }

    @Test
    fun `last4 present and registered resolves ByLast4 enriched with issuer`() = runTest {
        // Even when the account is registered, we stay on a last4-filter
        // query — older txs parsed before registration still have
        // account_id=null, and an account-id query would miss them.
        // The registered account is used only to enrich the header.
        val dao = FakeAccountDao(
            listOf(acc(id = 11L, issuer = "Axis", last4 = "XX1111")),
        )
        val res = resolveCardDrill(accountId = null, last4 = "XX1111", accountDao = dao)
        res.shouldBeInstanceOf<CardDrillResolution.ByLast4>()
        res.last4 shouldBe "XX1111"
        res.issuer shouldBe "Axis"
    }

    @Test
    fun `last4 present but unregistered falls back to ByLast4`() = runTest {
        val dao = FakeAccountDao(emptyList())
        val res = resolveCardDrill(accountId = null, last4 = "XX9999", accountDao = dao)
        res.shouldBeInstanceOf<CardDrillResolution.ByLast4>()
        res.last4 shouldBe "XX9999"
        res.issuer shouldBe null
    }

    @Test
    fun `both null yields Empty`() = runTest {
        val dao = FakeAccountDao(emptyList())
        val res = resolveCardDrill(accountId = null, last4 = null, accountDao = dao)
        res shouldBe CardDrillResolution.Empty
    }

    @Test
    fun `accountId wins over last4 when both provided`() = runTest {
        // If both are carried by the nav (e.g. a registered card chip),
        // accountId is the canonical identifier — last4 is ignored for
        // resolution but the chip's last4 is still surfaced via the
        // stored entity.
        val dao = FakeAccountDao(
            listOf(
                acc(id = 1L, issuer = "Kotak", last4 = "XX0000"),
                acc(id = 2L, issuer = "Other", last4 = "XX0000"), // collision
            ),
        )
        val res = resolveCardDrill(accountId = 2L, last4 = "XX0000", accountDao = dao)
        res.shouldBeInstanceOf<CardDrillResolution.ByAccount>()
        res.accountId shouldBe 2L
        res.issuer shouldBe "Other"
    }

    // --- fixtures ---

    private fun acc(id: Long, issuer: String, last4: String?) = AccountEntity(
        id = id,
        kind = "SAVINGS",
        issuer = issuer,
        last4 = last4,
        displayName = null,
        isDefaultSpend = false,
        createdAtMs = 0L,
    )

    private class FakeAccountDao(
        private val rows: List<AccountEntity>,
    ) : AccountDao {
        override suspend fun getById(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun findByLast4(last4: String) = rows.filter { it.last4 == last4 }

        // Unused on the resolution path — return safe defaults.
        override suspend fun insert(row: AccountEntity) = 0L
        override suspend fun update(row: AccountEntity) = Unit
        override suspend fun delete(row: AccountEntity) = Unit
        override fun observeAll(): Flow<List<AccountEntity>> =
            MutableStateFlow(rows).asStateFlow()
        override suspend fun getAll() = rows
        override suspend fun findByIssuerAndLast4(issuer: String, last4: String) =
            rows.firstOrNull {
                it.issuer.equals(issuer, ignoreCase = true) &&
                    it.last4?.equals(last4, ignoreCase = true) == true
            }
        override fun observeCountByStatus(status: String): Flow<Int> =
            MutableStateFlow(rows.count { it.status == status }).asStateFlow()
        override suspend fun bumpSeenCount(id: Long) = Unit
        override suspend fun setStatus(id: Long, status: String) = Unit
    }
}
