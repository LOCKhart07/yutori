package com.yutori.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.yutori.ui.theme.YutoriTheme

/**
 * Top-of-screen progress strip for the onboarding flow
 * (mockups/v24-onboarding-flow.html). Renders [stepCount] equal-width
 * pills; the one at index `stepNumber - 1` is the active accent fill,
 * earlier ones are dimmed accent ("done"), later ones are neutral.
 */
@Composable
fun OnboardingProgressDots(
    stepNumber: Int,
    stepCount: Int,
) {
    val accent = MaterialTheme.colorScheme.primary
    val accentDim = accent.copy(alpha = ACCENT_DIM_ALPHA)
    val neutral = YutoriTheme.colors.surfaceElevated2

    Row(modifier = Modifier.fillMaxWidth().height(DOT_HEIGHT)) {
        for (i in 1..stepCount) {
            val color = when {
                i < stepNumber -> accentDim
                i == stepNumber -> accent
                else -> neutral
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = DOT_GAP_HALF)
                    .height(DOT_HEIGHT)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

private val DOT_HEIGHT = 3.dp
private val DOT_GAP_HALF = 3.dp
private const val ACCENT_DIM_ALPHA = 0.40f
