package com.yutori.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yutori.budget.Budget
import com.yutori.budget.BudgetCalculator
import com.yutori.budget.Transaction
import com.yutori.database.dao.BudgetDao
import com.yutori.database.dao.TransactionDao
import com.yutori.database.entities.BudgetEntity
import com.yutori.database.entities.TransactionEntity
import com.yutori.transactions.MonthKeyComputer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Dashboard ViewModel.
 *
 * Pure state mapping: combines the three Flows this screen needs
 * ([budgetDao.observeByMonth], [transactionDao.observeByMonth],
 * [transactionDao.observePendingForex]) and computes a
 * [DashboardUiState].
 *
 * No business logic lives here — the math is in
 * [BudgetCalculator]. This class only orchestrates Flows and
 * bucketing for display.
 */
class DashboardViewModel(
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val hasPermissionProvider: () -> Boolean,
    nowMs: () -> Long = { System.currentTimeMillis() },
    /**
     * Where the snapshot math and entity→domain mapping runs. Default
     * is [Dispatchers.Default] so the work stays off the main thread
     * (#98); tests replace this with the test scheduler so `runTest`
     * can virtually-advance past dispatched work.
     */
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val nowMsProvider: () -> Long = nowMs
    private val currentMonthKey: String = MonthKeyComputer.ofDevice(nowMs())

    private val _viewedMonthKey = MutableStateFlow(currentMonthKey)
    val viewedMonthKey: StateFlow<String> = _viewedMonthKey.asStateFlow()

    /**
     * Earliest month_key observed across the transactions table, or
     * [currentMonthKey] when the table is empty. Drives the dashboard
     * pager's past-edge (#21) — the pager lets the user swipe forward
     * into unbounded future months but refuses to swipe past this.
     */
    val earliestMonthKey: StateFlow<String> = transactionDao.observeEarliestMonthKey()
        .map { it ?: currentMonthKey }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = currentMonthKey,
        )

    fun navigateMonth(deltaMonths: Long) {
        _viewedMonthKey.value = MonthKeyComputer.shift(_viewedMonthKey.value, deltaMonths)
    }

    /** Explicit set — used by the pager on settle so we don't have to compute a delta. */
    fun setMonth(monthKey: String) {
        _viewedMonthKey.value = monthKey
    }

    fun resetToCurrentMonth() {
        _viewedMonthKey.value = currentMonthKey
    }

    fun isCurrentMonth(monthKey: String): Boolean = monthKey == currentMonthKey

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = _viewedMonthKey
        .flatMapLatest { mk -> observeMonth(mk) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DashboardUiState.Loading,
        )

    /**
     * Per-month UI state factory for the dashboard pager (#21). Each
     * page collects its own Flow keyed by [monthKey]; pager windowing
     * naturally disposes pages that scroll out of view.
     */
    fun observeMonth(monthKey: String): Flow<DashboardUiState> =
        if (!hasPermissionProvider()) {
            flowOf(DashboardUiState.NeedsPermission)
        } else {
            combinedReadyFlow(monthKey)
                .catch { emit(DashboardUiState.Empty(monthKey, hasBudget = false)) }
        }

    private fun combinedReadyFlow(monthKey: String): Flow<DashboardUiState> {
        val txFlow = transactionDao.observeByMonth(monthKey)
        val budgetFlow = budgetDao.observeByMonth(monthKey)
        val pendingFlow = transactionDao.observePendingForex()

        // `flowOn(computationDispatcher)` moves the combine transform —
        // toUiState's Room fetches, entity→domain mapping, and the
        // snapshot math — off the main thread. That's the single
        // biggest scroll-smoothness win for #98 at 7k+ SMS scale;
        // main stays free to drive the pager's fling animation.
        return combine(txFlow, budgetFlow, pendingFlow) { txs, budget, pendingForex ->
            toUiState(monthKey, txs, budget, pendingForex.size)
        }.flowOn(computationDispatcher)
    }

    private suspend fun toUiState(
        monthKey: String,
        txEntities: List<TransactionEntity>,
        budgetEntity: BudgetEntity?,
        pendingForexCount: Int,
    ): DashboardUiState {
        // #14 budget roll-forward: when this month has no explicit
        // row, fall back to the nearest prior row's limit. Still
        // returns Empty(hasBudget=false) only when there's neither an
        // explicit nor an inherited budget AND zero transactions.
        val inheritedEntity: BudgetEntity? =
            if (budgetEntity == null) budgetDao.getLatestBefore(monthKey) else null
        val resolvedLimit: Double? =
            budgetEntity?.limitInr ?: inheritedEntity?.limitInr
        val hasBudget: Boolean = resolvedLimit != null

        if (txEntities.isEmpty() && !hasBudget) {
            return DashboardUiState.Empty(monthKey, hasBudget = false, limitInr = null)
        }
        if (txEntities.isEmpty()) {
            return DashboardUiState.Empty(
                monthKey = monthKey,
                hasBudget = true,
                limitInr = resolvedLimit,
            )
        }

        // Snapshot math uses prior-month budgets too. Load them once.
        // Inherited months never contribute a fresh (limit − net) term
        // to carry-over (§6.6), so only true explicit priors go in.
        val priorBudgetEntities = budgetDao.getAllBefore(monthKey)
        val priorBudgets: List<Budget> = priorBudgetEntities.map { it.toDomainBudget() }

        // Budget calculator operates over ALL money-moving transactions
        // (prior + this month) because carryOver walks every prior
        // month's (limit − net spend) contribution. Without priors in
        // the list, BudgetCalculator treats prior months as zero-spend
        // and the full prior limit becomes fake surplus.
        val priorTxFlat = transactionDao.getBeforeMonth(monthKey)
            .map { it.toDomainTransaction() }
        val thisMonthTxs = txEntities.map { it.toDomainTransaction() }
        val snapshot = BudgetCalculator.snapshot(
            transactions = priorTxFlat + thisMonthTxs,
            budgets = priorBudgets,
            monthKey = monthKey,
            currentMonthLimit = resolvedLimit,
        )

        return DashboardUiState.Ready(
            monthKey = monthKey,
            snapshot = snapshot,
            derived = DashboardDerived.from(snapshot, monthKey, nowMsProvider()),
            byCategory = bucketByCategory(txEntities),
            byCard = bucketByCard(txEntities),
            transactionCount = txEntities.size,
            pendingForexCount = pendingForexCount,
        )
    }

    private fun bucketByCategory(
        txs: List<TransactionEntity>,
    ): List<CategorySlice> =
        txs
            .filter { it.budgetEffect == "SPEND" && it.inrAmount != null }
            .groupBy { it.category ?: "OTHER" }
            .map { (cat, rows) ->
                CategorySlice(
                    categoryName = cat,
                    totalInr = rows.sumOf { it.inrAmount!! },
                    transactionCount = rows.size,
                )
            }
            .sortedByDescending { it.totalInr }

    /**
     * Bucket SPEND rows into one chip per *account*. Since issue #6
     * accounts can be UPI-only (no last-4), so we group by accountId
     * when it's set and fall back to last-4 grouping for rows the
     * resolver didn't tie to a registered account. Rows with neither
     * account_id nor last4 aren't user-surfaceable at the account
     * granularity and are dropped from this view.
     */
    private fun bucketByCard(
        txs: List<TransactionEntity>,
    ): List<CardChip> {
        val spend = txs.filter { it.budgetEffect == "SPEND" && it.inrAmount != null }
        val withAccount = spend.filter { it.accountId != null }
        val withoutAccount = spend.filter { it.accountId == null && it.last4 != null }

        val byAccount = withAccount
            .groupBy { it.accountId!! }
            .map { (accId, rows) ->
                CardChip(
                    accountId = accId,
                    // Multiple last4 values can legitimately share an
                    // accountId if the account is UPI-only (null) or
                    // appeared under varied surface forms — take the
                    // first non-null one for display.
                    last4 = rows.firstOrNull { it.last4 != null }?.last4,
                    issuer = rows.firstOrNull { it.issuer != null }?.issuer,
                    totalInr = rows.sumOf { it.inrAmount!! },
                    transactionCount = rows.size,
                )
            }

        val byLast4 = withoutAccount
            .groupBy { it.last4!! }
            .map { (last4, rows) ->
                CardChip(
                    accountId = null,
                    last4 = last4,
                    issuer = rows.firstOrNull { it.issuer != null }?.issuer,
                    totalInr = rows.sumOf { it.inrAmount!! },
                    transactionCount = rows.size,
                )
            }

        return (byAccount + byLast4).sortedByDescending { it.totalInr }
    }

    private fun BudgetEntity.toDomainBudget(): Budget = Budget(
        monthKey = monthKey,
        limitInr = limitInr,
        warnThresholdPct = thresholdWarnPct,
    )

    private fun TransactionEntity.toDomainTransaction(): Transaction = Transaction(
        id = id,
        monthKey = monthKey,
        inrAmount = inrAmount,
        budgetEffect = com.yutori.classifier.BudgetEffect.valueOf(budgetEffect),
        occurredAtMs = occurredAtMs,
    )

    companion object {
        private const val STOP_TIMEOUT_MS: Long = 5_000L
    }
}

/**
 * Manual [ViewModelProvider.Factory]. Hilt would remove this
 * boilerplate; for v1 MVP we keep DI explicit.
 */
class DashboardViewModelFactory(
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val hasPermissionProvider: () -> Boolean,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            "Unknown ViewModel class: $modelClass"
        }
        return DashboardViewModel(
            transactionDao = transactionDao,
            budgetDao = budgetDao,
            hasPermissionProvider = hasPermissionProvider,
        ) as T
    }
}
