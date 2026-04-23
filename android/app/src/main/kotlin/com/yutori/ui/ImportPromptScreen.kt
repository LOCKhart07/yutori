package com.yutori.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Onboarding step 3 (mockups/v24-onboarding-flow.html, frame 3).
 *
 * Shown only when READ_SMS was granted in step 2 — otherwise we have
 * nothing to read. Options mirror [ImportDialog] exactly so the
 * onboarding path and the in-app "Import past SMS" path produce the
 * same enqueue payload (range labels + the null-days = "everything"
 * sentinel).
 */
@Composable
fun ImportPromptScreen(
    stepNumber: Int,
    stepCount: Int,
    onStartImport: (sinceMs: Long) -> Unit,
    onSkip: () -> Unit,
) {
    val options = remember { ImportRangeOptions }
    var selectedIndex by remember { mutableIntStateOf(DEFAULT_INDEX) }
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
                    text = "Import past SMS",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Reads your SMS history and extracts " +
                        "transactions. Already-imported messages are " +
                        "skipped automatically.",
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
                options.forEachIndexed { i, option ->
                    OnboardingRadioRow(
                        label = option.label,
                        selected = i == selectedIndex,
                        onClick = { selectedIndex = i },
                    )
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
                    text = "Start import",
                    onClick = {
                        val sinceMs = sinceMsFor(options[selectedIndex])
                        onStartImport(sinceMs)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Skip — don't import",
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

/**
 * Range options shown on both [ImportDialog] and [ImportPromptScreen].
 * Kept as one source of truth so the onboarding path and the in-app
 * path can't drift. `daysBack = null` means "everything on this
 * phone" — encoded as `sinceMs = 0L` at enqueue time.
 */
internal data class ImportRangeOption(val label: String, val daysBack: Int?)

internal val ImportRangeOptions: List<ImportRangeOption> = listOf(
    ImportRangeOption("Last 1 month", 30),
    ImportRangeOption("Last 3 months", 90),
    ImportRangeOption("Last 6 months", 180),
    ImportRangeOption("Last 1 year", 365),
    ImportRangeOption("Everything on this phone", null),
)

internal fun sinceMsFor(
    option: ImportRangeOption,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    val days = option.daysBack ?: return 0L
    return Instant.ofEpochMilli(nowMs)
        .atZone(zone)
        .minus(days.toLong(), ChronoUnit.DAYS)
        .toInstant()
        .toEpochMilli()
}

private const val DEFAULT_INDEX = 1   // "Last 3 months"

@Composable
private fun OnboardingRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = YutoriTheme.colors
    val accent = MaterialTheme.colorScheme.primary
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
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) accent else colors.onFaint,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                colors.onMuted
            },
        )
    }
}
