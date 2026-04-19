package com.yutori.ui.update

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yutori.ui.theme.YutoriTextStyles
import com.yutori.ui.theme.YutoriTheme
import com.yutori.update.UpdateCheckError

/**
 * Settings card + dedicated "Check now" button.
 *
 * Rendered inside [com.yutori.ui.SettingsScreen] as the last section.
 * The host is responsible for showing [UpdateDialog] when
 * [UpdateScreenState.dialogVisible] is true — this composable only
 * surfaces the card and kicks callbacks.
 */
@Composable
fun AppUpdatesSection(
    state: UpdateScreenState,
    onToggleCheckOnOpen: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onOpenDialog: () -> Unit,
) {
    Column {
        Text(
            text = "APP UPDATES",
            style = YutoriTextStyles.Caps,
            color = YutoriTheme.colors.onFaint,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            color = YutoriTheme.colors.surfaceElevated,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Current version",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = state.currentVersion,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = YutoriTheme.colors.onMuted,
                    )
                }
                HorizontalDivider(color = YutoriTheme.colors.divider)

                val statusClickable = state.phase is UpdateScreenState.Phase.Available
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (statusClickable) Modifier.clickable(onClick = onOpenDialog) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    StatusLine(phase = state.phase)
                }
                HorizontalDivider(color = YutoriTheme.colors.divider)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Check on app open",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.checkOnOpenEnabled,
                        onCheckedChange = onToggleCheckOnOpen,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = YutoriTheme.colors.info,
                        ),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            CheckNowButton(
                phase = state.phase,
                onClick = onCheckNow,
            )
        }
    }
}

@Composable
private fun StatusLine(phase: UpdateScreenState.Phase) {
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    when (phase) {
        is UpdateScreenState.Phase.NotCheckedYet ->
            Text("Not checked yet", style = mono, color = YutoriTheme.colors.onMuted)
        is UpdateScreenState.Phase.Checking ->
            Text("Checking…", style = mono, color = YutoriTheme.colors.info)
        is UpdateScreenState.Phase.UpToDate ->
            Text("Up to date", style = mono, color = YutoriTheme.colors.positive)
        is UpdateScreenState.Phase.Available ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Update available: ${phase.release.tagName.removePrefix("v")}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        is UpdateScreenState.Phase.Downloading ->
            Text("Downloading…", style = mono, color = YutoriTheme.colors.info)
        is UpdateScreenState.Phase.DownloadFailed ->
            Text("Download failed", style = mono, color = YutoriTheme.colors.negative)
        is UpdateScreenState.Phase.InstallFailed ->
            Text("Install failed", style = mono, color = YutoriTheme.colors.negative)
        is UpdateScreenState.Phase.ErrorChecking -> {
            // Generic phrasing + the concrete tag so future-me can
            // diagnose from a screenshot. Status codes are mapped to
            // causes in docs/RELEASING.md "Updater status codes".
            val tag = when (val r = phase.reason) {
                is UpdateCheckError.Http -> r.code.toString()
                UpdateCheckError.Offline -> "offline"
            }
            Text("Couldn't check ($tag)", style = mono, color = YutoriTheme.colors.negative)
        }
    }
}

@Composable
private fun CheckNowButton(phase: UpdateScreenState.Phase, onClick: () -> Unit) {
    val enabled = phase !is UpdateScreenState.Phase.Checking &&
        phase !is UpdateScreenState.Phase.Downloading
    val label = when (phase) {
        is UpdateScreenState.Phase.Checking -> "Checking…"
        is UpdateScreenState.Phase.ErrorChecking -> "Try again"
        else -> "Check now"
    }
    val tint = if (enabled) YutoriTheme.colors.info else YutoriTheme.colors.onFaint
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .border(
                width = 1.dp,
                color = if (enabled) tint.copy(alpha = 0.35f) else YutoriTheme.colors.divider,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
        )
    }
}
