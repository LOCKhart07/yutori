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
import com.spendwise.transactions.MonthKeyComputer
import com.spendwise.ui.AccountDraft
import com.spendwise.ui.AccountEditScreen
import com.spendwise.ui.AccountsScreen
import com.spendwise.ui.BudgetSetupScreen
import com.spendwise.ui.CardDrillDownScreen
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
    data object BudgetSetup : Screen
    data class CategoryDrill(val category: String) : Screen
    data class CardDrill(val last4: String) : Screen
    data class TransactionDetail(val transactionId: Long) : Screen
    data object Settings : Screen
    data object Accounts : Screen
    data class AccountEdit(val accountId: Long?) : Screen       // null = new
    data object RecipientRules : Screen
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

    var screen: Screen by remember { mutableStateOf(Screen.Dashboard) }
    var importDialogOpen by remember { mutableStateOf(false) }

    // Route the system back button to our Screen state instead of
    // letting Android finish the activity. Back from any non-root
    // screen pops to its parent; back from Dashboard is disabled so
    // Android's default (exit app) takes over.
    androidx.activity.compose.BackHandler(enabled = screen != Screen.Dashboard) {
        screen = when (val s = screen) {
            is Screen.Dashboard          -> Screen.Dashboard  // disabled — won't fire
            is Screen.BudgetSetup        -> Screen.Dashboard
            is Screen.CategoryDrill      -> Screen.Dashboard
            is Screen.CardDrill          -> Screen.Dashboard
            is Screen.TransactionDetail  -> Screen.Dashboard  // v1 MVP has no tx-nav stack
            is Screen.Settings           -> Screen.Dashboard
            is Screen.Accounts           -> Screen.Settings
            is Screen.AccountEdit        -> Screen.Accounts
            is Screen.RecipientRules     -> Screen.Settings
        }
    }

    val currentMonthKey = remember {
        MonthKeyComputer.ofDevice(System.currentTimeMillis())
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

            DashboardScreen(
                state = state,
                importStatus = importStatus,
                onSetBudget = { screen = Screen.BudgetSetup },
                onImport = { importDialogOpen = true },
                onSettings = { screen = Screen.Settings },
                onCategoryClick = { cat -> screen = Screen.CategoryDrill(cat) },
                onCardClick = { last4 -> screen = Screen.CardDrill(last4) },
                hasSettingsBadge = suggestedCount > 0,
                onMonthPrev = { viewModel.navigateMonth(-1) },
                onMonthNext = { viewModel.navigateMonth(1) },
                onResetMonth = { viewModel.resetToCurrentMonth() },
                isCurrentMonth = viewModel.isCurrentMonth(viewedMonthKey),
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
                currentMonthKey,
            ) {
                value = budgetDao.getByMonth(currentMonthKey)
            }
            val currentBudget = currentBudgetEntity?.let(BudgetMapper::toDomain)

            BudgetSetupScreen(
                monthKey = currentMonthKey,
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
                        screen = Screen.Dashboard
                    }
                },
                onCancel = { screen = Screen.Dashboard },
            )
        }

        is Screen.CategoryDrill -> {
            CategoryDrillDownScreen(
                monthKey = currentMonthKey,
                category = s.category,
                transactionsFlow = database.transactionDao()
                    .observeByMonthAndCategory(currentMonthKey, s.category),
                onBack = { screen = Screen.Dashboard },
                onTransactionClick = { id -> screen = Screen.TransactionDetail(id) },
            )
        }

        is Screen.CardDrill -> {
            // Resolve accountId for this last4 (if registered) so we can use
            // the account-scoped query. Falls back to filtering all month
            // transactions by last4 when no account is registered.
            val accountDao = database.accountDao()
            val resolvedAccountId: Long? by produceState<Long?>(null, s.last4) {
                val match = accountDao.findByLast4(s.last4).firstOrNull()
                value = match?.id
            }
            val issuer: String? by produceState<String?>(null, s.last4) {
                val match = accountDao.findByLast4(s.last4).firstOrNull()
                value = match?.issuer
            }

            val txDao = database.transactionDao()
            val flow = if (resolvedAccountId != null) {
                txDao.observeByMonthAndAccount(currentMonthKey, resolvedAccountId!!)
            } else {
                // No registered account — show all transactions this month
                // with matching last4 by filtering the broader flow.
                txDao.observeByMonth(currentMonthKey)
                    .map { list -> list.filter { it.last4 == s.last4 } }
            }

            CardDrillDownScreen(
                monthKey = currentMonthKey,
                last4 = s.last4,
                issuerLabel = issuer,
                transactionsFlow = flow,
                onBack = { screen = Screen.Dashboard },
                onTransactionClick = { id -> screen = Screen.TransactionDetail(id) },
            )
        }

        is Screen.TransactionDetail -> {
            TransactionDetailScreen(
                transactionId = s.transactionId,
                transactionDao = database.transactionDao(),
                sourceDao = database.transactionSourceDao(),
                smsLogDao = database.smsLogDao(),
                onBack = {
                    // Back to dashboard for v1 MVP — no history stack yet.
                    screen = Screen.Dashboard
                },
            )
        }

        is Screen.Settings -> {
            val suggestedCount: Int by database.accountDao()
                .observeCountByStatus("SUGGESTED")
                .collectAsStateWithLifecycle(initialValue = 0)
            SettingsScreen(
                onBack = { screen = Screen.Dashboard },
                onAccounts = { screen = Screen.Accounts },
                onRecipientRules = { screen = Screen.RecipientRules },
                accountSuggestionCount = suggestedCount,
            )
        }

        is Screen.Accounts -> {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val accountDao = database.accountDao()
            AccountsScreen(
                accountsFlow = accountDao.observeAll(),
                onBack = { screen = Screen.Settings },
                onAdd = { screen = Screen.AccountEdit(accountId = null) },
                onEdit = { id -> screen = Screen.AccountEdit(accountId = id) },
                onConfirmSuggestion = { id ->
                    // Reuse the edit form so the user can tweak display
                    // name + default-spend before committing. Status
                    // flips to CONFIRMED on Save (see persistAccount).
                    screen = Screen.AccountEdit(accountId = id)
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
                            screen = Screen.Accounts
                        }
                    },
                    onCancel = { screen = Screen.Accounts },
                    onDelete = if (s.accountId != null) {
                        {
                            scope.launch {
                                ruleDao.findByAccountId(s.accountId)
                                    .forEach { ruleDao.delete(it) }
                                accountDao.getById(s.accountId)?.let { accountDao.delete(it) }
                                screen = Screen.Accounts
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
                onBack = { screen = Screen.Settings },
                onToggleEnabled = { rule, enabled ->
                    scope.launch { ruleDao.update(rule.copy(isEnabled = enabled)) }
                },
                onDeleteUserRule = { rule ->
                    scope.launch { ruleDao.delete(rule) }
                },
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
