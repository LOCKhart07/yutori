package com.spendwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.spendwise.ui.theme.SpendWiseTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendwise.classifier.AccountKind
import com.spendwise.database.entities.AccountEntity
import com.spendwise.database.entities.BudgetEntity
import com.spendwise.database.entities.RecipientRuleEntity
import com.spendwise.database.mappers.BudgetMapper
import com.spendwise.importing.HistoricalImportWorker
import com.spendwise.ui.AccountDraft
import com.spendwise.ui.AccountEditScreen
import com.spendwise.ui.AlertSettingsScreen
import com.spendwise.ui.AccountsScreen
import com.spendwise.ui.BudgetSetupScreen
import com.spendwise.ui.CardDrillDownScreen
import com.spendwise.ui.CardDrillResolution
import com.spendwise.ui.resolveCardDrill
import com.spendwise.ui.CategoryDrillDownScreen
import com.spendwise.ui.DashboardScreen
import com.spendwise.ui.DashboardUiState
import com.spendwise.ui.DashboardViewModel
import com.spendwise.ui.DashboardViewModelFactory
import com.spendwise.ui.ImportDialog
import com.spendwise.ui.ImportStatus
import com.spendwise.ui.PermissionScreen
import com.spendwise.ui.Permissions
import com.spendwise.ui.RecipientRulesScreen
import com.spendwise.ui.SettingsScreen
import com.spendwise.ui.TransactionDetailScreen
import com.spendwise.ui.importStatusFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: draw content behind status + gesture bars.
        // Status bar icon color is set inside SpendWiseTheme to follow
        // the theme (light icons on dark surface and vice versa).
        enableEdgeToEdge()
        setContent {
            SpendWiseTheme {
                AppContent()
            }
        }
    }
}

/** v1 MVP screens. Swap for Navigation-Compose when it earns its weight. */
private sealed interface Screen {
    data object Dashboard : Screen
    data class BudgetSetup(val monthKey: String) : Screen
    data class CategoryDrill(val monthKey: String, val category: String) : Screen
    /**
     * Account drill-down. [accountId] is the canonical identifier when
     * the chip represents a registered account (issue #6). [last4] is
     * a fallback for chips derived from unregistered cards. At least
     * one is non-null.
     */
    data class CardDrill(
        val monthKey: String,
        val accountId: Long?,
        val last4: String?,
    ) : Screen
    data class TransactionDetail(val transactionId: Long) : Screen
    data object Settings : Screen
    data object Accounts : Screen
    data class AccountEdit(val accountId: Long?) : Screen       // null = new
    data object RecipientRules : Screen
    data object AlertSettings : Screen
}

@Composable
private fun AppContent() {
    val app = androidx.compose.ui.platform.LocalContext.current
        .applicationContext as SpendWiseApp
    val database = app.database

    var permissionGeneration by remember { mutableStateOf(0) }
    val hasPermission = remember(permissionGeneration) {
        Permissions.hasRealtimePermission(app)
    }

    if (!hasPermission) {
        PermissionScreen(onGranted = { permissionGeneration++ })
        return
    }

    // Nav stack. Push on forward nav, pop on back. Dashboard always
    // sits at the bottom so the system back button never finishes the
    // activity from a deeper screen — it unwinds to Dashboard first.
    var navStack: List<Screen> by remember {
        mutableStateOf(listOf(Screen.Dashboard))
    }
    val screen: Screen = navStack.last()

    fun goTo(next: Screen) {
        navStack = navStack + next
    }
    fun goBack() {
        val popped = navStack.dropLast(1)
        navStack = popped.ifEmpty { listOf(Screen.Dashboard) }
    }

    var importDialogOpen by remember { mutableStateOf(false) }

    // System back button unwinds the stack; disabled on the root
    // (Dashboard) so Android's default (exit app) takes over.
    androidx.activity.compose.BackHandler(enabled = navStack.size > 1) {
        goBack()
    }

    when (val s = screen) {
        is Screen.Dashboard -> {
            val viewModel: DashboardViewModel = viewModel(
                key = "dashboard-$permissionGeneration",
                factory = DashboardViewModelFactory(
                    transactionDao = database.transactionDao(),
                    budgetDao = database.budgetDao(),
                    hasPermissionProvider = { true },
                ),
            )
            val state: DashboardUiState by viewModel.uiState.collectAsState()
            val importStatus: ImportStatus by importStatusFlow(app)
                .collectAsStateWithLifecycle(initialValue = ImportStatus.Idle)
            val suggestedCount: Int by database.accountDao()
                .observeCountByStatus("SUGGESTED")
                .collectAsStateWithLifecycle(initialValue = 0)
            val viewedMonthKey: String by viewModel.viewedMonthKey.collectAsState()

            // Re-check the runtime POST_NOTIFICATIONS grant on every
            // recomposition tick that follows a lifecycle event so we
            // pick up grant/revoke from the OS settings flow.
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            var notificationGranted by remember {
                mutableStateOf(Permissions.hasNotificationPermission(app))
            }
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        notificationGranted = Permissions.hasNotificationPermission(app)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            var notificationBannerDismissed by androidx.compose.runtime.saveable.rememberSaveable {
                mutableStateOf(false)
            }

            DashboardScreen(
                state = state,
                importStatus = importStatus,
                onSetBudget = { goTo(Screen.BudgetSetup(viewedMonthKey)) },
                onImport = { importDialogOpen = true },
                onSettings = { goTo(Screen.Settings) },
                onCategoryClick = { cat -> goTo(Screen.CategoryDrill(viewedMonthKey, cat)) },
                onCardClick = { accountId, last4 ->
                    goTo(Screen.CardDrill(viewedMonthKey, accountId, last4))
                },
                hasSettingsBadge = suggestedCount > 0,
                onMonthPrev = { viewModel.navigateMonth(-1) },
                onMonthNext = { viewModel.navigateMonth(1) },
                onResetMonth = { viewModel.resetToCurrentMonth() },
                isCurrentMonth = viewModel.isCurrentMonth(viewedMonthKey),
                showNotificationPermissionBanner = !notificationGranted &&
                    !notificationBannerDismissed,
                onOpenNotificationSettings = {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                    ).apply {
                        putExtra(
                            android.provider.Settings.EXTRA_APP_PACKAGE,
                            app.packageName,
                        )
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    app.startActivity(intent)
                },
                onDismissNotificationBanner = { notificationBannerDismissed = true },
            )

            if (importDialogOpen) {
                ImportDialog(
                    onConfirm = { sinceMs ->
                        HistoricalImportWorker.enqueue(app, sinceMs)
                        importDialogOpen = false
                    },
                    onDismiss = { importDialogOpen = false },
                )
            }
        }

        is Screen.BudgetSetup -> {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val budgetDao = database.budgetDao()

            val currentBudgetEntity: BudgetEntity? by produceState<BudgetEntity?>(
                initialValue = null,
                s.monthKey,
            ) {
                value = budgetDao.getByMonth(s.monthKey)
            }
            val currentBudget = currentBudgetEntity?.let(BudgetMapper::toDomain)

            BudgetSetupScreen(
                monthKey = s.monthKey,
                currentBudget = currentBudget,
                onSave = { budget ->
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val existing = budgetDao.getByMonth(budget.monthKey)
                        val entity = BudgetMapper.toEntity(
                            budget = budget,
                            createdAtMs = existing?.createdAtMs ?: now,
                            updatedAtMs = now,
                        )
                        budgetDao.upsert(entity)
                        goBack()
                    }
                },
                onCancel = { goBack() },
            )
        }

        is Screen.CategoryDrill -> {
            CategoryDrillDownScreen(
                monthKey = s.monthKey,
                category = s.category,
                transactionsFlow = database.transactionDao()
                    .observeByMonthAndCategory(s.monthKey, s.category),
                onBack = { goBack() },
                onTransactionClick = { id -> goTo(Screen.TransactionDetail(id)) },
            )
        }

        is Screen.CardDrill -> {
            // Three-branch resolution per issue #6:
            //   1. accountId present → account-scoped query (covers
            //      registered card accounts and UPI-only accounts).
            //   2. accountId null, last4 present → try to look up an
            //      account by last4; if found, account-scoped query,
            //      else fall back to month-filtered-by-last4.
            //   3. both null → nothing to show (nav layer shouldn't
            //      reach this but we degrade gracefully).
            val accountDao = database.accountDao()
            val txDao = database.transactionDao()

            val resolved: CardDrillResolution? by produceState<CardDrillResolution?>(
                null, s.accountId, s.last4,
            ) {
                value = resolveCardDrill(
                    accountId = s.accountId,
                    last4 = s.last4,
                    accountDao = accountDao,
                )
            }

            val r = resolved
            if (r == null) {
                LoadingPlaceholder()
            } else {
                val flow = when (r) {
                    is CardDrillResolution.ByAccount ->
                        txDao.observeByMonthAndAccount(s.monthKey, r.accountId)
                    is CardDrillResolution.ByLast4 ->
                        txDao.observeByMonth(s.monthKey)
                            .map { list -> list.filter { it.last4 == r.last4 } }
                    is CardDrillResolution.Empty ->
                        flowOf(emptyList())
                }

                CardDrillDownScreen(
                    monthKey = s.monthKey,
                    last4 = r.last4,
                    issuerLabel = r.issuer,
                    transactionsFlow = flow,
                    onBack = { goBack() },
                    onTransactionClick = { id -> goTo(Screen.TransactionDetail(id)) },
                )
            }
        }

        is Screen.TransactionDetail -> {
            TransactionDetailScreen(
                transactionId = s.transactionId,
                transactionDao = database.transactionDao(),
                sourceDao = database.transactionSourceDao(),
                smsLogDao = database.smsLogDao(),
                onBack = { goBack() },
            )
        }

        is Screen.Settings -> {
            val suggestedCount: Int by database.accountDao()
                .observeCountByStatus("SUGGESTED")
                .collectAsStateWithLifecycle(initialValue = 0)
            SettingsScreen(
                onBack = { goBack() },
                onAccounts = { goTo(Screen.Accounts) },
                onRecipientRules = { goTo(Screen.RecipientRules) },
                onAlertSettings = { goTo(Screen.AlertSettings) },
                accountSuggestionCount = suggestedCount,
            )
        }

        is Screen.Accounts -> {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val accountDao = database.accountDao()
            AccountsScreen(
                accountsFlow = accountDao.observeAll(),
                onBack = { goBack() },
                onAdd = { goTo(Screen.AccountEdit(accountId = null)) },
                onEdit = { id -> goTo(Screen.AccountEdit(accountId = id)) },
                onConfirmSuggestion = { id ->
                    // Reuse the edit form so the user can tweak display
                    // name + default-spend before committing. Status
                    // flips to CONFIRMED on Save (see persistAccount).
                    goTo(Screen.AccountEdit(accountId = id))
                },
                onIgnoreSuggestion = { id ->
                    scope.launch { accountDao.setStatus(id, "DISMISSED") }
                },
            )
        }

        is Screen.AccountEdit -> {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val accountDao = database.accountDao()
            val ruleDao = database.recipientRuleDao()
            val transactionDao = database.transactionDao()
            val ctx = androidx.compose.ui.platform.LocalContext.current

            val initialDraft: AccountDraft? by produceState<AccountDraft?>(null, s.accountId) {
                if (s.accountId == null) {
                    value = null
                    return@produceState
                }
                val entity = accountDao.getById(s.accountId) ?: return@produceState
                val linked = ruleDao.findByAccountId(s.accountId)
                    .filter { it.reclassifyAs == "SELF_TRANSFER" }
                    .map { it.pattern }
                value = AccountDraft(
                    id = entity.id,
                    kind = AccountKind.valueOf(entity.kind),
                    issuer = entity.issuer,
                    last4 = entity.last4,
                    displayName = entity.displayName,
                    isDefaultSpend = entity.isDefaultSpend,
                    upiHandles = linked,
                )
            }

            // When editing an existing account, wait for load.
            if (s.accountId != null && initialDraft == null) {
                LoadingPlaceholder()
            } else {
                AccountEditScreen(
                    initial = initialDraft,
                    onSave = { draft ->
                        scope.launch {
                            val previousHandles = initialDraft?.upiHandles?.toSet()
                                ?: emptySet()
                            val newlyAdded = draft.upiHandles.filter {
                                it !in previousHandles
                            }
                            val persistedId = persistAccount(
                                draft = draft,
                                accountDao = accountDao,
                                ruleDao = ruleDao,
                            )
                            val reclassified =
                                com.spendwise.backup.ReclassifyOnRuleAdd.forNewUpiHandles(
                                    newHandles = newlyAdded,
                                    accountId = persistedId,
                                    transactionDao = transactionDao,
                                )
                            if (reclassified > 0) {
                                android.widget.Toast.makeText(
                                    ctx,
                                    "Reclassified $reclassified past " +
                                        "transaction(s) as self-transfers.",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                            goBack()
                        }
                    },
                    onCancel = { goBack() },
                    onDelete = if (s.accountId != null) {
                        {
                            scope.launch {
                                ruleDao.findByAccountId(s.accountId)
                                    .forEach { ruleDao.delete(it) }
                                accountDao.getById(s.accountId)?.let { accountDao.delete(it) }
                                goBack()
                            }
                        }
                    } else null,
                )
            }
        }

        is Screen.RecipientRules -> {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val ruleDao = database.recipientRuleDao()

            RecipientRulesScreen(
                rulesFlow = ruleDao.observeAll(),
                onBack = { goBack() },
                onToggleEnabled = { rule, enabled ->
                    scope.launch { ruleDao.update(rule.copy(isEnabled = enabled)) }
                },
                onDeleteUserRule = { rule ->
                    scope.launch { ruleDao.delete(rule) }
                },
            )
        }

        is Screen.AlertSettings -> {
            AlertSettingsScreen(
                settings = app.impactAlertSettings,
                onBack = { goBack() },
            )
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        com.spendwise.ui.LoadingSpinner()
    }
}

/**
 * Persists an account edit atomically (best-effort — Room doesn't give
 * us a suspend transaction API here, but the sequence is ordered and
 * the FK on recipient_rules prevents dangling references if we crash
 * mid-way).
 */
private suspend fun persistAccount(
    draft: AccountDraft,
    accountDao: com.spendwise.database.dao.AccountDao,
    ruleDao: com.spendwise.database.dao.RecipientRuleDao,
): Long {
    val now = System.currentTimeMillis()
    val id: Long = if (draft.id == 0L) {
        val entity = AccountEntity(
            kind = draft.kind.name,
            issuer = draft.issuer,
            last4 = draft.last4,
            displayName = draft.displayName,
            isDefaultSpend = draft.isDefaultSpend,
            createdAtMs = now,
        )
        accountDao.insert(entity)
    } else {
        val existing = accountDao.getById(draft.id) ?: return draft.id
        accountDao.update(
            existing.copy(
                kind = draft.kind.name,
                issuer = draft.issuer,
                last4 = draft.last4,
                displayName = draft.displayName,
                isDefaultSpend = draft.isDefaultSpend,
                // Saving a SUGGESTED row through the edit form is how
                // the user confirms it.
                status = "CONFIRMED",
            ),
        )
        draft.id
    }

    // Sync linked recipient_rules: rebuild SELF_TRANSFER rules for this
    // account. Simple approach — wipe then recreate. Preserves rules
    // linked to this account whose reclassifyAs ≠ SELF_TRANSFER (none
    // today, but future-proof).
    val existing = ruleDao.findByAccountId(id)
        .filter { it.reclassifyAs == "SELF_TRANSFER" }
    existing.forEach { ruleDao.delete(it) }

    for (handle in draft.upiHandles.distinct()) {
        ruleDao.insert(
            RecipientRuleEntity(
                pattern = handle,
                patternKind = "LITERAL",
                reclassifyAs = "SELF_TRANSFER",
                accountId = id,
                source = "USER",
                isEnabled = true,
                note = "Own UPI handle for ${draft.issuer} ••${draft.last4}",
            ),
        )
    }
    return id
}
