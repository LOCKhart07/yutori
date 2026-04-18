package com.yutori.suggestions

import android.content.Context
import android.util.Log
import com.yutori.database.YutoriDatabase
import com.yutori.database.entities.RecipientRuleEntity
import com.yutori.database.entities.RuleSuggestionEntity
import com.yutori.ui.TxMatchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Glue between the Recipient rules UI and the suggestion miner / DAOs.
 * Held as a process-singleton by [com.yutori.YutoriApp] so the Rescan
 * spinner state is shared across navigation.
 *
 * Does not own a reparse trigger — accepting a suggestion inserts the
 * recipient rule but does not retroactively reclassify past transactions.
 * Retroactive reclassification lands with #30 (reparse pipeline).
 */
class SuggestionsController(private val context: Context, private val db: YutoriDatabase) {

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    suspend fun rescanNow() {
        if (_scanning.value) return
        _scanning.value = true
        try {
            val miner = SuggestionMiner(
                transactionDao = db.transactionDao(),
                ruleSuggestionDao = db.ruleSuggestionDao(),
                recipientRuleDao = db.recipientRuleDao(),
                accountDao = db.accountDao(),
            )
            withContext(Dispatchers.IO) {
                miner.runOnce(System.currentTimeMillis())
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Manual rescan failed", t)
        } finally {
            _scanning.value = false
        }
        // Belt-and-suspenders: also enqueue the worker so a background
        // run happens even if this process dies before the in-line call
        // lands something. Cheap — worker no-ops if nothing to do.
        SuggestionRescanWorker.enqueueOneShot(context)
    }

    suspend fun accept(suggestion: RuleSuggestionEntity) = withContext(Dispatchers.IO) {
        val classification = suggestion.inferredClassification ?: return@withContext
        val rule = RecipientRuleEntity(
            pattern = suggestion.pattern,
            patternKind = suggestion.patternKind,
            reclassifyAs = classification,
            accountId = suggestion.inferredAccountId,
            source = "LEARNED",
            note = null,
            isEnabled = true,
        )
        runCatching { db.recipientRuleDao().insert(rule) }
            .onFailure { Log.w(TAG, "Accept: rule insert failed", it) }
        db.ruleSuggestionDao().deleteById(suggestion.id)
    }

    suspend fun dismiss(id: Long) = withContext(Dispatchers.IO) {
        db.ruleSuggestionDao().markDismissed(id, System.currentTimeMillis())
    }

    suspend fun loadMatches(merchantKey: String): List<TxMatchRow> = withContext(Dispatchers.IO) {
        db.transactionDao().findByMerchantKey(merchantKey).map {
            TxMatchRow(
                id = it.id,
                merchant = it.merchant ?: it.merchantKey ?: "—",
                occurredAtMs = it.occurredAtMs,
                inrAmount = it.inrAmount,
                classification = it.classification,
            )
        }
    }

    companion object {
        private const val TAG = "SuggestionsController"
    }
}
