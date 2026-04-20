package com.yutori.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yutori.ui.theme.YutoriTheme
import java.text.DateFormat
import java.util.Date

/**
 * Settings → AI-assisted rules surface. Six states per
 * `mockups/v17-ai-rules.html` §A: off, opt-in, downloading, installed,
 * toggle-on-no-model, and download-failed.
 */
@Composable
fun AiSettingsScreen(
    state: AiSettingsUiState,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onConfirmOptIn: () -> Unit,
    onDismissOptIn: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: () -> Unit,
    onRetryDownload: () -> Unit,
) {
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
                BackRow(label = "Settings", onBack = onBack)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "AI-assisted rules",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(20.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToggleRow(
                    enabled = state.enabled,
                    onToggle = onToggle,
                )

                if (state.enabled) {
                    ModelCard(
                        modelState = state.modelState,
                        onStartDownload = onStartDownload,
                        onCancelDownload = onCancelDownload,
                        onDeleteModel = onDeleteModel,
                        onRetryDownload = onRetryDownload,
                    )
                }
            }
        }

        if (state.optInSheetVisible) {
            OptInSheet(
                onConfirm = onConfirmOptIn,
                onCancel = onDismissOptIn,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colors = YutoriTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AI-assisted rules",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Describe a rule in plain English; AI suggests a merchant pattern. " +
                    "Runs entirely on your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onMuted,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(),
        )
    }
}

@Composable
private fun ModelCard(
    modelState: ModelUiState,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: () -> Unit,
    onRetryDownload: () -> Unit,
) {
    val colors = YutoriTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
    ) {
        Text(
            text = "Deep",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "gemma-4-E2B · 2.58 GB",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = colors.onFaint,
        )
        Spacer(Modifier.height(12.dp))

        when (modelState) {
            ModelUiState.Absent -> {
                Text(
                    text = "Not downloaded yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onMuted,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onStartDownload) { Text("Download model") }
            }

            is ModelUiState.Downloading -> {
                val pct = if (modelState.total > 0) {
                    modelState.downloaded.toFloat() / modelState.total
                } else {
                    0f
                }
                Text(
                    text = "Downloading · ${formatBytes(modelState.downloaded)} " +
                        "of ${formatBytes(modelState.total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onMuted,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { pct.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onCancelDownload) { Text("Cancel") }
            }

            is ModelUiState.Ready -> {
                Text(
                    text = "Ready · Installed ${relativeDays(modelState.installedAtMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDeleteModel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete model")
                }
            }

            is ModelUiState.Failed -> {
                Text(
                    text = when (modelState.reason) {
                        FailureReason.Checksum ->
                            "Download failed · checksum mismatch"
                        FailureReason.Network ->
                            "Download failed · ${modelState.message ?: "network error"}"
                        FailureReason.Cancelled ->
                            "Download cancelled"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetryDownload) { Text("Retry") }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun OptInSheet(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onCancel,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Enable AI-assisted rules?",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "We'll download the Deep model (~2.58 GB) once. It runs entirely on " +
                    "your phone — no data leaves the device. Wi-Fi is recommended.",
                style = MaterialTheme.typography.bodyMedium,
                color = YutoriTheme.colors.onMuted,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                ) { Text("Continue") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val gib = bytes.toDouble() / (1024.0 * 1024 * 1024)
    return "%.2f GB".format(gib)
}

private fun relativeDays(installedAtMs: Long): String {
    val now = System.currentTimeMillis()
    val days = ((now - installedAtMs) / (1000L * 60 * 60 * 24)).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 30 -> "$days days ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(installedAtMs))
    }
}

/** Immutable state the screen renders. */
data class AiSettingsUiState(
    val enabled: Boolean,
    val modelState: ModelUiState,
    val optInSheetVisible: Boolean,
)

sealed interface ModelUiState {
    data object Absent : ModelUiState
    data class Downloading(val downloaded: Long, val total: Long) : ModelUiState
    data class Ready(val installedAtMs: Long) : ModelUiState
    data class Failed(val reason: FailureReason, val message: String?) : ModelUiState
}

enum class FailureReason { Checksum, Network, Cancelled }
