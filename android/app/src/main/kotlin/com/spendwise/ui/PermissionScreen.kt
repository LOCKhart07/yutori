package com.spendwise.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendwise.ui.theme.SpendWiseTextStyles
import com.spendwise.ui.theme.SpendWiseTheme

/**
 * Onboarding permission screen per mockups/v2.html frame 8. Single
 * step shown whenever SMS perms aren't granted. Same visual grammar
 * as Dashboard — surface elevated rows, amber primary CTA, tight
 * typography.
 */
@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { perms ->
        if (perms.values.all { it }) onGranted()
    }

    val statusInset: PaddingValues = WindowInsets.statusBars.asPaddingValues()
    val colors = SpendWiseTheme.colors

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = statusInset.calculateTopPadding() + 24.dp)
                .padding(horizontal = 32.dp),
        ) {
            Text(
                text = "STEP 1 OF 1",
                style = SpendWiseTextStyles.Caps,
                color = colors.onFaint,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Let SpendWise\nread your SMSes.",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Everything stays on this device. SpendWise never sends " +
                    "your messages anywhere — bank SMSes are parsed locally " +
                    "to build your spending record.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onMuted,
            )

            Spacer(Modifier.height(32.dp))

            PermCard(
                glyph = "✉",
                title = "Receive SMS",
                detail = "Catch new bank messages as they arrive.",
            )
            Spacer(Modifier.height(12.dp))
            PermCard(
                glyph = "✓",
                title = "Read SMS inbox",
                detail = "Import past transactions from your phone's SMS history.",
            )
            Spacer(Modifier.height(12.dp))
            PermCard(
                glyph = "!",
                title = "Post notifications",
                detail = "Alert you at 50%, 80%, and 100% of your budget.",
            )

            Spacer(Modifier.height(40.dp))

            // Primary CTA — fills-width amber pill
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        launcher.launch(Permissions.runtimePermissionsToRequest())
                    },
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "Grant permissions",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermCard(glyph: String, title: String, detail: String) {
    val colors = SpendWiseTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.surfaceElevated2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = glyph,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onMuted,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onMuted,
            )
        }
    }
}
