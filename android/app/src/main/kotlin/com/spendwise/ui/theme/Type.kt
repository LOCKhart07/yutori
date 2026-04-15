package com.spendwise.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.spendwise.R

/**
 * Typography scale — Inter for UI, JetBrains Mono for amounts and SMS
 * bodies. Both bundled under res/font/ so no network dependency.
 */

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular,  FontWeight.Normal,   FontStyle.Normal),
    Font(R.font.inter_medium,   FontWeight.Medium,   FontStyle.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.inter_bold,     FontWeight.Bold,     FontStyle.Normal),
)

val MonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular,  FontWeight.Normal,   FontStyle.Normal),
    Font(R.font.jetbrains_mono_medium,   FontWeight.Medium,   FontStyle.Normal),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold, FontStyle.Normal),
)

val SpendWiseTypography = Typography(
    // Display — reserved for the dashboard hero amount.
    displayLarge = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.02).em,
    ),
    displayMedium = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.02).em,
    ),
    displaySmall = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.01).em,
    ),

    headlineLarge = TextStyle(
        fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),

    titleLarge = TextStyle(
        fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp,
        letterSpacing = 0.1.em, // small-caps style for section headers
    ),

    bodyLarge = TextStyle(
        fontFamily = InterFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),

    labelLarge = TextStyle(
        fontFamily = InterFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, lineHeight = 13.sp,
        letterSpacing = 0.08.em,
    ),
)

/** Extra styles the Material3 Typography scale doesn't include. */
object SpendWiseTextStyles {
    /** JetBrains Mono 14sp — transaction list amounts, SMS sender. */
    val Mono = TextStyle(
        fontFamily = MonoFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp,
    )
    /** JetBrains Mono 11sp — raw SMS bodies. */
    val MonoSmall = TextStyle(
        fontFamily = MonoFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 17.sp,
    )
    /** Small-caps section header / meta row label. */
    val Caps = TextStyle(
        fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp,
        letterSpacing = 0.1.em,
    )
}
