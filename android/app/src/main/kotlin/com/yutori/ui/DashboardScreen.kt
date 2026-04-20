package com.yutori.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yutori.R
import com.yutori.budget.MonthSnapshot
import com.yutori.transactions.MonthKeyComputer
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Dashboard — v2 Copilot-inspired render. See plans/ui-design-brief.md
 * and mockups/v2.html. Frames covered:
 *   1   · normal under-budget
 *   1b  · approaching limit (amber progress + "pacing hot")
 *   1c  · early month (hides daily-burn, shows known-bills placeholder)
 *   1d  · last stretch, hot
 *   1e  · last stretch, surplus (green "on track")
 *   2   · over budget (red hero + banner)
 *   3   · no budget set
 */
/**
 * Max forward months past the current month that the pager is allowed
 * to reach. Issue #21 makes the forward direction "unbounded", but
 * Pager math prefers a finite page count — 24 months is well beyond any
 * realistic pre-setting horizon.
 */
private const val MAX_FORWARD_MONTHS = 24

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewedMonthKey: String,
    earliestMonthKey: String,
    currentMonthKey: String,
    observeMonth: (String) -> Flow<DashboardUiState>,
    importStatus: ImportStatus = ImportStatus.Idle,
    latestIngestedMessages: List<LatestIngestedMessage> = emptyList(),
    onSetBudget: () -> Unit = {},
    onImport: () -> Unit = {},
    onSettings: () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    onCardClick: (accountId: Long?, last4: String?) -> Unit = { _, _ -> },
    hasSettingsBadge: Boolean = false,
    onMonthSettled: (String) -> Unit = {},
    onResetMonth: () -> Unit = {},
    showNotificationPermissionBanner: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    onDismissNotificationBanner: () -> Unit = {},
) {
    // earliestMonthKey bounds the past edge; forward goes until the
    // current month + MAX_FORWARD_MONTHS so the user can preset a
    // budget a year or two ahead per #21.
    val pageCount = remember(earliestMonthKey, currentMonthKey) {
        val currentIdx = MonthKeyComputer
            .monthsBetween(earliestMonthKey, currentMonthKey)
            .toInt()
            .coerceAtLeast(0)
        currentIdx + 1 + MAX_FORWARD_MONTHS
    }
    val initialPage = remember(earliestMonthKey, viewedMonthKey, pageCount) {
        MonthKeyComputer
            .monthsBetween(earliestMonthKey, viewedMonthKey)
            .toInt()
            .coerceIn(0, pageCount - 1)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pageCount },
    )
    // Emit onMonthSettled whenever the pager lands on a page. Uses
    // settledPage (not currentPage) so mid-drag values don't thrash the
    // ViewModel. The topbar label is driven separately from currentPage
    // below so it stays in sync with the page content during the swipe.
    LaunchedEffect(pagerState, earliestMonthKey) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val mk = MonthKeyComputer.shift(earliestMonthKey, page.toLong())
            onMonthSettled(mk)
        }
    }
    // Month currently rendered by the pager — follows currentPage, which
    // snaps at the midpoint of a swipe/animation, matching when the
    // neighbouring page's content takes over. Drives the topbar label
    // and the "Today" affordance so they don't lag behind the content.
    val displayedMonthKey by remember(earliestMonthKey) {
        derivedStateOf {
            MonthKeyComputer.shift(earliestMonthKey, pagerState.currentPage.toLong())
        }
    }
    // If earliestMonthKey shifts earlier (historical import lands),
    // every page's index remaps. Re-pin the pager to viewedMonthKey so
    // the user stays on the same month visually.
    LaunchedEffect(earliestMonthKey) {
        val target = MonthKeyComputer
            .monthsBetween(earliestMonthKey, viewedMonthKey)
            .toInt()
            .coerceIn(0, pageCount - 1)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val statusInset: PaddingValues = WindowInsets.statusBars.asPaddingValues()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusInset.calculateTopPadding() + 8.dp),
        ) {
            TopBar(
                monthLabel = prettyMonthKey(displayedMonthKey, dayLabel = null),
                onImport = onImport,
                onSettings = onSettings,
                hasSettingsBadge = hasSettingsBadge,
                onMonthPrev = {
                    scope.launch {
                        val target = (pagerState.currentPage - 1).coerceAtLeast(0)
                        pagerState.animateScrollToPage(target)
                    }
                },
                onMonthNext = {
                    scope.launch {
                        val target = (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                        pagerState.animateScrollToPage(target)
                    }
                },
                onResetMonth = {
                    onResetMonth()
                    val target = MonthKeyComputer
                        .monthsBetween(earliestMonthKey, currentMonthKey)
                        .toInt()
                        .coerceIn(0, pageCount - 1)
                    scope.launch { pagerState.animateScrollToPage(target) }
                },
                canGoPrev = pagerState.currentPage > 0,
                canGoNext = pagerState.currentPage < pageCount - 1,
                isCurrentMonth = displayedMonthKey == currentMonthKey,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (showNotificationPermissionBanner) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.padding(horizontal = 24.dp)) {
                    NotificationPermissionBanner(
                        onAction = onOpenNotificationSettings,
                        onDismiss = onDismissNotificationBanner,
                    )
                }
            }
            // Jumps the pager to the page for [target] and lets
            // onMonthSettled propagate the settled month back to the
            // ViewModel. Mirrors the onResetMonth pattern above. Used
            // by the import-landing affordance (#74) so the user can
            // jump back to the earliest month a run wrote into.
            val onJumpToMonth: (String) -> Unit = { target ->
                val page = MonthKeyComputer
                    .monthsBetween(earliestMonthKey, target)
                    .toInt()
                    .coerceIn(0, pageCount - 1)
                scope.launch { pagerState.animateScrollToPage(page) }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // 12dp peek on each edge — lets the user see a sliver
                // of the neighbor month, hinting the dashboard is
                // horizontally pageable (#21 decision).
                contentPadding = PaddingValues(horizontal = 12.dp),
                pageSpacing = 0.dp,
            ) { page ->
                val monthKey = remember(earliestMonthKey, page) {
                    MonthKeyComputer.shift(earliestMonthKey, page.toLong())
                }
                val monthFlow: Flow<DashboardUiState> =
                    remember(monthKey) { observeMonth(monthKey) }
                val state by monthFlow.collectAsStateWithLifecycle(
                    initialValue = DashboardUiState.Loading,
                )
                MonthPage(
                    state = state,
                    importStatus = importStatus,
                    latestIngestedMessages = latestIngestedMessages,
                    onSetBudget = onSetBudget,
                    onCategoryClick = onCategoryClick,
                    onCardClick = onCardClick,
                    isCurrentMonth = monthKey == currentMonthKey,
                    pageMonthKey = monthKey,
                    onJumpToMonth = onJumpToMonth,
                )
            }
        }
    }
}

@Composable
private fun MonthPage(
    state: DashboardUiState,
    importStatus: ImportStatus,
    latestIngestedMessages: List<LatestIngestedMessage>,
    onSetBudget: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onCardClick: (accountId: Long?, last4: String?) -> Unit,
    isCurrentMonth: Boolean,
    pageMonthKey: String,
    onJumpToMonth: (String) -> Unit,
) {
    when (state) {
        is DashboardUiState.Loading -> LoadingView()
        is DashboardUiState.NeedsPermission -> NeedsPermissionView()
        is DashboardUiState.Empty -> EmptyView(
            state, onSetBudget, importStatus, latestIngestedMessages, isCurrentMonth,
            pageMonthKey, onJumpToMonth,
        )
        is DashboardUiState.Ready ->
            ReadyView(
                state, importStatus, latestIngestedMessages,
                onSetBudget, onCategoryClick, onCardClick,
                isCurrentMonth, pageMonthKey, onJumpToMonth,
            )
    }
}

// ───────────────────────── States ─────────────────────────

@Composable
private fun LoadingView() {
    LoadingSpinner()
}

@Composable
private fun NeedsPermissionView() {
    EmptyState(
        title = "SMS permission required",
        description = "Grant SMS access in Settings to let Yutori " +
            "read bank/UPI messages. We never send or share them.",
    )
}

@Composable
private fun EmptyView(
    state: DashboardUiState.Empty,
    onSetBudget: () -> Unit,
    importStatus: ImportStatus,
    latestIngestedMessages: List<LatestIngestedMessage>,
    isCurrentMonth: Boolean,
    pageMonthKey: String,
    onJumpToMonth: (String) -> Unit,
) {
    val inr = remember {
        NumberFormat.getCurrencyInstance(
            Locale.Builder().setLanguage("en").setRegion("IN").build(),
        )
    }
    ScrollingShell {
        Spacer(Modifier.height(24.dp))
        HeroAmount(
            primaryText = "₹0",
            subText = when {
                state.hasBudget && state.limitInr != null ->
                    "of ${inr.formatAmount(state.limitInr, compact = true)} · no spend yet"
                state.hasBudget -> "No spend yet this month"
                !isCurrentMonth -> "No budget set for this month"
                else -> "Spent this month"
            },
            // When a limit exists (explicit or inherited per #14), the
            // hero is the affordance to edit it — matches the Ready
            // state's wiring. No-budget months keep their dedicated
            // "Set budget" button below and no hero tap.
            onClick = if (state.hasBudget) onSetBudget else null,
        )
        Spacer(Modifier.height(20.dp))
        if (!state.hasBudget) {
            Banner(
                kind = BannerKind.Neutral,
                title = if (isCurrentMonth) "No budget set." else "Preset this month's budget.",
                detail = if (isCurrentMonth) {
                    "Set a monthly limit to enable progress tracking and alerts."
                } else {
                    "Set a budget ahead of time. Carry-over applies when the month begins."
                },
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = if (isCurrentMonth) "Set budget" else "Set budget for this month",
                onClick = onSetBudget,
            )
        } else {
            Banner(
                kind = BannerKind.Neutral,
                title = "No transactions yet.",
                detail = "Incoming SMSes will appear here automatically.",
            )
        }
        Spacer(Modifier.height(24.dp))
        ImportStatusBlock(importStatus, pageMonthKey, onJumpToMonth)
        if (isCurrentMonth) {
            LatestIngestedMessagesBlock(latestIngestedMessages)
        }
    }
}

@Composable
private fun ReadyView(
    state: DashboardUiState.Ready,
    importStatus: ImportStatus,
    latestIngestedMessages: List<LatestIngestedMessage>,
    onSetBudget: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onCardClick: (accountId: Long?, last4: String?) -> Unit,
    isCurrentMonth: Boolean,
    pageMonthKey: String,
    onJumpToMonth: (String) -> Unit,
) {
    val inr = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) }
    val snap = state.snapshot
    val derived = state.derived
    val hasBudget = snap.effectiveBudgetInr > 0.0
    // Hoist theme token reads to the top so conditional @Composable
    // getter access inside `if`/`when` branches can't imbalance groups.
    val themeColors = YutoriTheme.colors

    ScrollingShell {
        Spacer(Modifier.height(24.dp))

        // Hero — wrapped in a pace-tinted card.
        PaceTintedHeroCard(pace = derived.pace) {
            HeroAmount(
                primaryText = inr.formatAmount(snap.netSpendInr, compact = true),
                subText = heroSubLine(snap, derived, inr),
                primaryColor = when {
                    derived.pace == PaceBucket.Over -> themeColors.negative
                    derived.pace == PaceBucket.OverPace -> themeColors.warn
                    else -> null
                },
                subColor = subLineColor(derived.banner),
                onClick = if (hasBudget) onSetBudget else null,
            )

            if (hasBudget) {
                Spacer(Modifier.height(14.dp))
                ProgressTrack(
                    fraction = (snap.percentUsed / 100.0).coerceIn(0.0, 1.0).toFloat(),
                    color = paceProgressColor(derived.pace),
                    expectedFraction = derived.expectedPercentByNow
                        ?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        StatRow(snap, derived, inr)

        // Banner (state-aware)
        when (val b = derived.banner) {
            is DashboardBanner.Over -> {
                Spacer(Modifier.height(16.dp))
                Banner(
                    kind = BannerKind.Negative,
                    title = "Budget exceeded.",
                    detail = "You'll start next month with a ${inr.formatAmount(b.deficit, compact = true)} deficit carried over.",
                )
            }
            is DashboardBanner.Approaching -> {
                Spacer(Modifier.height(16.dp))
                Banner(
                    kind = BannerKind.Warn,
                    title = "Pacing hot.",
                    detail = "Keep to ${inr.formatAmount(b.dailyCapInr, compact = true)}/day to land on budget.",
                )
            }
            is DashboardBanner.OnTrack -> {
                Spacer(Modifier.height(16.dp))
                Banner(
                    kind = BannerKind.Positive,
                    title = "On track.",
                    detail = "At your current pace you'll carry +${inr.formatAmount(b.projectedSurplusInr, compact = true)} into next month.",
                )
            }
            is DashboardBanner.NoBudget -> {
                Spacer(Modifier.height(16.dp))
                Banner(
                    kind = BannerKind.Neutral,
                    title = "No budget set.",
                    detail = "Set a monthly limit to enable progress tracking and alerts.",
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Set budget", onSetBudget)
            }
            DashboardBanner.Normal, DashboardBanner.EarlyMonth -> Unit
        }

        if (state.pendingForexCount > 0) {
            Spacer(Modifier.height(16.dp))
            Banner(
                kind = BannerKind.Info,
                title = "${state.pendingForexCount} transaction(s) pending currency conversion.",
                detail = "Rates fetched automatically when network is available.",
            )
        }

        ImportStatusBlock(importStatus, pageMonthKey, onJumpToMonth)
        if (isCurrentMonth) {
            LatestIngestedMessagesBlock(latestIngestedMessages)
        }

        // By category
        Spacer(Modifier.height(24.dp))
        var catSort: CategorySort by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<CategorySort>(CategorySort.AmountDesc)
        }
        SortableSectionHead(
            title = "Spend by category",
            sortLabel = catSort.label,
            sortIcon = catSort.icon,
            onCycle = { catSort = catSort.next() },
            showSort = state.byCategory.isNotEmpty(),
        )
        if (state.byCategory.isEmpty()) {
            EmptySection("No spend yet.")
        } else {
            applyCategorySort(state.byCategory, catSort).forEach { slice ->
                CategoryRow(slice, inr, onClick = { onCategoryClick(slice.categoryName) })
            }
        }

        // Accounts strip
        if (state.byCard.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHead(title = "Accounts", meta = null)
            Spacer(Modifier.height(12.dp))
            AccountStrip(state.byCard, inr, onCardClick)
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = pluralStringResource(
                R.plurals.transactions_this_month,
                state.transactionCount,
                state.transactionCount,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = YutoriTheme.colors.onFaint,
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ───────────────────────── Building blocks ─────────────────────────

@Composable
private fun ScrollingShell(content: @Composable () -> Unit) {
    // The outer DashboardScreen applies the status-bar inset and hosts
    // the static TopBar; each pager page is just vertically-scrollable
    // content. Horizontal 12.dp here pairs with the Pager's 12.dp
    // contentPadding so a page's content still sits ~24.dp from the
    // physical screen edge (unchanged from pre-pager behaviour).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        content()
    }
}

@Composable
private fun TopBar(
    monthLabel: String,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    hasSettingsBadge: Boolean = false,
    onMonthPrev: () -> Unit = {},
    onMonthNext: () -> Unit = {},
    onResetMonth: () -> Unit = {},
    canGoPrev: Boolean = true,
    canGoNext: Boolean = true,
    isCurrentMonth: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Past chevron dims at the past-edge (earliest sms_log month).
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
                modifier = Modifier
                    .clickable(enabled = canGoPrev, onClick = onMonthPrev)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .size(24.dp),
                tint = if (canGoPrev) {
                    YutoriTheme.colors.onMuted
                } else {
                    YutoriTheme.colors.onFaint
                },
            )
            Text(
                text = monthLabel,
                modifier = Modifier.padding(horizontal = 2.dp),
                style = YutoriTextStyles.Caps,
                color = YutoriTheme.colors.onMuted,
            )
            // Forward chevron is unbounded forward per #21; dim only at
            // the (remote) MAX_FORWARD_MONTHS ceiling.
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                modifier = Modifier
                    .clickable(enabled = canGoNext, onClick = onMonthNext)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .size(24.dp),
                tint = if (canGoNext) {
                    YutoriTheme.colors.onMuted
                } else {
                    YutoriTheme.colors.onFaint
                },
            )
            if (!isCurrentMonth) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Today",
                    modifier = Modifier
                        .clickable(onClick = onResetMonth)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Import",
                modifier = Modifier.clickable(onClick = onImport).padding(vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
            androidx.compose.foundation.layout.Box(
                contentAlignment = Alignment.TopEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier
                        .clickable(onClick = onSettings)
                        .padding(vertical = 8.dp)
                        .size(22.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
                if (hasSettingsBadge) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .padding(top = 4.dp, end = 2.dp)
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroAmount(
    primaryText: String,
    subText: String,
    primaryColor: Color? = null,
    subColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val tapMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(modifier = tapMod) {
        Text(
            text = primaryText,
            style = MaterialTheme.typography.displayLarge,
            color = primaryColor ?: MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subText,
            style = MaterialTheme.typography.bodyMedium,
            color = subColor ?: YutoriTheme.colors.onMuted,
        )
    }
}

private fun heroSubLine(
    snap: MonthSnapshot,
    derived: DashboardDerived,
    inr: NumberFormat,
): String {
    val hasBudget = snap.effectiveBudgetInr > 0.0
    if (!hasBudget) return "Spent this month"
    val remaining = snap.effectiveBudgetInr - snap.netSpendInr
    val pct = snap.percentUsed
    val budget = inr.formatAmount(snap.effectiveBudgetInr, compact = true)
    return when {
        pct >= 100.0 ->
            "of $budget · ${"%.0f".format(pct)}% · over by ${inr.formatAmount(-remaining, compact = true)}"
        derived.daysLeft in 1..3 && pct >= 85.0 ->
            "of $budget · ${inr.formatAmount(remaining, compact = true)} for ${derived.daysLeft} days"
        derived.daysLeft in 1..3 && pct < 85.0 ->
            "of $budget · ${inr.formatAmount(remaining, compact = true)} remaining, ${derived.daysLeft} days left"
        else ->
            "of $budget · ${"%.0f".format(pct)}% used"
    }
}

@Composable
private fun subLineColor(banner: DashboardBanner): Color? {
    // Read the theme once, unconditionally — mixing @Composable getter
    // calls across `when` branches (especially when one branch returns
    // null) causes a Stack.pop group-imbalance crash on recomposition.
    val colors = YutoriTheme.colors
    return when (banner) {
        is DashboardBanner.Over        -> colors.negative
        is DashboardBanner.Approaching -> colors.warn
        is DashboardBanner.OnTrack     -> colors.positive
        else -> null
    }
}

@Composable
private fun progressColor(percentUsed: Double): Color {
    val colors = YutoriTheme.colors
    val dim = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    return when {
        percentUsed >= 100.0 -> colors.negative
        percentUsed >= 80.0  -> colors.warn
        else                 -> dim
    }
}

@Composable
private fun ProgressTrack(
    fraction: Float,
    color: Color,
    expectedFraction: Float? = null,
) {
    val colors = YutoriTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            progress = fraction.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .align(Alignment.Center),
            color = color,
            trackColor = colors.surfaceElevated2,
        )
        // Faint vertical tick at "today's expected position" — the gap
        // between the fill and the tick is the surplus/deficit story.
        if (expectedFraction != null && expectedFraction in 0.001f..0.999f) {
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
            ) {
                val tickX = maxWidth * expectedFraction
                Box(
                    modifier = Modifier
                        .padding(start = tickX - 1.dp)
                        .width(2.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(colors.onFaint),
                )
            }
        }
    }
}

/**
 * Tints the hero block based on the pace bucket. Same colour grammar
 * as the alert thresholds: green = under-pace (surplus brewing),
 * neutral = on track, warn = over-pace, red = over budget.
 */
@Composable
private fun PaceTintedHeroCard(
    pace: PaceBucket,
    content: @Composable () -> Unit,
) {
    val colors = YutoriTheme.colors
    val tint = when (pace) {
        PaceBucket.Under   -> colors.positive
        PaceBucket.OnTrack -> colors.onMuted
        PaceBucket.OverPace -> colors.warn
        PaceBucket.Over    -> colors.negative
    }
    val (bg, border) = when (pace) {
        PaceBucket.OnTrack -> colors.surfaceElevated to colors.divider
        else -> tint.copy(alpha = 0.08f) to tint.copy(alpha = 0.20f)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            content()
        }
    }
}

@Composable
private fun paceProgressColor(pace: PaceBucket): Color {
    val colors = YutoriTheme.colors
    return when (pace) {
        PaceBucket.Under   -> colors.positive
        PaceBucket.OnTrack -> MaterialTheme.colorScheme.primary
        PaceBucket.OverPace -> colors.warn
        PaceBucket.Over    -> colors.negative
    }
}

@Composable
private fun StatRow(
    snap: MonthSnapshot,
    derived: DashboardDerived,
    inr: NumberFormat,
) {
    val hasBudget = snap.effectiveBudgetInr > 0.0
    // First two stats always the same.
    // Sign-free display — red text + "Deficit" label carry the negative
    // signal without a minus prefix competing visually with the amount.
    val carryV = when {
        snap.carryOverInr > 0.0 -> "+${inr.formatAmount(snap.carryOverInr, compact = true)}"
        snap.carryOverInr < 0.0 -> inr.formatAmount(-snap.carryOverInr, compact = true)
        else                    -> "₹0"
    }
    val colors = YutoriTheme.colors
    val carryColor = when {
        snap.carryOverInr > 0.0 -> colors.positive
        snap.carryOverInr < 0.0 -> colors.negative
        else                    -> null
    }

    // Third stat adapts to context.
    data class StatSlot(val key: String, val value: String, val color: Color?)

    val third: StatSlot = when (val b = derived.banner) {
        is DashboardBanner.Over -> StatSlot(
            "Deficit",
            inr.formatAmount(b.deficit, compact = true),
            colors.negative,
        )
        is DashboardBanner.Approaching -> StatSlot(
            "Daily cap",
            inr.formatAmount(b.dailyCapInr, compact = true),
            colors.warn,
        )
        is DashboardBanner.OnTrack -> StatSlot(
            "Projected surplus",
            "+${inr.formatAmount(b.projectedSurplusInr, compact = true)}",
            colors.positive,
        )
        is DashboardBanner.EarlyMonth -> StatSlot(
            "Elapsed days",
            "${derived.dayOfMonth} of ${derived.daysInMonth}",
            null,
        )
        is DashboardBanner.NoBudget -> StatSlot(
            "Elapsed days",
            "${derived.dayOfMonth} of ${derived.daysInMonth}",
            null,
        )
        DashboardBanner.Normal -> StatSlot(
            "Daily burn",
            inr.formatAmount(derived.dailyBurnInr, compact = true),
            null,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatChip(
            label = "Carry-over",
            value = carryV,
            valueColor = carryColor,
            modifier = Modifier.weight(1f),
        )
        if (hasBudget) {
            StatChip(
                label = "Days left",
                value = derived.daysLeft.toString(),
                valueColor = if (derived.daysLeft in 1..3 && snap.percentUsed >= 80.0) {
                    colors.warn
                } else null,
                modifier = Modifier.weight(1f),
            )
            StatChip(
                label = third.key,
                value = third.value,
                valueColor = third.color,
                modifier = Modifier.weight(1f),
            )
        } else {
            // No budget — show calendar position as a single wide chip
            // so the row doesn't feel empty and doesn't duplicate the
            // same info in two cells.
            StatChip(
                label = "Day",
                value = "${derived.dayOfMonth} of ${derived.daysInMonth}",
                modifier = Modifier.weight(2f),
            )
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    valueColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = YutoriTheme.colors.surfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = YutoriTheme.colors.divider,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = YutoriTheme.colors.onFaint,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = YutoriTextStyles.Mono.copy(fontWeight = FontWeight.Medium),
                color = valueColor ?: MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

// ───────────────────────── Banner ─────────────────────────

private enum class BannerKind { Neutral, Warn, Positive, Negative, Info }

@Composable
private fun Banner(kind: BannerKind, title: String, detail: String) {
    val colors = YutoriTheme.colors
    val primary = MaterialTheme.colorScheme.primary
    val dotColor = when (kind) {
        BannerKind.Neutral  -> primary
        BannerKind.Warn     -> colors.warn
        BannerKind.Positive -> colors.positive
        BannerKind.Negative -> colors.negative
        BannerKind.Info     -> colors.info
    }
    val bg = dotColor.copy(alpha = 0.09f)
    val border = dotColor.copy(alpha = 0.22f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(dotColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = YutoriTheme.colors.onMuted,
            )
        }
        Spacer(
            modifier = Modifier
                .width(0.dp)
                .height(0.dp)
                .background(border),
        )
    }
}

@Composable
private fun NotificationPermissionBanner(
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = YutoriTheme.colors
    val tint = colors.warn
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.09f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(tint),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notifications are off.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Budget threshold alerts won't appear until " +
                        "you grant permission in Android settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onMuted,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Open settings",
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Dismiss",
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onMuted,
            )
        }
    }
}

// ───────────────────────── Category rows ─────────────────────────

@Composable
private fun SectionHead(title: String, meta: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title.uppercase(),
            style = YutoriTextStyles.Caps,
            color = YutoriTheme.colors.onFaint,
        )
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = YutoriTheme.colors.onFaint,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    slice: CategorySlice,
    inr: NumberFormat,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(YutoriTheme.colors.forCategory(slice.categoryName)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prettyCategory(slice.categoryName),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.transactions,
                    slice.transactionCount,
                    slice.transactionCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = YutoriTheme.colors.onFaint,
            )
        }
        Text(
            text = inr.formatAmount(slice.totalInr, compact = true),
            style = YutoriTextStyles.Mono,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun EmptySection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = YutoriTheme.colors.onMuted,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

// ───────────────────────── Account strip ─────────────────────────

@Composable
private fun AccountStrip(
    cards: List<CardChip>,
    inr: NumberFormat,
    onCardClick: (accountId: Long?, last4: String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        cards.forEach { card ->
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .clickable { onCardClick(card.accountId, card.last4) }
                    .clip(RoundedCornerShape(12.dp))
                    .background(YutoriTheme.colors.surfaceElevated)
                    .padding(14.dp),
            ) {
                Text(
                    text = (card.issuer ?: "Unknown").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = YutoriTheme.colors.onMuted,
                )
                // UPI-only accounts (issue #6) have no last-4 — show the
                // UPI tag instead of a masked number placeholder.
                Text(
                    text = card.last4?.let { "\u2022\u2022$it" } ?: "UPI",
                    style = YutoriTextStyles.Mono.copy(fontWeight = FontWeight.Normal),
                    color = YutoriTheme.colors.onMuted,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = inr.formatAmount(card.totalInr, compact = true),
                    style = YutoriTextStyles.Mono,
                )
            }
        }
    }
}

// ───────────────────────── Import status ─────────────────────────

@Composable
private fun ImportStatusBlock(
    status: ImportStatus,
    pageMonthKey: String,
    onJumpToMonth: (String) -> Unit,
) {
    when (status) {
        is ImportStatus.Idle -> Unit
        is ImportStatus.Running -> {
            Spacer(Modifier.height(12.dp))
            val label = if (status.total > 0) {
                "Importing past SMSes — ${status.processed}/${status.total}"
            } else {
                "Preparing import…"
            }
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = YutoriTheme.colors.onMuted,
            )
            LinearProgressIndicator(
                progress = {
                    if (status.total > 0) status.processed.toFloat() / status.total else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .padding(top = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = YutoriTheme.colors.surfaceElevated2,
            )
        }
        is ImportStatus.Succeeded -> {
            Spacer(Modifier.height(12.dp))
            val base = "Imported ${status.inserted} new, skipped ${status.duplicates} duplicates" +
                if (status.failures > 0) ", ${status.failures} failed" else ""
            val earliest = status.earliestMonthTouched
            if (earliest != null && earliest < pageMonthKey) {
                val label = prettyMonthKey(earliest, dayLabel = null)
                Text(
                    text = "$base · earliest in $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = YutoriTheme.colors.onMuted,
                )
                Text(
                    text = "Jump to $label",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { onJumpToMonth(earliest) },
                )
            } else {
                Text(
                    text = base,
                    style = MaterialTheme.typography.bodySmall,
                    color = YutoriTheme.colors.onMuted,
                )
            }
        }
        is ImportStatus.Failed -> {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Import failed: ${status.message ?: "unknown error"}",
                style = MaterialTheme.typography.bodySmall,
                color = YutoriTheme.colors.negative,
            )
        }
    }
}

@Composable
private fun LatestIngestedMessagesBlock(
    messages: List<LatestIngestedMessage>,
) {
    if (messages.isEmpty()) return

    Spacer(Modifier.height(16.dp))
    SectionHead(title = "Latest ingested messages", meta = null)
    Spacer(Modifier.height(10.dp))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = YutoriTheme.colors.surfaceElevated,
    ) {
        Column {
            messages.forEachIndexed { index, msg ->
                LatestIngestedRow(msg)
                if (index < messages.lastIndex) {
                    HorizontalDivider(color = YutoriTheme.colors.divider)
                }
            }
        }
    }
}

@Composable
private fun LatestIngestedRow(
    message: LatestIngestedMessage,
) {
    val colors = YutoriTheme.colors
    val (label, textTone, pillTone) = when (message.outcome) {
        IngestedMessageOutcome.AFFECTS_BUDGET ->
            Triple("Affects budget", colors.positive, colors.positive.copy(alpha = 0.2f))
        IngestedMessageOutcome.TRACKED_AS_INCOME ->
            Triple("Tracked as income", colors.info, colors.info.copy(alpha = 0.2f))
        IngestedMessageOutcome.IGNORED ->
            Triple("Ignored", colors.onMuted, colors.surfaceElevated2)
        IngestedMessageOutcome.NEEDS_REVIEW ->
            Triple("Needs review", colors.warn, colors.warn.copy(alpha = 0.2f))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message.sender,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = pillTone,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textTone,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            text = message.bodyPreview,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onMuted,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ───────────────────────── Primary button ─────────────────────────

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

// ───────────────────────── Helpers ─────────────────────────

private fun prettyMonthKey(monthKey: String, dayLabel: String?): String {
    // monthKey is "YYYY-MM"; convert to e.g. "April 2026".
    return try {
        val (y, m) = monthKey.split("-").let { it[0] to it[1].toInt() }
        val name = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )[m - 1]
        val base = "$name $y"
        if (dayLabel != null) "$base · $dayLabel" else base
    } catch (_: Exception) { monthKey }
}

// formatAmount() lives in MoneyFormatting.kt.

// ───────────────────────── Sort ─────────────────────────

/** Cycle: amount desc → amount asc → name A–Z → back. */
internal enum class CategorySort(
    val label: String,
    val icon: ImageVector? = null,
) {
    AmountDesc("Amount", Icons.Default.KeyboardArrowDown),
    AmountAsc("Amount", Icons.Default.KeyboardArrowUp),
    NameAsc("Name A–Z");

    fun next(): CategorySort = when (this) {
        AmountDesc -> AmountAsc
        AmountAsc  -> NameAsc
        NameAsc    -> AmountDesc
    }
}

private fun applyCategorySort(
    slices: List<CategorySlice>,
    sort: CategorySort,
): List<CategorySlice> = when (sort) {
    CategorySort.AmountDesc -> slices.sortedByDescending { it.totalInr }
    CategorySort.AmountAsc  -> slices.sortedBy { it.totalInr }
    CategorySort.NameAsc    -> slices.sortedBy { prettyCategory(it.categoryName) }
}

@Composable
private fun SortableSectionHead(
    title: String,
    sortLabel: String,
    sortIcon: ImageVector?,
    onCycle: () -> Unit,
    showSort: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title.uppercase(),
            style = YutoriTextStyles.Caps,
            color = YutoriTheme.colors.onFaint,
        )
        if (showSort) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onCycle)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(
                    text = sortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = YutoriTheme.colors.onMuted,
                )
                if (sortIcon != null) {
                    Icon(
                        imageVector = sortIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = YutoriTheme.colors.onMuted,
                    )
                }
            }
        }
    }
}
