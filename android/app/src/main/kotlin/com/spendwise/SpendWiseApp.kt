package com.spendwise

import android.app.Application
import androidx.room.Room
import com.spendwise.database.SpendWiseDatabase
import com.spendwise.database.entities.RecipientRuleEntity
import com.spendwise.forex.ForexConversionWorker
import com.spendwise.importing.HistoricalImportWorker
import com.spendwise.ingestion.ContentProviderSmsInboxLookup
import com.spendwise.ingestion.IngestionCoordinator
import com.spendwise.ingestion.IngestionPipeline
import com.spendwise.ingestion.SmsLogReconciler
import com.spendwise.notifications.AndroidAlertNotifier
import com.spendwise.ui.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * App-level service locator. Initializes the Room database, wires the
 * [IngestionPipeline], and exposes it globally to the
 * [com.spendwise.ingestion.SpendWiseSmsReceiver] via
 * [IngestionCoordinator.instance].
 *
 * This is deliberately Hilt-free for v1 MVP. The receiver and
 * the app's own Compose code are the only consumers; a lazy singleton
 * with a clear boundary is enough.
 */
class SpendWiseApp : Application() {

    val database: SpendWiseDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            SpendWiseDatabase::class.java,
            SpendWiseDatabase.NAME,
        )
            .addMigrations(SpendWiseDatabase.MIGRATION_1_2)
            // No destructive fallback — user data is the point.
            .build()
    }

    val impactAlertSettings: com.spendwise.settings.ImpactAlertSettings by lazy {
        com.spendwise.settings.ImpactAlertSettings(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()

        val pipeline = IngestionPipeline(
            smsLogDao = database.smsLogDao(),
            transactionDao = database.transactionDao(),
            transactionSourceDao = database.transactionSourceDao(),
            accountDao = database.accountDao(),
            recipientRuleDao = database.recipientRuleDao(),
            budgetDao = database.budgetDao(),
            budgetAlertStateDao = database.budgetAlertStateDao(),
            impactConfigProvider = {
                val s = impactAlertSettings.get()
                com.spendwise.ingestion.ImpactConfig(
                    enabled = s.enabled,
                    thresholdPct = s.thresholdPct,
                )
            },
        )
        val notifier = AndroidAlertNotifier(applicationContext)
        IngestionCoordinator.instance = IngestionCoordinator(
            pipeline = pipeline,
            alertNotifier = notifier,
            onTransactionIngested = {
                // Prod the forex worker so pending-forex rows resolve
                // promptly (before the periodic 6h cadence or next
                // launch). Idempotent — the worker returns success fast
                // if there's nothing to resolve.
                ForexConversionWorker.enqueueOneShot(this)
            },
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Seed recipient rules on first launch.
        scope.launch {
            seedRecipientRulesIfEmpty()
        }

        // Forex: one-shot on start + periodic every 6 h. Both gated on
        // network availability via WorkManager constraints.
        ForexConversionWorker.enqueueOneShot(this)
        ForexConversionWorker.enqueuePeriodic(this)

        // Post-start reconciliation: fill in `android_sms_id` on rows
        // the broadcast receiver saved before the content provider had
        // committed (ingestion-spec §8). 3-second delay avoids
        // competing with cold-start work. Skips if READ_SMS isn't
        // granted — resumes when it is.
        scope.launch {
            delay(RECONCILE_START_DELAY_MS)
            if (!Permissions.hasSmsPermissions(applicationContext)) return@launch
            val reconciler = SmsLogReconciler(
                smsLogDao = database.smsLogDao(),
                inboxLookup = ContentProviderSmsInboxLookup(contentResolver),
            )
            runCatching { reconciler.reconcile() }
                .onSuccess { android.util.Log.i(TAG, "Reconciler: $it") }
                .onFailure { android.util.Log.w(TAG, "Reconciler failed", it) }

            // Catch-up: ingest anything received while the process was
            // in stopped state (force-stop / fresh install). No-op when
            // sms_log is empty — user has to explicitly trigger a full
            // historical import to get started with data.
            runCatching { runCatchUpImport() }
                .onFailure { android.util.Log.w(TAG, "Catch-up import failed", it) }
        }
    }

    private suspend fun runCatchUpImport() {
        val latest = database.smsLogDao().latestReceivedAtMs() ?: return
        HistoricalImportWorker.enqueueCatchUp(applicationContext, latest + 1)
        android.util.Log.i(TAG, "Catch-up import enqueued (sinceMs=${latest + 1})")
    }

    private suspend fun seedRecipientRulesIfEmpty() {
        val dao = database.recipientRuleDao()
        if (dao.getEnabled().isNotEmpty()) return

        SEED_RULES.forEach { dao.insert(it) }
    }

    private companion object {
        private const val TAG = "SpendWiseApp"
        private const val RECONCILE_START_DELAY_MS: Long = 3_000L

        // Seed list per settings-spec §3.3 — CC-bill middlemen that
        // ship with the app. Users add their own entries via Settings.
        val SEED_RULES = listOf(
            RecipientRuleEntity(
                pattern = """cred\.club[@¡]axisb""",
                patternKind = "REGEX",
                reclassifyAs = "CC_BILL_PAYMENT",
                source = "SEED",
                note = "CRED CC bill payments",
            ),
            RecipientRuleEntity(
                pattern = """.*@paytm(cc|postpaid|ccbill)""",
                patternKind = "REGEX",
                reclassifyAs = "CC_BILL_PAYMENT",
                source = "SEED",
                note = "Paytm CC rails",
            ),
            RecipientRuleEntity(
                pattern = """.*@ybl.*creditcard""",
                patternKind = "REGEX",
                reclassifyAs = "CC_BILL_PAYMENT",
                source = "SEED",
                note = "PhonePe CC",
            ),
            RecipientRuleEntity(
                pattern = """.*@okhdfcbankcc""",
                patternKind = "REGEX",
                reclassifyAs = "CC_BILL_PAYMENT",
                source = "SEED",
                note = "HDFC CC via Google Pay",
            ),
        )
    }
}
