package com.spendwise.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extra tokens that don't fit Material3's ColorScheme — semantic
 * state colors and per-category tints. Exposed via
 * [SpendWiseTheme.colors] so screens don't reach for raw palette
 * values.
 */
@Immutable
data class SpendWiseColorExtras(
    val positive: Color,
    val info: Color,
    val warn: Color,
    val negative: Color,
    val onMuted: Color,
    val onFaint: Color,
    val surfaceElevated: Color,  // cards, banners
    val surfaceElevated2: Color, // progress track
    val divider: Color,
    // Category tints — keyed by enum name (e.g. "FOOD_DINING").
    val categoryFood: Color,
    val categoryGroceries: Color,
    val categoryTravel: Color,
    val categoryBills: Color,
    val categoryShopping: Color,
    val categoryEntertainment: Color,
    val categoryHealth: Color,
    val categoryNeutral: Color,
) {
    /**
     * Resolve a category color by enum name. Unknown / neutral
     * categories fall back to a muted gray so the visual grammar
     * says "unsorted" rather than picking a tint at random.
     */
    fun forCategory(categoryName: String?): Color = when (categoryName) {
        "FOOD_DINING"        -> categoryFood
        "GROCERIES"          -> categoryGroceries
        "TRAVEL_TRANSPORT"   -> categoryTravel
        "BILLS_UTILITIES"    -> categoryBills
        "SHOPPING"           -> categoryShopping
        "ENTERTAINMENT"      -> categoryEntertainment
        "HEALTH"             -> categoryHealth
        else                 -> categoryNeutral
    }
}

private val DarkExtras = SpendWiseColorExtras(
    positive              = SpendWiseColors.DarkPositive,
    info                  = SpendWiseColors.DarkInfo,
    warn                  = SpendWiseColors.DarkWarn,
    negative              = SpendWiseColors.DarkNegative,
    onMuted               = SpendWiseColors.DarkOnMuted,
    onFaint               = SpendWiseColors.DarkOnFaint,
    surfaceElevated       = SpendWiseColors.DarkSurfaceEl,
    surfaceElevated2      = SpendWiseColors.DarkSurfaceEl2,
    divider               = SpendWiseColors.DarkDivider,
    categoryFood          = SpendWiseColors.DarkCatFood,
    categoryGroceries     = SpendWiseColors.DarkCatGroceries,
    categoryTravel        = SpendWiseColors.DarkCatTravel,
    categoryBills         = SpendWiseColors.DarkCatBills,
    categoryShopping      = SpendWiseColors.DarkCatShopping,
    categoryEntertainment = SpendWiseColors.DarkCatEnt,
    categoryHealth        = SpendWiseColors.DarkCatHealth,
    categoryNeutral       = SpendWiseColors.DarkOnMuted,
)

private val LightExtras = SpendWiseColorExtras(
    positive              = SpendWiseColors.LightPositive,
    info                  = SpendWiseColors.LightInfo,
    warn                  = SpendWiseColors.LightWarn,
    negative              = SpendWiseColors.LightNegative,
    onMuted               = SpendWiseColors.LightOnMuted,
    onFaint               = SpendWiseColors.LightOnFaint,
    surfaceElevated       = SpendWiseColors.LightSurfaceEl,
    surfaceElevated2      = SpendWiseColors.LightSurfaceEl2,
    divider               = SpendWiseColors.LightDivider,
    categoryFood          = SpendWiseColors.LightCatFood,
    categoryGroceries     = SpendWiseColors.LightCatGroceries,
    categoryTravel        = SpendWiseColors.LightCatTravel,
    categoryBills         = SpendWiseColors.LightCatBills,
    categoryShopping      = SpendWiseColors.LightCatShopping,
    categoryEntertainment = SpendWiseColors.LightCatEnt,
    categoryHealth        = SpendWiseColors.LightCatHealth,
    categoryNeutral       = SpendWiseColors.LightOnMuted,
)

private val LocalSpendWiseColors = staticCompositionLocalOf<SpendWiseColorExtras> {
    error("SpendWiseTheme not applied — wrap composition in SpendWiseTheme { }")
}

private val DarkColorScheme = darkColorScheme(
    primary            = SpendWiseColors.DarkAccent,
    onPrimary          = SpendWiseColors.DarkAccentOn,
    secondary          = SpendWiseColors.DarkOn,
    onSecondary        = SpendWiseColors.DarkSurface,
    background         = SpendWiseColors.DarkSurface,
    onBackground       = SpendWiseColors.DarkOn,
    surface            = SpendWiseColors.DarkSurface,
    onSurface          = SpendWiseColors.DarkOn,
    surfaceVariant     = SpendWiseColors.DarkSurfaceEl,
    onSurfaceVariant   = SpendWiseColors.DarkOnMuted,
    error              = SpendWiseColors.DarkNegative,
    onError            = SpendWiseColors.DarkSurface,
    tertiary           = SpendWiseColors.DarkInfo,
    onTertiary         = SpendWiseColors.DarkSurface,
    outline            = SpendWiseColors.DarkDivider,
)

private val LightColorScheme = lightColorScheme(
    primary            = SpendWiseColors.LightAccent,
    onPrimary          = SpendWiseColors.LightAccentOn,
    secondary          = SpendWiseColors.LightOn,
    onSecondary        = SpendWiseColors.LightSurface,
    background         = SpendWiseColors.LightSurface,
    onBackground       = SpendWiseColors.LightOn,
    surface            = SpendWiseColors.LightSurface,
    onSurface          = SpendWiseColors.LightOn,
    surfaceVariant     = SpendWiseColors.LightSurfaceEl,
    onSurfaceVariant   = SpendWiseColors.LightOnMuted,
    error              = SpendWiseColors.LightNegative,
    onError            = SpendWiseColors.LightSurface,
    tertiary           = SpendWiseColors.LightInfo,
    onTertiary         = SpendWiseColors.LightSurface,
    outline            = SpendWiseColors.LightDivider,
)

@Composable
fun SpendWiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extras = if (darkTheme) DarkExtras else LightExtras

    // Sync system bar icon color with theme. Dark mode → light icons;
    // light mode → dark icons. Uses the View-based API (Compose's
    // preferred WindowCompat path) rather than the deprecated
    // SideEffect on the system bar controller.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
                ?: return@SideEffect
            val controller = androidx.core.view.WindowCompat
                .getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalSpendWiseColors provides extras,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SpendWiseTypography,
            shapes = SpendWiseShapes,
            content = content,
        )
    }
}

/** Screens read extra tokens via `SpendWiseTheme.colors.xxx`. */
object SpendWiseTheme {
    val colors: SpendWiseColorExtras
        @Composable @ReadOnlyComposable
        get() = LocalSpendWiseColors.current
}
