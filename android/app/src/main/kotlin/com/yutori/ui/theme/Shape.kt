package com.yutori.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii used across the Copilot-inspired mockups.
 * 10dp — small chips, pills
 * 12dp — stat cards, banners, buttons
 * 14dp — sections with visible containment
 */
val YutoriShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
