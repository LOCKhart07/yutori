package com.spendwise.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.spendwise.SpendWiseApp
import com.spendwise.backup.SettingsBackup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Composable-friendly bundle of export/import launchers. The Settings
 * screen requests these via [rememberBackupActions] and wires the
 * returned lambdas to buttons.
 */
class BackupActions(
    val onExportClick: () -> Unit,
    val onImportClick: () -> Unit,
)

@Composable
fun rememberBackupActions(): BackupActions {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as SpendWiseApp
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) {
                SettingsBackup.exportToJson(
                    accountDao = app.database!!.accountDao(),
                    ruleDao = app.database!!.recipientRuleDao(),
                    nowMs = System.currentTimeMillis(),
                )
            }
            val ok = writeTextToUri(context, uri, json)
            toast(context, if (ok) "Settings exported." else "Export failed.")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) { readTextFromUri(context, uri) }
            if (json == null) {
                toast(context, "Couldn't read file.")
                return@launch
            }
            val summary = withContext(Dispatchers.IO) {
                SettingsBackup.importFromJson(
                    json = json,
                    accountDao = app.database!!.accountDao(),
                    ruleDao = app.database!!.recipientRuleDao(),
                    nowMs = System.currentTimeMillis(),
                )
            }
            val msg = buildString {
                append("Imported ${summary.accountsInserted} accounts")
                append(", ${summary.rulesInserted} rules")
                if (summary.accountsSkipped + summary.rulesSkipped > 0) {
                    append(" (${summary.accountsSkipped + summary.rulesSkipped} duplicates skipped)")
                }
                if (summary.rulesUnlinked > 0) {
                    append(", $summary.rulesUnlinked rules unlinked")
                }
            }
            toast(context, msg)
        }
    }

    return BackupActions(
        onExportClick = {
            val fname = "spendwise-settings-${defaultFilenameStamp()}.json"
            exportLauncher.launch(fname)
        },
        onImportClick = {
            importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
        },
    )
}

private fun defaultFilenameStamp(): String {
    val fmt = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
    return fmt.format(Date())
}

private fun writeTextToUri(context: Context, uri: Uri, content: String): Boolean =
    try {
        context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
            true
        } ?: false
    } catch (e: Exception) {
        android.util.Log.e("BackupActions", "Export write failed", e)
        false
    }

private fun readTextFromUri(context: Context, uri: Uri): String? =
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        android.util.Log.e("BackupActions", "Import read failed", e)
        null
    }

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
