package com.yutori.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yutori.budget.Budget
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme

/**
 * Onboarding step 4 (mockups/v24-onboarding-flow.html, frame 4).
 *
 * Single INR input + four preset chips. Save emits a [Budget] for the
 * caller to persist at [monthKey]; Skip leaves the budgets table
 * untouched and lets the dashboard show its existing "Set a monthly
 * budget" CTA (per ui-spec §5.3).
 */
@Composable
fun BudgetPromptScreen(
    monthKey: String,
    stepNumber: Int,
    stepCount: Int,
    onSave: (Budget) -> Unit,
    onSkip: () -> Unit,
) {
    var limitText by remember { mutableStateOf("") }
    val parsedLimit = limitText.trim().toDoubleOrNull()
    val saveEnabled = parsedLimit != null && parsedLimit > 0.0
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
                    .padding(top = 24.dp)
                    .padding(horizontal = 32.dp),
            ) {
                OnboardingProgressDots(
                    stepNumber = stepNumber,
                    stepCount = stepCount,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "STEP $stepNumber OF $stepCount",
                    style = YutoriTextStyles.Caps,
                    color = colors.onFaint,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Set your monthly budget",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "We'll alert you at 50%, 80%, and 100% of " +
                        "this number. Edit anytime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onMuted,
                )
                Spacer(Modifier.height(24.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp),
            ) {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { new ->
                        if (new.isEmpty() || new.matches(Regex("""\d+(\.\d*)?"""))) {
                            limitText = new
                        }
                    },
                    placeholder = {
                        Text(
                            "40000",
                            style = YutoriTextStyles.Mono.copy(
                                fontSize = 28.sp,
                                lineHeight = 32.sp,
                                color = colors.onFaint,
                            ),
                        )
                    },
                    prefix = {
                        Text(
                            "₹ ",
                            style = YutoriTextStyles.Mono.copy(
                                fontSize = 28.sp,
                                lineHeight = 32.sp,
                                color = colors.onMuted,
                            ),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = YutoriTextStyles.Mono.copy(
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
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
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Common starting points",
                    style = YutoriTextStyles.Caps,
                    color = colors.onFaint,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BUDGET_PRESETS.forEach { preset ->
                        BudgetPresetChip(
                            label = preset.label,
                            active = parsedLimit?.toLong() == preset.amountInr,
                            onClick = {
                                limitText = preset.amountInr.toString()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp),
            ) {
                PrimaryActionButton(
                    text = "Save & finish",
                    enabled = saveEnabled,
                    onClick = {
                        val limit = parsedLimit ?: return@PrimaryActionButton
                        onSave(
                            Budget(
                                monthKey = monthKey,
                                limitInr = limit,
                                warnThresholdPct = DEFAULT_WARN_PCT,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Skip — set later",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSkip)
                        .padding(vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private data class BudgetPreset(val label: String, val amountInr: Long)

private val BUDGET_PRESETS = listOf(
    BudgetPreset("₹20k", 20_000L),
    BudgetPreset("₹40k", 40_000L),
    BudgetPreset("₹60k", 60_000L),
    BudgetPreset("₹1L", 100_000L),
)

private const val DEFAULT_WARN_PCT = 80

@Composable
private fun BudgetPresetChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YutoriTheme.colors
    val borderColor = if (active) colors.onFaint else androidx.compose.ui.graphics.Color.Transparent
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated),
        color = colors.surfaceElevated,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (active) MaterialTheme.colorScheme.onBackground else colors.onMuted,
            textAlign = TextAlign.Center,
        )
    }
}
