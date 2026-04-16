package com.spendwise.backup

import com.spendwise.database.dao.AccountDao
import com.spendwise.database.dao.RecipientRuleDao
import com.spendwise.database.entities.AccountEntity
import com.spendwise.database.entities.RecipientRuleEntity
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SettingsBackupTest {

    @Test
    fun `round-trip preserves accounts and user rules`() = runTest {
        val srcAcc = FakeAccountDao().apply {
            insert(account(kind = "CREDIT_CARD", issuer = "Axis", last4 = "3333"))
            insert(account(kind = "SAVINGS", issuer = "Kotak", last4 = "0000"))
        }
        val srcRule = FakeRecipientRuleDao().apply {
            insert(rule(pattern = "user@upi", accountId = 2, source = "USER"))
            // Seed rule — should NOT be exported (source != USER).
            insert(rule(pattern = "CRED.CLUB", accountId = null, source = "SEED"))
        }

        val json = SettingsBackup.exportToJson(srcAcc, srcRule, nowMs = 1_700_000_000_000L)

        val dstAcc = FakeAccountDao()
        val dstRule = FakeRecipientRuleDao()
        val summary = SettingsBackup.importFromJson(json, dstAcc, dstRule, nowMs = 0L)

        summary.accountsInserted shouldBe 2
        summary.accountsSkipped shouldBe 0
        summary.rulesInserted shouldBe 1
        summary.rulesUnlinked shouldBe 0

        dstAcc.all shouldHaveSize 2
        dstRule.all shouldHaveSize 1

        val rule = dstRule.all.single()
        rule.pattern shouldBe "user@upi"
        rule.source shouldBe "USER"
        // Linked by last4 — should resolve to the newly inserted Kotak id.
        val kotakId = dstAcc.all.single { it.last4 == "0000" }.id
        rule.accountId shouldBe kotakId
    }

    @Test
    fun `re-importing same backup dedups accounts and rules`() = runTest {
        val accounts = FakeAccountDao().apply {
            insert(account(issuer = "Kotak", last4 = "0000"))
        }
        val rules = FakeRecipientRuleDao().apply {
            insert(rule(pattern = "user@upi", accountId = 1, source = "USER"))
        }
        val json = SettingsBackup.exportToJson(accounts, rules, nowMs = 1L)

        // First import into fresh DB.
        val dstAcc = FakeAccountDao()
        val dstRule = FakeRecipientRuleDao()
        SettingsBackup.importFromJson(json, dstAcc, dstRule, nowMs = 0L)

        // Second import into the *same* destination — should skip all.
        val summary = SettingsBackup.importFromJson(json, dstAcc, dstRule, nowMs = 0L)
        summary.accountsInserted shouldBe 0
        summary.accountsSkipped shouldBe 1
        summary.rulesInserted shouldBe 0
        summary.rulesSkipped shouldBe 1
        dstAcc.all shouldHaveSize 1
        dstRule.all shouldHaveSize 1
    }

    @Test
    fun `dedup matches across last4 surface variations`() = runTest {
        // Destination already has Kotak with stored "XX0000"; backup has "0000".
        val dstAcc = FakeAccountDao().apply {
            insert(account(issuer = "kotak", last4 = "XX0000"))
        }
        val dstRule = FakeRecipientRuleDao()

        val backup = """
          {"version":1,"exportedAtMs":0,
           "accounts":[
             {"kind":"SAVINGS","issuer":"Kotak","last4":"0000","isDefaultSpend":false}
           ],
           "recipientRules":[]}
        """.trimIndent()

        val summary = SettingsBackup.importFromJson(backup, dstAcc, dstRule, nowMs = 0L)
        summary.accountsInserted shouldBe 0
        summary.accountsSkipped shouldBe 1
        dstAcc.all shouldHaveSize 1
    }

    @Test
    fun `malformed JSON returns summary with warning`() = runTest {
        val dstAcc = FakeAccountDao()
        val dstRule = FakeRecipientRuleDao()
        val summary = SettingsBackup.importFromJson("not-json", dstAcc, dstRule, nowMs = 0L)
        summary.accountsInserted shouldBe 0
        summary.rulesInserted shouldBe 0
        summary.warnings shouldHaveSize 1
        summary.warnings.single().lowercase() shouldContain "parse"
    }

    @Test
    fun `accounts missing issuer are skipped - missing last4 allowed as UPI-only`() = runTest {
        // Since issue #6, `last4` is optional (UPI-only accounts exist).
        // Only `issuer` remains required — rows without it are still
        // skipped with a warning.
        val dstAcc = FakeAccountDao()
        val dstRule = FakeRecipientRuleDao()
        val backup = """
          {"version":1,
           "accounts":[
             {"kind":"SAVINGS","issuer":"Kotak"},
             {"kind":"SAVINGS","last4":"0000"},
             {"kind":"SAVINGS","issuer":"ICICI","last4":"1111"}
           ],
           "recipientRules":[]}
        """.trimIndent()
        val summary = SettingsBackup.importFromJson(backup, dstAcc, dstRule, nowMs = 0L)
        // Kotak (UPI-only) + ICICI inserted; the issuer-less row warns.
        summary.accountsInserted shouldBe 2
        summary.warnings shouldHaveSize 1
        dstAcc.all.map { it.issuer }.toSet() shouldBe setOf("Kotak", "ICICI")
        dstAcc.all.single { it.issuer == "Kotak" }.last4 shouldBe null
    }

    @Test
    fun `UPI-only accounts round-trip through export and import`() = runTest {
        // Issue #6: exporting an account with null last4 and re-importing
        // it must preserve the null and not duplicate on a second import.
        val srcAcc = FakeAccountDao().apply {
            insert(account(issuer = "Paytm", last4 = null))
        }
        val srcRule = FakeRecipientRuleDao()
        val json = SettingsBackup.exportToJson(srcAcc, srcRule, nowMs = 0L)

        val dstAcc = FakeAccountDao()
        val dstRule = FakeRecipientRuleDao()
        val first = SettingsBackup.importFromJson(json, dstAcc, dstRule, nowMs = 0L)
        first.accountsInserted shouldBe 1
        dstAcc.all.single().last4 shouldBe null

        // Second import against the same DB — should dedup, not duplicate.
        val second = SettingsBackup.importFromJson(json, dstAcc, dstRule, nowMs = 0L)
        second.accountsInserted shouldBe 0
        second.accountsSkipped shouldBe 1
        dstAcc.all shouldHaveSize 1
    }

    @Test
    fun `rule with linkedAccountLast4 that doesn't exist is imported unlinked`() = runTest {
        val dstAcc = FakeAccountDao()
        val dstRule = FakeRecipientRuleDao()
        val backup = """
          {"version":1,
           "accounts":[],
           "recipientRules":[
             {"pattern":"nobody@upi","patternKind":"LITERAL",
              "reclassifyAs":"SELF_TRANSFER","source":"USER",
              "isEnabled":true,"linkedAccountLast4":"9999"}
           ]}
        """.trimIndent()
        val summary = SettingsBackup.importFromJson(backup, dstAcc, dstRule, nowMs = 0L)
        summary.rulesInserted shouldBe 1
        summary.rulesUnlinked shouldBe 1
        val inserted = dstRule.all.single()
        inserted.accountId shouldBe null
        inserted.pattern shouldBe "nobody@upi"
    }

    @Test
    fun `unknown version emits warning but still imports`() = runTest {
        val dstAcc = FakeAccountDao()
        val dstRule = FakeRecipientRuleDao()
        val backup = """
          {"version":99,
           "accounts":[
             {"kind":"SAVINGS","issuer":"Kotak","last4":"0000"}
           ],
           "recipientRules":[]}
        """.trimIndent()
        val summary = SettingsBackup.importFromJson(backup, dstAcc, dstRule, nowMs = 0L)
        summary.accountsInserted shouldBe 1
        summary.warnings.shouldNotBeNull()
        summary.warnings.any { "version" in it.lowercase() } shouldBe true
    }

    @Test
    fun `rule pattern missing is skipped with warning`() = runTest {
        val dstAcc = FakeAccountDao()
        val dstRule = FakeRecipientRuleDao()
        val backup = """
          {"version":1,"accounts":[],
           "recipientRules":[
             {"patternKind":"LITERAL","reclassifyAs":"SELF_TRANSFER","source":"USER"}
           ]}
        """.trimIndent()
        val summary = SettingsBackup.importFromJson(backup, dstAcc, dstRule, nowMs = 0L)
        summary.rulesInserted shouldBe 0
        summary.warnings shouldHaveSize 1
        dstRule.all.shouldHaveSize(0)
    }

    // ---- fixtures ----

    private fun account(
        kind: String = "SAVINGS",
        issuer: String = "Kotak",
        last4: String? = "0000",
        isDefaultSpend: Boolean = false,
    ) = AccountEntity(
        kind = kind,
        issuer = issuer,
        last4 = last4,
        displayName = null,
        isDefaultSpend = isDefaultSpend,
        createdAtMs = 0L,
    )

    private fun rule(
        pattern: String,
        accountId: Long?,
        source: String,
    ) = RecipientRuleEntity(
        pattern = pattern,
        patternKind = "LITERAL",
        reclassifyAs = "SELF_TRANSFER",
        accountId = accountId,
        source = source,
        note = null,
        isEnabled = true,
    )

    // ---- fakes ----

    class FakeAccountDao : AccountDao {
        val all = mutableListOf<AccountEntity>()
        private var seq = 0L

        override suspend fun insert(row: AccountEntity): Long {
            seq += 1
            all += row.copy(id = seq)
            return seq
        }
        override suspend fun update(row: AccountEntity) {
            all.replaceAll { if (it.id == row.id) row else it }
        }
        override suspend fun delete(row: AccountEntity) { all.removeIf { it.id == row.id } }
        override suspend fun getById(id: Long) = all.firstOrNull { it.id == id }
        override fun observeAll(): Flow<List<AccountEntity>> =
            MutableStateFlow(all.toList()).asStateFlow()
        override suspend fun getAll(): List<AccountEntity> = all.toList()
        override suspend fun findByLast4(last4: String) = all.filter { it.last4 == last4 }
        override suspend fun findByIssuerAndLast4(issuer: String, last4: String) =
            all.firstOrNull {
                it.issuer.equals(issuer, ignoreCase = true) &&
                    it.last4.equals(last4, ignoreCase = true)
            }
        override fun observeCountByStatus(status: String): Flow<Int> =
            MutableStateFlow(all.count { it.status == status }).asStateFlow()
        override suspend fun bumpSeenCount(id: Long) {
            all.replaceAll {
                if (it.id == id) it.copy(seenCount = it.seenCount + 1) else it
            }
        }
        override suspend fun setStatus(id: Long, status: String) {
            all.replaceAll { if (it.id == id) it.copy(status = status) else it }
        }
    }

    class FakeRecipientRuleDao : RecipientRuleDao {
        val all = mutableListOf<RecipientRuleEntity>()
        private var seq = 0L

        override suspend fun insert(row: RecipientRuleEntity): Long {
            if (row.id != 0L) { all += row; return row.id }
            seq += 1
            all += row.copy(id = seq)
            return seq
        }
        override suspend fun update(row: RecipientRuleEntity) {
            all.replaceAll { if (it.id == row.id) row else it }
        }
        override suspend fun delete(row: RecipientRuleEntity) {
            all.removeIf { it.id == row.id }
        }
        override suspend fun getById(id: Long) = all.firstOrNull { it.id == id }
        override fun observeAll(): Flow<List<RecipientRuleEntity>> =
            MutableStateFlow(all.toList()).asStateFlow()
        override suspend fun getEnabled() = all.filter { it.isEnabled }
        override suspend fun findByAccountId(accountId: Long) =
            all.filter { it.accountId == accountId }
    }
}
