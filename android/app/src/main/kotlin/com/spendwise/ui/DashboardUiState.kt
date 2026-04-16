package com.spendwise.ui

import com.spendwise.budget.MonthSnapshot

/**
 * Dashboard state model per ui-spec.md §3. Emitted by
 * [DashboardViewModel] via a [kotlinx.coroutines.flow.Flow].
 */
sealed interface DashboardUiState {

    /** First moment after process start — no Flow collection yet. */
    data object Loading : DashboardUiState

    /** No SMS permission has ever been granted. */
    data object NeedsPermission : DashboardUiState

    /** Permission granted but no transactions yet. */
    data class Empty(val monthKey: String, val hasBudget: Boolean) : DashboardUiState

    /** The happy path. */
    data class Ready(
        val monthKey: String,
        val snapshot: MonthSnapshot,
        val derived: DashboardDerived,
        val byCategory: List<CategorySlice>,
        val byCard: List<CardChip>,
        val transactionCount: Int,
        val pendingForexCount: Int,
    ) : DashboardUiState
}

data class CategorySlice(
    val categoryName: String,          // display name
    val totalInr: Double,
    val transactionCount: Int,
)

/**
 * One tile in the dashboard's "Accounts" strip. Since issue #6,
 * identity can be by registered-account id OR by an unregistered
 * last-4 — accountId is the canonical nav arg; last4 is fallback.
 *
 * At least one of [accountId] or [last4] is non-null (otherwise the
 * tx is ungrouped and doesn't surface here).
 */
data class CardChip(
    val accountId: Long?,
    val last4: String?,
    val issuer: String?,
    val totalInr: Double,
    val transactionCount: Int,
)
