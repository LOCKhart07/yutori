package com.yutori.ui.theme

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
 * [YutoriTheme.colors] so screens don't reach for raw palette
 * values.
 */
@Immutable
data class YutoriColorExtras(
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
    val categorySubscriptions: Color,
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
        "SUBSCRIPTIONS"      -> categorySubscriptions
        "HEALTH"             -> categoryHealth
        else                 -> categoryNeutral
    }
}

private val DarkExtras = YutoriColorExtras(
    positive              = YutoriColors.DarkPositive,
    info                  = YutoriColors.DarkInfo,
    warn                  = YutoriColors.DarkWarn,
    negative              = YutoriColors.DarkNegative,
    onMuted               = YutoriColors.DarkOnMuted,
    onFaint               = YutoriColors.DarkOnFaint,
    surfaceElevated       = YutoriColors.DarkSurfaceEl,
    surfaceElevated2      = YutoriColors.DarkSurfaceEl2,
    divider               = YutoriColors.DarkDivider,
    categoryFood          = YutoriColors.DarkCatFood,
    categoryGroceries     = YutoriColors.DarkCatGroceries,
    categoryTravel        = YutoriColors.DarkCatTravel,
    categoryBills         = YutoriColors.DarkCatBills,
    categoryShopping      = YutoriColors.DarkCatShopping,
    categoryEntertainment = YutoriColors.DarkCatEnt,
    categorySubscriptions = YutoriColors.DarkCatSubs,
    categoryHealth        = YutoriColors.DarkCatHealth,
    categoryNeutral       = YutoriColors.DarkOnMuted,
)

private val LightExtras = YutoriColorExtras(
    positive              = YutoriColors.LightPositive,
    info                  = YutoriColors.LightInfo,
    warn                  = YutoriColors.LightWarn,
    negative              = YutoriColors.LightNegative,
    onMuted               = YutoriColors.LightOnMuted,
    onFaint               = YutoriColors.LightOnFaint,
    surfaceElevated       = YutoriColors.LightSurfaceEl,
    surfaceElevated2      = YutoriColors.LightSurfaceEl2,
    divider               = YutoriColors.LightDivider,
    categoryFood          = YutoriColors.LightCatFood,
    categoryGroceries     = YutoriColors.LightCatGroceries,
    categoryTravel        = YutoriColors.LightCatTravel,
    categoryBills         = YutoriColors.LightCatBills,
    categoryShopping      = YutoriColors.LightCatShopping,
    categoryEntertainment = YutoriColors.LightCatEnt,
    categorySubscriptions = YutoriColors.LightCatSubs,
    categoryHealth        = YutoriColors.LightCatHealth,
    categoryNeutral       = YutoriColors.LightOnMuted,
)

private val LocalYutoriColors = staticCompositionLocalOf<YutoriColorExtras> {
    error("YutoriTheme not applied — wrap composition in YutoriTheme { }")
}

private val DarkColorScheme = darkColorScheme(
    primary            = YutoriColors.DarkAccent,
    onPrimary          = YutoriColors.DarkAccentOn,
    secondary          = YutoriColors.DarkOn,
    onSecondary        = YutoriColors.DarkSurface,
    background         = YutoriColors.DarkSurface,
    onBackground       = YutoriColors.DarkOn,
    surface            = YutoriColors.DarkSurface,
    onSurface          = YutoriColors.DarkOn,
    surfaceVariant     = YutoriColors.DarkSurfaceEl,
    onSurfaceVariant   = YutoriColors.DarkOnMuted,
    error              = YutoriColors.DarkNegative,
    onError            = YutoriColors.DarkSurface,
    tertiary           = YutoriColors.DarkInfo,
    onTertiary         = YutoriColors.DarkSurface,
    outline            = YutoriColors.DarkDivider,
)

private val LightColorScheme = lightColorScheme(
    primary            = YutoriColors.LightAccent,
    onPrimary          = YutoriColors.LightAccentOn,
    secondary          = YutoriColors.LightOn,
    onSecondary        = YutoriColors.LightSurface,
    background         = YutoriColors.LightSurface,
    onBackground       = YutoriColors.LightOn,
    surface            = YutoriColors.LightSurface,
    onSurface          = YutoriColors.LightOn,
    surfaceVariant     = YutoriColors.LightSurfaceEl,
    onSurfaceVariant   = YutoriColors.LightOnMuted,
    error              = YutoriColors.LightNegative,
    onError            = YutoriColors.LightSurface,
    tertiary           = YutoriColors.LightInfo,
    onTertiary         = YutoriColors.LightSurface,
    outline            = YutoriColors.LightDivider,
)

@Composable
fun YutoriTheme(
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
        LocalYutoriColors provides extras,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = YutoriTypography,
            shapes = YutoriShapes,
            content = content,
        )
    }
}

/** Screens read extra tokens via `YutoriTheme.colors.xxx`. */
object YutoriTheme {
    val colors: YutoriColorExtras
        @Composable @ReadOnlyComposable
        get() = LocalYutoriColors.current
}
