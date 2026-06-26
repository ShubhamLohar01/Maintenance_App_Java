package com.candorfoods.factoryops.presentation.common.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Phase-2B type scale.
 *
 * Header / display / title — sans-serif (system Roboto until Plus Jakarta Sans is bundled).
 * Body — sans-serif (system Roboto until Inter is bundled).
 * Mono extras (timestamps, kWh values) — system monospace until JetBrains Mono is bundled.
 *
 * Sizes follow the factory-floor spec: bigger than Material 3 defaults so
 * operators can read screens in dim/dusty light at arm's length.
 */

private val Sans = FactoryFonts.sans
private val Mono = FactoryFonts.mono

val FactoryTypography = Typography(
    // Display — huge numbers (kWh totals, hero values)
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize   = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.4).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize   = 32.sp,
        lineHeight = 40.sp,
    ),
    // Headline — screen titles
    headlineLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 22.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 20.sp,
        lineHeight = 28.sp,
    ),
    // Title — card titles
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize   = 18.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
    ),
    // Body
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize   = 17.sp,                // bumped from M3 default 16sp
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize   = 15.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize   = 13.sp,
        lineHeight = 20.sp,
    ),
    // Labels — chips, badges, metadata
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize   = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

/**
 * Type styles outside Material 3's standard slots —
 * tabular/mono number rendering for kWh, currency, timestamps, IDs.
 */
data class FactoryTypeExtras(
    val displayHero: TextStyle,
    val monoLarge:   TextStyle,
    val monoMedium:  TextStyle,
)

val DefaultFactoryTypeExtras = FactoryTypeExtras(
    displayHero = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize   = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.6).sp,
    ),
    monoLarge = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize   = 24.sp,
        lineHeight = 32.sp,
    ),
    monoMedium = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 22.sp,
    ),
)

val LocalFactoryTypeExtras = staticCompositionLocalOf { DefaultFactoryTypeExtras }

/** Convenience accessor (mirrors `MaterialTheme.typography`). */
object FactoryTypeScale {
    val extras: FactoryTypeExtras
        @Composable @ReadOnlyComposable
        get() = LocalFactoryTypeExtras.current
}

/**
 * Font-family aliases. Today: system fonts. When `.ttf` files are bundled into
 * `res/font/`, swap the values here and the whole app picks up the change —
 * no edits needed in screens.
 */
object FactoryFonts {
    val sans: FontFamily = FontFamily.SansSerif
    val mono: FontFamily = FontFamily.Monospace
}
