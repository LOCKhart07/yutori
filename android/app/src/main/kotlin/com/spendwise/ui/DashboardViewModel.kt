package com.spendwise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spendwise.budget.Budget
import com.spendwise.budget.BudgetCalculator
import com.spendwise.budget.Transaction
import com.spendwise.database.dao.BudgetDao
import com.spendwise.database.dao.TransactionDao
import com.spendwise.database.entities.BudgetEntity
import com.spendwise.database.entities.TransactionEntity
import com.spendwise.transactions.MonthKeyComputer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOf

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
) : ViewModel() {

    private val nowMsProvider: () -> Long = nowMs
    private val currentMonthKey: String = MonthKeyComputer.ofDevice(nowMs())

    private val _viewedMonthKey = MutableStateFlow(currentMonthKey)
    val viewedMonthKey: StateFlow<String> = _viewedMonthKey.asStateFlow()

    fun navigateMonth(deltaMonths: Long) {
        _viewedMonthKey.value = MonthKeyComputer.shift(_viewedMonthKey.value, deltaMonths)
    }

    fun resetToCurrentMonth() {
        _viewedMonthKey.value = currentMonthKey
    }

    fun isCurrentMonth(monthKey: String): Boolean = monthKey == currentMonthKey

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = run {
        val hasPerm = hasPermissionProvider()
        if (!hasPerm) {
            flowOf<DashboardUiState>(DashboardUiState.NeedsPermission)
        } else {
            _viewedMonthKey.flatMapLatest { mk -> combinedReadyFlow(mk) }
        }
            .catch { emit(DashboardUiState.Empty(_viewedMonthKey.value, hasBudget = false)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DashboardUiState.Loading,
            )
    }

    private fun combinedReadyFlow(monthKey: String): Flow<DashboardUiState> {
        val txFlow = transactionDao.observeByMonth(monthKey)
        val budgetFlow = budgetDao.observeByMonth(monthKey)
        val pendingFlow = transactionDao.observePendingForex()

        return combine(txFlow, budgetFlow, pendingFlow) { txs, budget, pendingForex ->
            toUiState(monthKey, txs, budget, pendingForex.size)
        }
    }

    private suspend fun toUiState(
        monthKey: String,
        txEntities: List<TransactionEntity>,
        budgetEntity: BudgetEntity?,
        pendingForexCount: Int,
    ): DashboardUiState {
        if (txEntities.isEmpty() && budgetEntity == null) {
            return DashboardUiState.Empty(monthKey, hasBudget = false)
        }
        if (txEntities.isEmpty()) {
            return DashboardUiState.Empty(monthKey, hasBudget = true)
        }

        // Snapshot math uses prior-month budgets too. Load them once.
        val priorBudgetEntities = budgetDao.getAllBefore(monthKey)
        val allBudgets: List<Budget> = buildList {
            priorBudgetEntities.forEach { add(it.toDomainBudget()) }
            budgetEntity?.let { add(it.toDomainBudget()) }
        }

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
            budgets = allBudgets,
            monthKey = monthKey,
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

    private fun bucketByCard(
        txs: List<TransactionEntity>,
    ): List<CardChip> =
        txs
            .filter { it.last4 != null && it.budgetEffect == "SPEND" && it.inrAmount != null }
            .groupBy { it.last4!! }
            .map { (last4, rows) ->
                CardChip(
                    last4 = last4,
                    issuer = rows.firstOrNull { it.issuer != null }?.issuer,
                    totalInr = rows.sumOf { it.inrAmount!! },
                    transactionCount = rows.size,
                )
            }
            .sortedByDescending { it.totalInr }

    private fun BudgetEntity.toDomainBudget(): Budget = Budget(
        monthKey = monthKey,
        limitInr = limitInr,
        warnThresholdPct = thresholdWarnPct,
    )

    private fun TransactionEntity.toDomainTransaction(): Transaction = Transaction(
        id = id,
        monthKey = monthKey,
        inrAmount = inrAmount,
        budgetEffect = com.spendwise.classifier.BudgetEffect.valueOf(budgetEffect),
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
