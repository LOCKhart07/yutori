package com.spendwise.backup

import android.util.Log
import com.spendwise.database.dao.AccountDao
import com.spendwise.database.dao.RecipientRuleDao
import com.spendwise.database.entities.AccountEntity
import com.spendwise.database.entities.RecipientRuleEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON backup/restore of user-owned settings — accounts and
 * recipient_rules. Transactions and sms_log are not included; those
 * belong to a separate data export (ui-spec §11).
 *
 * Format version 1 carries account links by `last4` rather than
 * `accountId` so the file round-trips across reinstalls and devices.
 */
object SettingsBackup {

    const val FORMAT_VERSION = 1
    private const val TAG = "SettingsBackup"

    data class ImportSummary(
        val accountsInserted: Int,
        val accountsSkipped: Int,
        val rulesInserted: Int,
        val rulesSkipped: Int,
        val rulesUnlinked: Int,
        val warnings: List<String>,
    )

    suspend fun exportToJson(
        accountDao: AccountDao,
        ruleDao: RecipientRuleDao,
        nowMs: Long,
    ): String {
        // Only export user-confirmed accounts. SUGGESTED / DISMISSED
        // are machine-generated proposals — re-importing them on a
        // fresh install would surface stale suggestions from another
        // moment in time.
        val accounts = accountDao.getAll().filter { it.status == "CONFIRMED" }
        val accountsById = accounts.associateBy { it.id }
        // Snapshot of enabled + disabled user rules. getEnabled() only
        // returns enabled rows; for disabled we'd need a second path —
        // but v1 MVP Settings UI can only disable, not delete seed
        // rules, so the import filter `source == "USER"` covers
        // everything a user actually owns.
        val rules = ruleDao.getEnabled().filter { it.source == "USER" }

        val accountsArr = JSONArray().apply {
            for (a in accounts) put(a.toJson())
        }
        val rulesArr = JSONArray().apply {
            for (r in rules) {
                val linkedLast4 = r.accountId?.let { accountsById[it]?.last4 }
                put(r.toJson(linkedLast4))
            }
        }

        return JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("exportedAtMs", nowMs)
            put("accounts", accountsArr)
            put("recipientRules", rulesArr)
        }.toString(2)
    }

    suspend fun importFromJson(
        json: String,
        accountDao: AccountDao,
        ruleDao: RecipientRuleDao,
        nowMs: Long,
    ): ImportSummary {
        val warnings = mutableListOf<String>()
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return ImportSummary(0, 0, 0, 0, 0,
                listOf("Couldn't parse file as SpendWise backup JSON: ${e.message}"))
        }

        val version = root.optInt("version", -1)
        if (version != FORMAT_VERSION) {
            warnings += "Unknown backup version $version (expected " +
                "$FORMAT_VERSION); attempting best-effort import."
        }

        // Existing accounts — key on (issuer-lowercase, digits-only-last4)
        // to dedup across surface variations like "XX0000" vs "0000".
        val existing = accountDao.getAll()
        val existingKey = existing.associateBy {
            it.issuer.lowercase() to it.last4.filter(Char::isDigit)
        }

        val last4ToId = mutableMapOf<String, Long>()
        for (a in existing) last4ToId[a.last4] = a.id

        var accountsInserted = 0
        var accountsSkipped = 0
        val accountsJson = root.optJSONArray("accounts") ?: JSONArray()
        for (i in 0 until accountsJson.length()) {
            val obj = accountsJson.optJSONObject(i) ?: continue
            val issuer = obj.optString("issuer").ifBlank { null }
            if (issuer == null) {
                warnings += "account[$i]: missing issuer"
                continue
            }
            val last4 = obj.optString("last4").ifBlank { null }
            if (last4 == null) {
                warnings += "account[$i]: missing last4"
                continue
            }
            val key = issuer.lowercase() to last4.filter(Char::isDigit)
            val existingRow = existingKey[key]
            if (existingRow != null) {
                accountsSkipped++
                last4ToId[last4] = existingRow.id
                continue
            }
            val id = accountDao.insert(
                AccountEntity(
                    kind = obj.optString("kind", "OTHER"),
                    issuer = issuer,
                    last4 = last4,
                    displayName = obj.optStringOrNull("displayName"),
                    isDefaultSpend = obj.optBoolean("isDefaultSpend", false),
                    createdAtMs = nowMs,
                ),
            )
            last4ToId[last4] = id
            accountsInserted++
        }

        val existingRuleKeys = ruleDao.getEnabled()
            .map { it.pattern to it.patternKind }.toMutableSet()

        var rulesInserted = 0
        var rulesSkipped = 0
        var rulesUnlinked = 0
        val rulesJson = root.optJSONArray("recipientRules") ?: JSONArray()
        for (i in 0 until rulesJson.length()) {
            val obj = rulesJson.optJSONObject(i) ?: continue
            val pattern = obj.optString("pattern").ifBlank { null }
            if (pattern == null) {
                warnings += "rule[$i]: missing pattern"
                continue
            }
            val patternKind = obj.optString("patternKind", "LITERAL")
            if ((pattern to patternKind) in existingRuleKeys) {
                rulesSkipped++
                continue
            }

            val linkedLast4 = obj.optStringOrNull("linkedAccountLast4")
            val resolvedId = linkedLast4?.let { l4 ->
                last4ToId[l4]
                    ?: existingKey.keys.firstOrNull { it.second == l4.filter(Char::isDigit) }
                        ?.let { existingKey[it]?.id }
            }
            if (linkedLast4 != null && resolvedId == null) rulesUnlinked++

            ruleDao.insert(
                RecipientRuleEntity(
                    pattern = pattern,
                    patternKind = patternKind,
                    reclassifyAs = obj.optString("reclassifyAs", "SELF_TRANSFER"),
                    accountId = resolvedId,
                    source = obj.optString("source", "USER"),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    note = obj.optStringOrNull("note"),
                ),
            )
            existingRuleKeys += pattern to patternKind
            rulesInserted++
        }

        Log.i(TAG, "Import: +$accountsInserted accounts, +$rulesInserted rules, " +
            "$accountsSkipped+$rulesSkipped skipped, unlinked=$rulesUnlinked")
        return ImportSummary(
            accountsInserted, accountsSkipped,
            rulesInserted, rulesSkipped, rulesUnlinked,
            warnings.toList(),
        )
    }

    private fun AccountEntity.toJson(): JSONObject = JSONObject().apply {
        put("kind", kind)
        put("issuer", issuer)
        put("last4", last4)
        putOpt("displayName", displayName)
        put("isDefaultSpend", isDefaultSpend)
    }

    private fun RecipientRuleEntity.toJson(linkedLast4: String?): JSONObject =
        JSONObject().apply {
            put("pattern", pattern)
            put("patternKind", patternKind)
            put("reclassifyAs", reclassifyAs)
            put("source", source)
            put("isEnabled", isEnabled)
            putOpt("note", note)
            putOpt("linkedAccountLast4", linkedLast4)
        }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        val v = optString(name, "")
        return v.ifBlank { null }
    }
}
