package com.yutori

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.yutori.database.YutoriDatabase
import com.yutori.database.entities.RecipientRuleEntity
import com.yutori.forex.ForexConversionWorker
import com.yutori.importing.HistoricalImportWorker
import com.yutori.ingestion.ContentProviderSmsInboxLookup
import com.yutori.ingestion.IngestionCoordinator
import com.yutori.ingestion.IngestionPipeline
import com.yutori.ingestion.SmsLogReconciler
import com.yutori.notifications.AndroidAlertNotifier
import com.yutori.ui.Permissions
import com.yutori.ui.update.UpdateViewModel
import com.yutori.update.UpdateModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * App-level service locator. Initializes the Room database, wires the
 * [IngestionPipeline], and exposes it globally to the
 * [com.yutori.ingestion.YutoriSmsReceiver] via
 * [IngestionCoordinator.instance].
 *
 * This is deliberately Hilt-free for v1 MVP. The receiver and
 * the app's own Compose code are the only consumers; a lazy singleton
 * with a clear boundary is enough.
 */
class YutoriApp : Application() {

    /**
     * Non-null once the DB has opened successfully. Readers must
     * null-check — on migration failure this stays null and
     * [databaseError] holds the throwable. MainActivity routes to the
     * recovery screen in that case (see error-states-spec §5.1 / §5.5).
     */
    var database: YutoriDatabase? = null
        private set

    /** Non-null when DB init failed. Consumed by MainActivity's routing. */
    var databaseError: Throwable? = null
        private set

    val impactAlertSettings: com.yutori.settings.ImpactAlertSettings by lazy {
        com.yutori.settings.ImpactAlertSettings(applicationContext)
    }

    val onboardingPrefs: com.yutori.onboarding.OnboardingPrefs by lazy {
        com.yutori.onboarding.OnboardingPrefs(applicationContext)
    }

    /**
     * Process-scoped ViewModel store owned by the Application. Lets
     * [UpdateViewModel] survive Activity recreation so Settings and the
     * app-level dialog observer see the same state, and the cold-start
     * check (fired once per process from [ProcessLifecycleOwner])
     * doesn't race Activity creation.
     */
    private val appViewModelStore = ViewModelStore()

    val updateViewModel: UpdateViewModel by lazy {
        val client = UpdateModule.createHttpClient()
        ViewModelProvider(
            appViewModelStore,
            UpdateViewModel.Factory(
                repo = UpdateModule.createRepository(client),
                downloader = UpdateModule.createDownloader(client, this),
                installer = UpdateModule.createInstaller(this),
                prefs = UpdateModule.createPrefs(this),
                currentVersion = BuildConfig.VERSION_NAME,
            ),
        )[UpdateViewModel::class.java]
    }

    val feedbackViewModel: com.yutori.feedback.FeedbackViewModel by lazy {
        val reporter = com.yutori.feedback.FeedbackModule.createReporter()
        ViewModelProvider(
            appViewModelStore,
            com.yutori.feedback.FeedbackViewModel.Factory(reporter),
        )[com.yutori.feedback.FeedbackViewModel::class.java]
    }

    val suggestionsController: com.yutori.suggestions.SuggestionsController? by lazy {
        database?.let { com.yutori.suggestions.SuggestionsController(applicationContext, it) }
    }

    val aiSettingsRepository: com.yutori.ai.AiSettingsRepository by lazy {
        com.yutori.ai.AiSettingsRepository(applicationContext)
    }

    val llmEngineHolder: com.yutori.ai.LlmEngineHolder by lazy {
        com.yutori.ai.LlmEngineHolder(
            modelFileProvider = {
                val f = com.yutori.ai.ModelFiles.modelFile(applicationContext)
                f.takeIf { it.exists() && it.length() > 0 }
            },
        )
    }

    override fun onCreate() {
        super.onCreate()

        // Register the AI settings repo so ModelDownloadWorker (which
        // constructs via reflection and can't take a ctor arg) can reach
        // the same instance via AiSettingsRepositoryProvider.get(). Must
        // happen before WorkManager could plausibly run a pending worker,
        // so kept before initDatabase().
        com.yutori.ai.AiSettingsRepositoryProvider.register(aiSettingsRepository)

        // Treat pre-existing installs that already granted SMS access
        // as onboarded — the Welcome / Import / Budget steps would
        // otherwise re-fire on first launch after this version lands.
        onboardingPrefs.backfillIfPreviouslyGranted(
            Permissions.hasRealtimePermission(applicationContext),
        )

        initDatabase()
        val db = database ?: return  // short-circuit the rest of init; MainActivity shows recovery screen

        val pipeline = IngestionPipeline(
            smsLogDao = db.smsLogDao(),
            transactionDao = db.transactionDao(),
            transactionSourceDao = db.transactionSourceDao(),
            accountDao = db.accountDao(),
            recipientRuleDao = db.recipientRuleDao(),
            budgetDao = db.budgetDao(),
            budgetAlertStateDao = db.budgetAlertStateDao(),
            impactConfigProvider = {
                val s = impactAlertSettings.get()
                com.yutori.ingestion.ImpactConfig(
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
            seedRecipientRulesIfEmpty(db)
        }

        // Forex: one-shot on start + periodic every 6 h. Both gated on
        // network availability via WorkManager constraints.
        ForexConversionWorker.enqueueOneShot(this)
        ForexConversionWorker.enqueuePeriodic(this)

        // Rule-suggestion miner: daily refresh. The post-import one-shot
        // and manual Rescan hook into the same worker class.
        com.yutori.suggestions.SuggestionRescanWorker.enqueuePeriodic(this)

        // Post-start reconciliation: fill in `android_sms_id` on rows
        // the broadcast receiver saved before the content provider had
        // committed (ingestion-spec §8). 3-second delay avoids
        // competing with cold-start work. Skips if READ_SMS isn't
        // granted — resumes when it is.
        scope.launch {
            delay(RECONCILE_START_DELAY_MS)
            if (!Permissions.hasSmsPermissions(applicationContext)) return@launch
            val reconciler = SmsLogReconciler(
                smsLogDao = db.smsLogDao(),
                inboxLookup = ContentProviderSmsInboxLookup(contentResolver),
            )
            runCatching { reconciler.reconcile() }
                .onSuccess { android.util.Log.i(TAG, "Reconciler: $it") }
                .onFailure { android.util.Log.w(TAG, "Reconciler failed", it) }

            // Catch-up: ingest anything received while the process was
            // in stopped state (force-stop / fresh install). No-op when
            // sms_log is empty — user has to explicitly trigger a full
            // historical import to get started with data.
            runCatching { runCatchUpImport(db) }
                .onFailure { android.util.Log.w(TAG, "Catch-up import failed", it) }
        }

        // Fire the autoupdater cold-start check — runs once per
        // process via Application.onCreate. The VM's
        // [UpdateViewModel.onColdStartCheck] internally honors the
        // on-open toggle, the 6h debounce, and previously-dismissed
        // tags, so it's a safe no-op when nothing's due.
        updateViewModel.onColdStartCheck()
    }

    /**
     * Build the Room DB and force the first open so migrations run now,
     * not on the first query far from here. Failures are caught and
     * stashed in [databaseError] so MainActivity can route to the
     * recovery screen instead of the app crashing mid-compose.
     */
    private fun initDatabase() {
        try {
            val db = Room.databaseBuilder(
                applicationContext,
                YutoriDatabase::class.java,
                YutoriDatabase.NAME,
            )
                .addMigrations(
                    YutoriDatabase.MIGRATION_1_2,
                    YutoriDatabase.MIGRATION_2_3,
                    YutoriDatabase.MIGRATION_3_4,
                    YutoriDatabase.MIGRATION_4_5,
                )
                // No destructive fallback — user data is the point.
                .build()
            // Force the SQLite open + migration eagerly so the throwable
            // surfaces here, not from an arbitrary downstream Flow.
            db.openHelper.writableDatabase
            database = db
        } catch (t: Throwable) {
            databaseError = t
            android.util.Log.e(TAG, "DB init failed — routing to recovery screen", t)
        }
    }

    private suspend fun runCatchUpImport(db: YutoriDatabase) {
        val latest = db.smsLogDao().latestReceivedAtMs() ?: return
        HistoricalImportWorker.enqueueCatchUp(applicationContext, latest + 1)
        android.util.Log.i(TAG, "Catch-up import enqueued (sinceMs=${latest + 1})")
    }

    private suspend fun seedRecipientRulesIfEmpty(db: YutoriDatabase) {
        val dao = db.recipientRuleDao()
        if (dao.getEnabled().isNotEmpty()) return

        SEED_RULES.forEach { dao.insert(it) }
    }

    private companion object {
        private const val TAG = "YutoriApp"
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
