package com.spendwise.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Copilot.money-inspired palette. Dark is primary (most daily use);
 * light mirrors the grammar with the same accent discipline.
 *
 * Discipline: ~90% of the UI is neutrals. Accent amber appears only
 * on primary CTAs and the "Primary" SMS tag. Semantic colors appear
 * only when state carries meaning (over-budget, refund, forex pending).
 * Per-category tints are muted — similar lightness, differentiated
 * by hue, never saturated.
 */
object SpendWiseColors {

    // ─── Dark ────────────────────────────────────────────────────
    val DarkSurface        = Color(0xFF0F0F10)  // near-black, not true #000
    val DarkSurfaceEl      = Color(0xFF17171A)  // cards, banners
    val DarkSurfaceEl2     = Color(0xFF1E1E22)  // progress track
    val DarkOn             = Color(0xFFEDEEF0)
    val DarkOnMuted        = Color(0xFF8A8E96)
    val DarkOnFaint        = Color(0xFF5A5D63)
    val DarkDivider        = Color(0x14EDEEF0)  // 0.08 alpha over on-color
    val DarkAccent         = Color(0xFFF5B547)  // warm amber
    val DarkAccentOn       = Color(0xFF1A1408)  // text on accent
    val DarkPositive       = Color(0xFF6FB07E)
    val DarkInfo           = Color(0xFF7BA9D1)
    val DarkWarn           = Color(0xFFE0A260)
    val DarkNegative       = Color(0xFFE06C61)

    // ─── Light ───────────────────────────────────────────────────
    val LightSurface       = Color(0xFFFAFAF7)  // warm off-white
    val LightSurfaceEl     = Color(0xFFFFFFFF)
    val LightSurfaceEl2    = Color(0xFFF3F3EE)
    val LightOn            = Color(0xFF15161A)
    val LightOnMuted       = Color(0xFF6B6F76)
    val LightOnFaint       = Color(0xFFA5A8AD)
    val LightDivider       = Color(0x1415161A)
    val LightAccent        = Color(0xFFC48919)
    val LightAccentOn      = Color(0xFFFFF8EB)
    val LightPositive      = Color(0xFF3E7E4F)
    val LightInfo          = Color(0xFF3B6E9A)
    val LightWarn          = Color(0xFFA66217)
    val LightNegative      = Color(0xFFB7433A)

    // ─── Category tints — dark ──────────────────────────────────
    val DarkCatFood        = Color(0xFFC47E5E)  // warm tangerine
    val DarkCatGroceries   = Color(0xFF7DAB8F)  // sage
    val DarkCatTravel      = Color(0xFF7A8FB8)  // dusty indigo
    val DarkCatBills       = Color(0xFFB59A63)  // mustard-tan
    val DarkCatShopping    = Color(0xFFB88A92)  // dusty rose
    val DarkCatEnt         = Color(0xFF9B8FB5)  // muted violet
    val DarkCatHealth      = Color(0xFF6FA6A1)  // desaturated teal
    // UPI Transfer / Cash / Uncategorized / Other stay neutral — they
    // read as "unsorted," not themselves a category.

    // ─── Category tints — light ─────────────────────────────────
    val LightCatFood       = Color(0xFFA96A46)
    val LightCatGroceries  = Color(0xFF4F8068)
    val LightCatTravel     = Color(0xFF4F678D)
    val LightCatBills      = Color(0xFF8E7134)
    val LightCatShopping   = Color(0xFF8E5E67)
    val LightCatEnt        = Color(0xFF716389)
    val LightCatHealth     = Color(0xFF3F7874)
}
