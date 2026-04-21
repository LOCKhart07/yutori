package com.yutori.ui

import com.yutori.budget.MonthSnapshot

/**
 * Dashboard state model per ui-spec.md §3. Emitted by
 * [DashboardViewModel] via a [kotlinx.coroutines.flow.Flow].
 */
sealed interface DashboardUiState {

    /** First moment after process start — no Flow collection yet. */
    data object Loading : DashboardUiState

    /** No SMS permission has ever been granted. */
    data object NeedsPermission : DashboardUiState

    /**
     * Permission granted but no transactions yet. [limitInr] is the
     * resolved limit when one exists (explicit row OR inherited per
     * #14) — non-null iff [hasBudget] is true. The view uses it to
     * render "of ₹X · no spend yet" instead of a bare "No spend yet
     * this month".
     */
    data class Empty(
        val monthKey: String,
        val hasBudget: Boolean,
        val limitInr: Double? = null,
    ) : DashboardUiState

    /** The happy path. */
    data class Ready(
        val monthKey: String,
        val snapshot: MonthSnapshot,
        val derived: DashboardDerived,
        val byCategory: List<CategorySlice>,
        val byCard: List<CardChip>,
        val transactionCount: Int,
        val pendingForexCount: Int,
        /**
         * True when viewing the current month AND zero SPEND-effect
         * transactions have occurred_at within today's local-day window.
         * Drives the "No spends today" easter-egg pill (#79). Always
         * false on past/future months.
         */
        val noSpendsToday: Boolean = false,
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
