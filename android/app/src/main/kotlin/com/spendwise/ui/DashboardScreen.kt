package com.spendwise.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendwise.budget.MonthSnapshot
import com.spendwise.ui.theme.SpendWiseTextStyles
import com.spendwise.ui.theme.SpendWiseTheme
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
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    importStatus: ImportStatus = ImportStatus.Idle,
    onSetBudget: () -> Unit = {},
    onImport: () -> Unit = {},
    onSettings: () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    onCardClick: (last4: String) -> Unit = {},
    hasSettingsBadge: Boolean = false,
    onMonthPrev: () -> Unit = {},
    onMonthNext: () -> Unit = {},
    onResetMonth: () -> Unit = {},
    isCurrentMonth: Boolean = true,
    showNotificationPermissionBanner: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    onDismissNotificationBanner: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (state) {
            is DashboardUiState.Loading -> LoadingView()
            is DashboardUiState.NeedsPermission -> NeedsPermissionView()
            is DashboardUiState.Empty ->
                EmptyView(
                    state, onSetBudget, onImport, importStatus, onSettings,
                    hasSettingsBadge,
                    onMonthPrev, onMonthNext, onResetMonth, isCurrentMonth,
                    showNotificationPermissionBanner,
                    onOpenNotificationSettings, onDismissNotificationBanner,
                )
            is DashboardUiState.Ready ->
                ReadyView(
                    state, importStatus,
                    onSetBudget, onImport, onSettings,
                    onCategoryClick, onCardClick,
                    hasSettingsBadge,
                    onMonthPrev, onMonthNext, onResetMonth, isCurrentMonth,
                    showNotificationPermissionBanner,
                    onOpenNotificationSettings, onDismissNotificationBanner,
                )
        }
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
        description = "Grant SMS access in Settings to let SpendWise " +
            "read bank/UPI messages. We never send or share them.",
    )
}

@Composable
private fun EmptyView(
    state: DashboardUiState.Empty,
    onSetBudget: () -> Unit,
    onImport: () -> Unit,
    importStatus: ImportStatus,
    onSettings: () -> Unit,
    hasSettingsBadge: Boolean = false,
    onMonthPrev: () -> Unit = {},
    onMonthNext: () -> Unit = {},
    onResetMonth: () -> Unit = {},
    isCurrentMonth: Boolean = true,
    showNotificationPermissionBanner: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    onDismissNotificationBanner: () -> Unit = {},
) {
    ScrollingShell {
        TopBar(
            monthLabel = prettyMonthKey(state.monthKey, dayLabel = null),
            onImport = onImport,
            onSettings = onSettings,
            hasSettingsBadge = hasSettingsBadge,
            onMonthPrev = onMonthPrev,
            onMonthNext = onMonthNext,
            onResetMonth = onResetMonth,
            isCurrentMonth = isCurrentMonth,
        )
        if (showNotificationPermissionBanner) {
            Spacer(Modifier.height(16.dp))
            NotificationPermissionBanner(
                onAction = onOpenNotificationSettings,
                onDismiss = onDismissNotificationBanner,
            )
        }
        Spacer(Modifier.height(24.dp))
        HeroAmount(primaryText = "₹0", subText = if (state.hasBudget) "No spend yet this month" else "Spent this month")
        Spacer(Modifier.height(20.dp))
        if (!state.hasBudget) {
            Banner(
                kind = BannerKind.Neutral,
                title = "No budget set.",
                detail = "Set a monthly limit to enable progress tracking and alerts.",
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = "Set budget", onClick = onSetBudget)
        } else {
            Banner(
                kind = BannerKind.Neutral,
                title = "No transactions yet.",
                detail = "Incoming SMSes will appear here automatically.",
            )
        }
        Spacer(Modifier.height(24.dp))
        ImportStatusBlock(importStatus)
    }
}

@Composable
private fun ReadyView(
    state: DashboardUiState.Ready,
    importStatus: ImportStatus,
    onSetBudget: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    hasSettingsBadge: Boolean = false,
    onMonthPrev: () -> Unit = {},
    onMonthNext: () -> Unit = {},
    onResetMonth: () -> Unit = {},
    isCurrentMonth: Boolean = true,
    showNotificationPermissionBanner: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    onDismissNotificationBanner: () -> Unit = {},
) {
    val inr = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val snap = state.snapshot
    val derived = state.derived
    val hasBudget = snap.effectiveBudgetInr > 0.0
    // Hoist theme token reads to the top so conditional @Composable
    // getter access inside `if`/`when` branches can't imbalance groups.
    val themeColors = SpendWiseTheme.colors

    ScrollingShell {
        TopBar(
            monthLabel = prettyMonthKey(
                state.monthKey,
                dayLabel = if (isCurrentMonth && derived.daysLeft in 1..3) "Day ${derived.dayOfMonth}" else null,
            ),
            onImport = onImport,
            onSettings = onSettings,
            hasSettingsBadge = hasSettingsBadge,
            onMonthPrev = onMonthPrev,
            onMonthNext = onMonthNext,
            onResetMonth = onResetMonth,
            isCurrentMonth = isCurrentMonth,
        )
        if (showNotificationPermissionBanner) {
            Spacer(Modifier.height(16.dp))
            NotificationPermissionBanner(
                onAction = onOpenNotificationSettings,
                onDismiss = onDismissNotificationBanner,
            )
        }

        Spacer(Modifier.height(24.dp))

        // Hero
        val overBudget = snap.percentUsed >= 100.0
        HeroAmount(
            primaryText = inr.formatCompact(snap.netSpendInr),
            subText = heroSubLine(snap, derived, inr),
            primaryColor = if (overBudget) themeColors.negative else null,
            subColor = subLineColor(derived.banner),
            onClick = if (hasBudget) onSetBudget else null,
        )

        if (hasBudget) {
            Spacer(Modifier.height(16.dp))
            ProgressTrack(
                fraction = (snap.percentUsed / 100.0).coerceIn(0.0, 1.0).toFloat(),
                color = progressColor(snap.percentUsed),
            )
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
                    detail = "You'll start next month with a ${inr.format(b.deficit)} deficit carried over.",
                )
            }
            is DashboardBanner.Approaching -> {
                Spacer(Modifier.height(16.dp))
                Banner(
                    kind = BannerKind.Warn,
                    title = "Pacing hot.",
                    detail = "Keep to ${inr.format(b.dailyCapInr)}/day to land on budget.",
                )
            }
            is DashboardBanner.OnTrack -> {
                Spacer(Modifier.height(16.dp))
                Banner(
                    kind = BannerKind.Positive,
                    title = "On track.",
                    detail = "At your current pace you'll carry +${inr.format(b.projectedSurplusInr)} into next month.",
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

        ImportStatusBlock(importStatus)

        // By category
        Spacer(Modifier.height(24.dp))
        var catSort: CategorySort by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<CategorySort>(CategorySort.AmountDesc)
        }
        SortableSectionHead(
            title = "Spend by category",
            sortLabel = catSort.label,
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
            text = "${state.transactionCount} transaction(s) this month",
            style = MaterialTheme.typography.labelSmall,
            color = SpendWiseTheme.colors.onFaint,
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ───────────────────────── Building blocks ─────────────────────────

@Composable
private fun ScrollingShell(content: @Composable () -> Unit) {
    // Inset for the system status bar — prevents content landing under
    // the phone's clock/icons in edge-to-edge mode.
    val statusInset: PaddingValues =
        WindowInsets.statusBars.asPaddingValues()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = statusInset.calculateTopPadding() + 8.dp)
            .padding(horizontal = 24.dp),
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
    isCurrentMonth: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "‹",
                modifier = Modifier
                    .clickable(onClick = onMonthPrev)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = SpendWiseTheme.colors.onMuted,
            )
            Text(
                text = monthLabel,
                modifier = Modifier.padding(horizontal = 2.dp),
                style = SpendWiseTextStyles.Caps,
                color = SpendWiseTheme.colors.onMuted,
            )
            // Forward chevron disabled on current month (no future data).
            Text(
                text = "›",
                modifier = Modifier
                    .clickable(enabled = !isCurrentMonth, onClick = onMonthNext)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrentMonth) {
                    SpendWiseTheme.colors.onFaint
                } else {
                    SpendWiseTheme.colors.onMuted
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
                Text(
                    text = "⚙",
                    modifier = Modifier.clickable(onClick = onSettings).padding(vertical = 8.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
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
            color = subColor ?: SpendWiseTheme.colors.onMuted,
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
    return when {
        pct >= 100.0 ->
            "of ${inr.format(snap.effectiveBudgetInr)} · ${"%.0f".format(pct)}% · over by ${inr.format(-remaining)}"
        derived.daysLeft in 1..3 && pct >= 85.0 ->
            "of ${inr.format(snap.effectiveBudgetInr)} · ${inr.format(remaining)} for ${derived.daysLeft} days"
        derived.daysLeft in 1..3 && pct < 85.0 ->
            "of ${inr.format(snap.effectiveBudgetInr)} · ${inr.format(remaining)} remaining, ${derived.daysLeft} days left"
        else ->
            "of ${inr.format(snap.effectiveBudgetInr)} · ${"%.0f".format(pct)}% used"
    }
}

@Composable
private fun subLineColor(banner: DashboardBanner): Color? {
    // Read the theme once, unconditionally — mixing @Composable getter
    // calls across `when` branches (especially when one branch returns
    // null) causes a Stack.pop group-imbalance crash on recomposition.
    val colors = SpendWiseTheme.colors
    return when (banner) {
        is DashboardBanner.Over        -> colors.negative
        is DashboardBanner.Approaching -> colors.warn
        is DashboardBanner.OnTrack     -> colors.positive
        else -> null
    }
}

@Composable
private fun progressColor(percentUsed: Double): Color {
    val colors = SpendWiseTheme.colors
    val dim = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    return when {
        percentUsed >= 100.0 -> colors.negative
        percentUsed >= 80.0  -> colors.warn
        else                 -> dim
    }
}

@Composable
private fun ProgressTrack(fraction: Float, color: Color) {
    @Suppress("DEPRECATION")
    LinearProgressIndicator(
        progress = fraction.coerceIn(0f, 1f),
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = SpendWiseTheme.colors.surfaceElevated2,
    )
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
        snap.carryOverInr > 0.0 -> "+${inr.format(snap.carryOverInr)}"
        snap.carryOverInr < 0.0 -> inr.format(-snap.carryOverInr)
        else                    -> "₹0"
    }
    val colors = SpendWiseTheme.colors
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
            inr.format(b.deficit),
            colors.negative,
        )
        is DashboardBanner.Approaching -> StatSlot(
            "Daily cap",
            inr.format(b.dailyCapInr),
            colors.warn,
        )
        is DashboardBanner.OnTrack -> StatSlot(
            "Projected surplus",
            "+${inr.format(b.projectedSurplusInr)}",
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
            inr.format(derived.dailyBurnInr),
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
        color = SpendWiseTheme.colors.surfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = SpendWiseTheme.colors.divider,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = SpendWiseTheme.colors.onFaint,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = SpendWiseTextStyles.Mono.copy(fontWeight = FontWeight.Medium),
                color = valueColor ?: MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

// ───────────────────────── Banner ─────────────────────────

private enum class BannerKind { Neutral, Warn, Positive, Negative, Info }

@Composable
private fun Banner(kind: BannerKind, title: String, detail: String) {
    val colors = SpendWiseTheme.colors
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
                color = SpendWiseTheme.colors.onMuted,
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
    val colors = SpendWiseTheme.colors
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
            style = SpendWiseTextStyles.Caps,
            color = SpendWiseTheme.colors.onFaint,
        )
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = SpendWiseTheme.colors.onFaint,
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
                .background(SpendWiseTheme.colors.forCategory(slice.categoryName)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prettyCategory(slice.categoryName),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${slice.transactionCount} txn(s)",
                style = MaterialTheme.typography.labelSmall,
                color = SpendWiseTheme.colors.onFaint,
            )
        }
        Text(
            text = inr.formatCompact(slice.totalInr),
            style = SpendWiseTextStyles.Mono,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun EmptySection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = SpendWiseTheme.colors.onMuted,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

// ───────────────────────── Account strip ─────────────────────────

@Composable
private fun AccountStrip(
    cards: List<CardChip>,
    inr: NumberFormat,
    onCardClick: (String) -> Unit,
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
                    .clickable { onCardClick(card.last4) }
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpendWiseTheme.colors.surfaceElevated)
                    .padding(14.dp),
            ) {
                Text(
                    text = (card.issuer ?: "Unknown").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = SpendWiseTheme.colors.onMuted,
                )
                Text(
                    text = "••${card.last4}",
                    style = SpendWiseTextStyles.Mono.copy(fontWeight = FontWeight.Normal),
                    color = SpendWiseTheme.colors.onMuted,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = inr.formatCompact(card.totalInr),
                    style = SpendWiseTextStyles.Mono,
                )
            }
        }
    }
}

// ───────────────────────── Import status ─────────────────────────

@Composable
private fun ImportStatusBlock(status: ImportStatus) {
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
                color = SpendWiseTheme.colors.onMuted,
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
                trackColor = SpendWiseTheme.colors.surfaceElevated2,
            )
        }
        is ImportStatus.Succeeded -> {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Imported ${status.inserted} new, skipped ${status.duplicates} duplicates" +
                    if (status.failures > 0) ", ${status.failures} failed" else "",
                style = MaterialTheme.typography.bodySmall,
                color = SpendWiseTheme.colors.onMuted,
            )
        }
        is ImportStatus.Failed -> {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Import failed: ${status.message ?: "unknown error"}",
                style = MaterialTheme.typography.bodySmall,
                color = SpendWiseTheme.colors.negative,
            )
        }
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

// formatCompact() shared with CategoryDrillDownScreen.kt; defined there.

// ───────────────────────── Sort ─────────────────────────

/** Cycle: amount desc → amount asc → name A–Z → back. */
internal enum class CategorySort(val label: String) {
    AmountDesc("Amount ↓"),
    AmountAsc("Amount ↑"),
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
            style = SpendWiseTextStyles.Caps,
            color = SpendWiseTheme.colors.onFaint,
        )
        if (showSort) {
            Text(
                text = sortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = SpendWiseTheme.colors.onMuted,
                modifier = Modifier
                    .clickable(onClick = onCycle)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}
