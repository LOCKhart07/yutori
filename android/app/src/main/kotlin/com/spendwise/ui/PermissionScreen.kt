package com.spendwise.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.spendwise.ui.theme.SpendWiseTextStyles
import com.spendwise.ui.theme.SpendWiseTheme

/**
 * Onboarding permission gate. Two states:
 *
 * 1. Initial gate (mockups/v2.html frame 8) — the standard "Let Yutori
 *    read your SMSes" card stack with a Grant CTA.
 * 2. Restricted-settings guidance (mockups/v6-restricted-settings.html)
 *    — shown when the launcher callback reports a denied permission
 *    whose `shouldShowRequestPermissionRationale` is false. Covers both
 *    the Android 13+ sideload block (system swallows the request
 *    without ever showing a dialog) and the "don't ask again" dead-end.
 *    Primary CTA deep-links to the system app-info page so the user
 *    can flip "Allow restricted settings"; Try again re-fires the
 *    permission launcher without leaving Yutori.
 */
@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var showGuidance by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { perms ->
        if (perms.values.all { it }) {
            onGranted()
            return@rememberLauncherForActivityResult
        }
        // Denied. If the OS won't even show the dialog any more
        // (restricted settings on Android 13+, or "don't ask again"
        // after prior denials), surface the app-info detour.
        val blocked = activity != null && perms.any { (perm, granted) ->
            !granted &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }
        if (blocked) showGuidance = true
    }

    if (showGuidance) {
        RestrictedSettingsGuidance(
            onOpenAppInfo = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            onRetry = {
                showGuidance = false
                launcher.launch(Permissions.runtimePermissionsToRequest())
            },
        )
    } else {
        InitialGate(
            onGrant = { launcher.launch(Permissions.runtimePermissionsToRequest()) },
        )
    }
}

@Composable
private fun InitialGate(onGrant: () -> Unit) {
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
                glyph = "⟲",
                title = "Read SMS inbox",
                detail = "Import past transactions from your phone's SMS history.",
            )
            Spacer(Modifier.height(12.dp))
            PermCard(
                glyph = "◉",
                title = "Post notifications",
                detail = "Alert you at 50%, 80%, and 100% of your budget.",
            )

            Spacer(Modifier.height(40.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onGrant),
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

@Composable
private fun RestrictedSettingsGuidance(
    onOpenAppInfo: () -> Unit,
    onRetry: () -> Unit,
) {
    val statusInset: PaddingValues = WindowInsets.statusBars.asPaddingValues()
    val colors = SpendWiseTheme.colors
    val sideloadNote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        "Android 13+ restricts sideloaded apps from requesting some " +
            "permissions without this manual opt-in."
    } else {
        "Android has blocked the permission request — flip the toggle " +
            "in App info and try again."
    }

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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.info.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "i",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.info,
                )
            }
            Spacer(Modifier.height(18.dp))

            Text(
                text = "ONE MORE STEP",
                style = SpendWiseTextStyles.Caps,
                color = colors.onFaint,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Android's blocking\nthis by default.",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Because SpendWise was installed outside the Play Store, " +
                    "Android won't let it ask for SMS access yet. You need to " +
                    "flip one toggle in App info, then come back.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onMuted,
            )

            Spacer(Modifier.height(24.dp))

            StepsCard()

            Spacer(Modifier.height(32.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenAppInfo),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "Open app info",
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
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onRetry),
                color = colors.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = colors.divider,
                ),
            ) {
                Text(
                    text = "Try again",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.onMuted,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = sideloadNote,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Once Yutori is installed, future updates arrive as an in-app prompt " +
                    "and install without another Play Protect warning.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepsCard() {
    val colors = SpendWiseTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceElevated,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Step(
                n = "1",
                title = "Tap Open app info below.",
                detail = "Takes you straight to SpendWise's system settings page.",
            )
            HorizontalDivider(
                color = colors.divider,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            Step(
                n = "2",
                title = "Tap the three-dot menu (top-right), then Allow " +
                    "restricted settings.",
                detail = "Android will ask you to confirm — this lets the " +
                    "app request SMS.",
            )
            HorizontalDivider(
                color = colors.divider,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            Step(
                n = "3",
                title = "Come back here and tap Try again.",
                detail = "The permission dialog will appear this time.",
            )
        }
    }
}

@Composable
private fun Step(n: String, title: String, detail: String) {
    val colors = SpendWiseTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = n,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
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
