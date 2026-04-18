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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme

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

    val parsedLimit = limitText.trim().toDoubleOrNull()
    val saveEnabled = parsedLimit != null && parsedLimit >= 0.0

    val colors = YutoriTheme.colors

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
