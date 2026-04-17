package com.yutori.ui

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yutori.ui.theme.YutoriTheme
import kotlinx.coroutines.delay
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Full-screen recovery surface shown when Room migration / DB open
 * throws on app start. Per error-states-spec §5.1 and §5.5 (the latter
 * — SQLiteDatabaseCorruptException — uses the same screen).
 *
 * Two actions:
 *   - "Copy error details" → full stack trace to clipboard, for a bug
 *     report.
 *   - "Clear app data" → wipes the Room DB + settings via
 *     ActivityManager.clearApplicationUserData() after a confirm
 *     dialog. First launch after is onboarding; Android SMS inbox is
 *     untouched, so re-import rebuilds history.
 *
 * Details are collapsed by default behind a "Show error details" link;
 * the copy button still includes the full trace regardless of the
 * collapsed/expanded state.
 */
@Composable
fun MigrationErrorScreen(error: Throwable) {
    val context = LocalContext.current
    var detailsExpanded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var justCopied by remember { mutableStateOf(false) }
    val errorText = remember(error) { stackTraceString(error) }
    val statusInset = WindowInsets.statusBars.asPaddingValues()
    val negative = YutoriTheme.colors.negative

    if (justCopied) {
        LaunchedEffect(Unit) {
            delay(2_000)
            justCopied = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusInset.calculateTopPadding())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Spacer(Modifier.size(24.dp))

            // Icon
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(negative.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "!",
                    color = negative,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            Spacer(Modifier.size(20.dp))

            // Title
            Text(
                "Yutori couldn't upgrade your data.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.size(12.dp))

            // Body — copy updated from error-states-spec §5.1 to only
            // reference actions the UI actually exposes (#85).
            Text(
                "This shouldn't happen. Reinstall the APK you came from, " +
                    "or clear app data to start fresh (your Android SMS " +
                    "inbox is untouched).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.size(20.dp))

            // Details expander
            Text(
                if (detailsExpanded) "▾  Hide error details" else "▸  Show error details",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { detailsExpanded = !detailsExpanded }
                    .padding(vertical = 4.dp, horizontal = 8.dp),
            )

            if (detailsExpanded) {
                Spacer(Modifier.size(12.dp))
                // Column-with-verticalScroll (not Box) so long traces
                // scroll reliably via drag — the previous Box variant
                // sometimes swallowed the gesture (#85).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        errorText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Actions — destructive-first, filled; safe Copy action is
            // demoted to an outlined button underneath (#83).
            Button(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = negative,
                    contentColor = Color.White,
                ),
            ) {
                Text("Clear app data")
            }

            Spacer(Modifier.size(8.dp))

            OutlinedButton(
                onClick = {
                    copyToClipboard(context, errorText)
                    justCopied = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (justCopied) "✓ Copied" else "Copy error details")
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear app data?") },
            text = {
                Text(
                    "This deletes all transactions, budgets, and account " +
                        "settings on this device. Your Android SMS inbox " +
                        "is untouched — re-import after clearing to rebuild.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        clearAppData(context)
                    },
                ) {
                    Text("Clear data", color = negative, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

internal fun stackTraceString(t: Throwable): String {
    val sw = StringWriter()
    t.printStackTrace(PrintWriter(sw))
    return sw.toString()
}

private fun copyToClipboard(context: Context, text: String) {
    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clip.setPrimaryClip(ClipData.newPlainText("Yutori migration error", text))
    // In-app feedback is handled by the calling composable (button
    // label flips to "✓ Copied" for 2s). Android 13+ also shows a
    // system clipboard chip; the in-app feedback is the authoritative
    // signal the user can't miss.
}

private fun clearAppData(context: Context) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    // Kills the process + wipes /data/data/<package>/* then relaunches.
    // Returns true if the OS honored the request; false is rare (root
    // denial, etc.) — we don't have a graceful fallback, so surface a
    // toast and let the user clear via Settings.
    val ok = am.clearApplicationUserData()
    if (!ok) {
        Toast.makeText(
            context,
            "Couldn't clear automatically. Open Android Settings → Apps → Yutori → Storage → Clear data.",
            Toast.LENGTH_LONG,
        ).show()
    }
}
