package com.yutori.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yutori.R
import com.yutori.ui.theme.YutoriTheme

/**
 * Onboarding step 1 (mockups/v24-onboarding-flow.html, frame 1).
 *
 * Brand-first welcome: the launcher icon, then 余裕 / Yutori /
 * "breathing room" stacked under it. The privacy reassurance is a
 * small line above the CTA — we don't repeat "nothing leaves your
 * phone" twice on the same screen and we don't pretend permissions
 * "leave the device" anywhere downstream (see PermissionScreen).
 */
@Composable
fun WelcomeScreen(
    stepNumber: Int,
    stepCount: Int,
    onGetStarted: () -> Unit,
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
                    .padding(top = 24.dp)
                    .padding(horizontal = 32.dp),
            ) {
                OnboardingProgressDots(
                    stepNumber = stepNumber,
                    stepCount = stepCount,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(168.dp),
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "余裕",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Normal,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "YUTORI",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 3.sp,
                    ),
                    color = colors.onMuted,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "breathing room",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                    ),
                    color = colors.onFaint,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "Your data never leaves your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                PrimaryActionButton(
                    text = "Get started",
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
