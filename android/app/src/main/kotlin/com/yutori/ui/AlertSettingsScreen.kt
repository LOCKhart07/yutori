package com.yutori.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yutori.settings.ImpactAlertSettings
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme

/**
 * "Alert thresholds" screen — first concrete content is the per-tx
 * impact-notification toggle + threshold (mockup
 * `mockups/v3-behavioral.html` §2). Cumulative-threshold settings move
 * here later when the rest of the alert-tuning UI lands.
 */
@Composable
fun AlertSettingsScreen(
    settings: ImpactAlertSettings,
    onBack: () -> Unit,
    warnThresholdPct: Int = 80,
) {
    val cfg by settings.state.collectAsStateWithLifecycle(initialValue = settings.get())
    val statusInset: PaddingValues = WindowInsets.statusBars.asPaddingValues()
    val colors = YutoriTheme.colors

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusInset.calculateTopPadding() + 8.dp)
                .padding(horizontal = 24.dp),
        ) {
            BackRow(label = "Settings", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            Text("Alert thresholds", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tune when Yutori pings you. The cumulative budget " +
                    "alerts (50%, $warnThresholdPct%, 100%) live on the " +
                    "budget setup screen for now.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onMuted,
            )
            Spacer(Modifier.height(20.dp))

            Text(
                text = "PER-TRANSACTION ALERTS",
                style = YutoriTextStyles.Caps,
                color = colors.onFaint,
            )
            Spacer(Modifier.height(8.dp))

            ImpactCard(
                cfg = cfg,
                onToggle = { settings.setEnabled(it) },
                onThresholdChange = { settings.setThresholdPct(it) },
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = if (cfg.enabled) {
                    "Big-spend alerts are ON. You'll get a push when a " +
                        "single transaction is at least ${cfg.thresholdPct}% " +
                        "of your monthly effective budget."
                } else {
                    "Big-spend alerts are OFF. Toggle on to be nudged " +
                        "when a single transaction takes a noticeable bite " +
                        "out of the month."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onMuted,
            )
        }
    }
}

@Composable
private fun ImpactCard(
    cfg: ImpactAlertSettings.Config,
    onToggle: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
) {
    val colors = YutoriTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Notify on big spends",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "When a single tx is a noticeable chunk of your " +
                            "budget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onMuted,
                    )
                }
                Switch(
                    checked = cfg.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = colors.onMuted,
                        uncheckedTrackColor = colors.surfaceElevated2,
                        uncheckedBorderColor = colors.divider,
                    ),
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Threshold",
                    style = YutoriTextStyles.Caps,
                    color = colors.onFaint,
                )
                Text(
                    "${cfg.thresholdPct}%",
                    style = YutoriTextStyles.Mono.copy(fontWeight = FontWeight.Medium),
                    color = if (cfg.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        colors.onMuted
                    },
                )
            }
            Spacer(Modifier.height(2.dp))
            Slider(
                value = cfg.thresholdPct.toFloat(),
                onValueChange = { onThresholdChange(it.toInt()) },
                valueRange = ImpactAlertSettings.MIN_PCT.toFloat()..
                    ImpactAlertSettings.MAX_PCT.toFloat(),
                steps = ImpactAlertSettings.MAX_PCT - ImpactAlertSettings.MIN_PCT - 1,
                enabled = cfg.enabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = colors.surfaceElevated2,
                    activeTickColor = colors.divider,
                    inactiveTickColor = colors.divider,
                    disabledThumbColor = colors.onFaint,
                    disabledActiveTrackColor = colors.surfaceElevated2,
                    disabledInactiveTrackColor = colors.surfaceElevated2,
                ),
            )
            Text(
                "Fire when a single transaction is ≥ this percent of " +
                    "your monthly limit.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onMuted,
            )
        }
    }
}
