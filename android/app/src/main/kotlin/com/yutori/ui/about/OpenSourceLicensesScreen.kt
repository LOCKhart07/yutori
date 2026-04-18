package com.yutori.ui.about

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yutori.ui.BackRow
import com.yutori.ui.theme.YutoriTheme

private const val APACHE_2_URL = "https://www.apache.org/licenses/LICENSE-2.0"

/**
 * About → Open-source licenses. Lists Yutori's runtime dependencies
 * (see [openSourceLibraries]) and points to the canonical Apache 2.0
 * text online rather than bundling it in the APK — every listed
 * library ships under Apache 2.0 and the license text is stable
 * upstream. See `plans/about-screen-spec.md` §2.2.
 */
@Composable
fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current

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
                BackRow(label = "About", onBack = onBack)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Open-source licenses",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Yutori is built on these open-source projects. " +
                        "All of them ship under the Apache License 2.0.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YutoriTheme.colors.onMuted,
                )
                Spacer(Modifier.height(20.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                openSourceLibraries.forEach { entry ->
                    LicenseRow(entry)
                    Spacer(Modifier.height(6.dp))
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(YutoriTheme.colors.surfaceElevated)
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(APACHE_2_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Apache License 2.0",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        Text(
                            text = "apache.org/licenses/LICENSE-2.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = YutoriTheme.colors.onMuted,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = YutoriTheme.colors.onFaint,
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun LicenseRow(entry: LicenseEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(YutoriTheme.colors.surfaceElevated)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = entry.license,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = YutoriTheme.colors.onMuted,
        )
    }
}
