package com.yutori.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yutori.budget.Budget
import com.yutori.budget.SpendSuggestion
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Budget setup — v2 styling. Mono limit input, amber accent slider,
 * primary CTA matches the dashboard "Set budget" button.
 */
@Composable
fun BudgetSetupScreen(
    monthKey: String,
    currentBudget: Budget?,
    onSave: (Budget) -> Unit,
    onCancel: () -> Unit,
    inheritedFromMonthKey: String? = null,
    carryOverInr: Double = 0.0,
    suggestion: SpendSuggestion? = null,
) {
    // #80: the caller loads `currentBudget` asynchronously (initial
    // null, then the stored Budget once the DAO resolves). Keying the
    // `remember` on `currentBudget` re-seeds the field when the real
    // value arrives, instead of latching to the null → "" path (the
    // field was *always* empty on open; the hard-coded placeholder
    // "45000" just made it look like the default loaded correctly).
    var limitText by remember(currentBudget) {
        mutableStateOf(currentBudget?.limitInr?.let { "%.0f".format(it) } ?: "")
    }
    var warnPct by remember(currentBudget) {
        mutableStateOf(currentBudget?.warnThresholdPct?.toFloat() ?: 80f)
    }
    // #15: the history suggestion is a standing affordance — shown
    // whenever a median exists, independent of inheritance. Dismissed
    // once used or once the user types their own number. Re-keyed on
    // currentBudget so an async re-seed (see #80 above) resets it.
    var suggestionDismissed by remember(currentBudget) { mutableStateOf(false) }

    val parsedLimit = limitText.trim().toDoubleOrNull()
    val saveEnabled = parsedLimit != null && parsedLimit >= 0.0

    val colors = YutoriTheme.colors
    val inr = remember {
        NumberFormat.getCurrencyInstance(
            Locale.Builder().setLanguage("en").setRegion("IN").build(),
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp)
                    .padding(horizontal = 24.dp),
            ) {
                BackRow(label = "Dashboard", onBack = onCancel)
                Spacer(Modifier.height(16.dp))

                Text(
                    text = prettyMonthBudget(monthKey).uppercase(),
                    style = YutoriTextStyles.Caps,
                    color = colors.onMuted,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Set budget",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(8.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    text = "How much you want to spend this month. Carry-over from " +
                        "prior months gets added automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onMuted,
                )

                Spacer(Modifier.height(28.dp))

                // Mono input
                Text(
                    text = "Monthly limit (INR)",
                    style = YutoriTextStyles.Caps,
                    color = colors.onFaint,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { new ->
                        if (new.isEmpty() || new.matches(Regex("""\d+(\.\d*)?"""))) {
                            limitText = new
                            // Typing your own number retires the suggestion.
                            suggestionDismissed = true
                        }
                    },
                    placeholder = {
                        Text(
                            "45000",
                            style = YutoriTextStyles.Mono.copy(
                                fontSize = 28.sp, lineHeight = 32.sp,
                                color = colors.onFaint,
                            ),
                        )
                    },
                    prefix = {
                        Text(
                            "₹ ",
                            style = YutoriTextStyles.Mono.copy(
                                fontSize = 28.sp, lineHeight = 32.sp,
                                color = colors.onMuted,
                            ),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = YutoriTextStyles.Mono.copy(
                        fontSize = 28.sp, lineHeight = 32.sp,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = colors.divider,
                        focusedContainerColor = colors.surfaceElevated,
                        unfocusedContainerColor = colors.surfaceElevated,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (parsedLimit != null && parsedLimit < 0.0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Limit must be ≥ 0.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.negative,
                    )
                }

                // #14: when the pre-fill came from a prior month's row
                // (inheritance), surface the source + what Save does.
                if (inheritedFromMonthKey != null) {
                    Spacer(Modifier.height(10.dp))
                    InheritedBudgetNote(
                        sourceMonthKey = inheritedFromMonthKey,
                        targetMonthKey = monthKey,
                    )
                }

                // #15: tap-to-fill suggestion from spend history. Sits
                // below the inheritance note (info) as the actionable
                // alternative — green vs the periwinkle note. Hidden when
                // the field already equals it, or once used/typed-over.
                val sug = suggestion
                if (sug != null && shouldShowSuggestion(sug, limitText, suggestionDismissed)) {
                    Spacer(Modifier.height(10.dp))
                    BudgetSuggestionChip(
                        leadIn = suggestionLeadIn(hasPrefill = currentBudget != null),
                        amountText = inr.formatAmount(sug.median, compact = true),
                        basisText = suggestionBasisText(sug),
                        onUse = {
                            limitText = "%.0f".format(sug.median)
                            suggestionDismissed = true
                        },
                    )
                }

                // Reconciles the dashboard's *effective* number with the
                // limit you set: effectiveBudget = limit + carryOver
                // (business-logic-spec §6.1). Hidden when there's nothing
                // to explain (no carry-over / no parseable limit).
                val breakdown = budgetBreakdown(parsedLimit, carryOverInr)
                if (breakdown != null) {
                    Spacer(Modifier.height(10.dp))
                    CarryOverBreakdown(breakdown)
                }

                Spacer(Modifier.height(28.dp))

                // Warn threshold slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Warning threshold",
                        style = YutoriTextStyles.Caps,
                        color = colors.onFaint,
                    )
                    Text(
                        text = "${warnPct.toInt()}%",
                        style = YutoriTextStyles.Mono.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = warnPct,
                    onValueChange = { warnPct = it },
                    valueRange = 60f..95f,
                    steps = 6,    // 60, 65, 70, 75, 80, 85, 90, 95
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = colors.surfaceElevated2,
                        activeTickColor = colors.divider,
                        inactiveTickColor = colors.divider,
                    ),
                )
                Text(
                    text = "Alerts fire at 50%, ${warnPct.toInt()}%, and 100% " +
                        "(plus every +10% over).",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onMuted,
                )

                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SecondaryButton(
                        text = "Cancel",
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryActionButton(
                        text = "Save",
                        enabled = saveEnabled,
                        onClick = {
                            val limit = parsedLimit ?: return@PrimaryActionButton
                            onSave(
                                Budget(
                                    monthKey = monthKey,
                                    limitInr = limit,
                                    warnThresholdPct = warnPct.toInt(),
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ── Shared button components ──────────────────────────────────────

@Composable
internal fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val bg = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        YutoriTheme.colors.surfaceElevated2
    }
    val fg = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        YutoriTheme.colors.onFaint
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = bg,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = fg,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = YutoriTheme.colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = YutoriTheme.colors.divider,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = YutoriTheme.colors.onMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private fun prettyMonthBudget(monthKey: String): String = try {
    val (y, m) = monthKey.split("-").let { it[0] to it[1].toInt() }
    val name = listOf(
        "January","February","March","April","May","June",
        "July","August","September","October","November","December",
    )[m - 1]
    "$name $y"
} catch (_: Exception) { monthKey }

/**
 * #14 pre-fill provenance. Shown under the limit input only when the
 * pre-filled values came from a prior month's row (inheritance),
 * never when the viewed month has its own explicit row.
 */
@Composable
private fun InheritedBudgetNote(
    sourceMonthKey: String,
    targetMonthKey: String,
) {
    val colors = YutoriTheme.colors
    val source = prettyMonthBudget(sourceMonthKey)
    val target = prettyMonthBudget(targetMonthKey)
    Surface(
        color = colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = colors.divider,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = colors.onMuted,
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 2.dp),
            )
            Text(
                text = "Pre-filled from $source. Save to create an explicit $target row.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onMuted,
            )
        }
    }
}

/**
 * What the carry-over breakdown card shows. Pure so it's unit-testable
 * without Compose.
 */
internal data class BudgetBreakdown(
    val limitInr: Double,
    val carryOverInr: Double,
    val effectiveInr: Double,
)

/**
 * The dashboard headline is the *effective* budget
 * (`effectiveBudget = limit + carryOver`, business-logic-spec §6.1),
 * but this editor only sets the *limit* — so tapping the dashboard
 * number and seeing a different one here looks broken. This produces
 * the reconciling breakdown.
 *
 * Returns null (⇒ card hidden, screen stays quiet) when there is
 * nothing to explain: the limit isn't parseable/valid, or carry-over
 * is zero (§6.6 — no prior budget rows / first budgeted month).
 */
internal fun budgetBreakdown(
    limitInr: Double?,
    carryOverInr: Double,
): BudgetBreakdown? {
    if (limitInr == null || limitInr < 0.0) return null
    if (carryOverInr == 0.0) return null
    return BudgetBreakdown(
        limitInr = limitInr,
        carryOverInr = carryOverInr,
        effectiveInr = limitInr + carryOverInr,
    )
}

@Composable
private fun CarryOverBreakdown(breakdown: BudgetBreakdown) {
    val colors = YutoriTheme.colors
    val inr = remember {
        NumberFormat.getCurrencyInstance(
            Locale.Builder().setLanguage("en").setRegion("IN").build(),
        )
    }
    val surplus = breakdown.carryOverInr > 0.0
    Surface(
        color = colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            BreakdownRow(
                label = "Limit",
                value = inr.formatAmount(breakdown.limitInr, compact = true),
                valueColor = colors.onMuted,
            )
            Spacer(Modifier.height(8.dp))
            BreakdownRow(
                label = "Carried over",
                value = (if (surplus) "+ " else "− ") +
                    inr.formatAmount(abs(breakdown.carryOverInr), compact = true),
                valueColor = if (surplus) colors.positive else colors.negative,
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = colors.divider)
            Spacer(Modifier.height(10.dp))
            BreakdownRow(
                label = "Effective this month",
                value = inr.formatAmount(breakdown.effectiveInr, compact = true),
                valueColor = MaterialTheme.colorScheme.onBackground,
                emphasize = true,
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    emphasize: Boolean = false,
) {
    val colors = YutoriTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (emphasize) {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (emphasize) MaterialTheme.colorScheme.onBackground else colors.onMuted,
        )
        Text(
            text = value,
            style = if (emphasize) {
                YutoriTextStyles.Mono.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            } else {
                YutoriTextStyles.Mono
            },
            color = valueColor,
        )
    }
}

// ── #15 budget suggestion from history ────────────────────────────

/**
 * Whether the suggestion chip should render. Hidden when there is no
 * suggestion, once it's been used/dismissed, or when the field already
 * shows the suggested value (no point offering a no-op). Comparison is
 * on the displayed integer-rupee form, matching what tapping writes.
 */
internal fun shouldShowSuggestion(
    suggestion: SpendSuggestion?,
    currentLimitText: String,
    dismissed: Boolean,
): Boolean {
    if (suggestion == null || dismissed) return false
    return "%.0f".format(suggestion.median) != currentLimitText.trim()
}

/**
 * Lead-in word for the chip. "Or use" when the field is already
 * pre-filled (a saved or inherited value the suggestion is an
 * alternative to); "Suggested" when the field is empty.
 */
internal fun suggestionLeadIn(hasPrefill: Boolean): String =
    if (hasPrefill) "Or use" else "Suggested"

/**
 * The chip's caption, e.g. "median of your last 3 months · Mar 40k ·
 * Apr 45k · May 42k". Months are rendered chronologically (the
 * [SpendSuggestion] holds them newest-first) so the breakdown reads
 * left-to-right in time.
 */
internal fun suggestionBasisText(suggestion: SpendSuggestion): String {
    val n = suggestion.months.size
    val breakdown = suggestion.months
        .sortedBy { it.monthKey }
        .joinToString(" · ") { "${shortMonth(it.monthKey)} ${thousandsK(it.netInr)}" }
    return "median of your last $n month${if (n == 1) "" else "s"} · $breakdown"
}

private fun shortMonth(monthKey: String): String = try {
    val m = monthKey.split("-")[1].toInt()
    listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )[m - 1]
} catch (_: Exception) {
    monthKey
}

/** Compact rupee-thousands for the breakdown line: 42_300 → "42k". */
private fun thousandsK(amount: Double): String = "${(amount / 1000.0).roundToLong()}k"

@Composable
private fun BudgetSuggestionChip(
    leadIn: String,
    amountText: String,
    basisText: String,
    onUse: () -> Unit,
) {
    val colors = YutoriTheme.colors
    Surface(
        color = colors.positive.copy(alpha = 0.07f),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.positive.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onUse),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = colors.positive,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                    Text(
                        text = "$leadIn ",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.positive,
                    )
                    Text(
                        text = amountText,
                        style = YutoriTextStyles.Mono.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = basisText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onMuted,
                )
            }
            Text(
                text = "Use",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.positive,
            )
        }
    }
}
