package com.candorfoods.factoryops.presentation.common.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary             = BrandTeal,
    onPrimary           = Color.White,
    primaryContainer    = BrandTeal.copy(alpha = 0.12f),
    onPrimaryContainer  = BrandTeal,

    secondary           = WarningLight,
    onSecondary         = Color.White,
    secondaryContainer  = WarningLight.copy(alpha = 0.14f),
    onSecondaryContainer = WarningLight,

    tertiary            = InfoLight,
    onTertiary          = Color.White,

    error               = DangerLight,
    onError             = Color.White,
    errorContainer      = DangerLight.copy(alpha = 0.12f),
    onErrorContainer    = DangerLight,

    background          = SurfaceLight,
    onBackground        = OnSurfaceLight,
    surface             = SurfaceLight,
    onSurface           = OnSurfaceLight,
    surfaceVariant      = SurfaceVarLight,
    onSurfaceVariant    = OnSurfaceMutedL,
    outline             = BorderLight,
    outlineVariant      = BorderLight,
)

private val DarkColorScheme = darkColorScheme(
    primary             = BrandTealDark,
    onPrimary           = Color(0xFF003830),
    primaryContainer    = BrandTealDark.copy(alpha = 0.18f),
    onPrimaryContainer  = BrandTealDark,

    secondary           = WarningDark,
    onSecondary         = Color(0xFF3F2A00),
    secondaryContainer  = WarningDark.copy(alpha = 0.18f),
    onSecondaryContainer = WarningDark,

    tertiary            = InfoDark,
    onTertiary          = Color(0xFF002B5C),

    error               = DangerDark,
    onError             = Color(0xFF5C0000),
    errorContainer      = DangerDark.copy(alpha = 0.18f),
    onErrorContainer    = DangerDark,

    background          = SurfaceDark,
    onBackground        = OnSurfaceDark,
    surface             = SurfaceDark,
    onSurface           = OnSurfaceDark,
    surfaceVariant      = SurfaceVarDark,
    onSurfaceVariant    = OnSurfaceMutedD,
    outline             = BorderDark,
    outlineVariant      = BorderDark,
)

@Composable
fun FactoryOpsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme    = if (darkTheme) DarkColorScheme    else LightColorScheme
    val factoryColors  = if (darkTheme) DarkFactoryColors  else LightFactoryColors

    CompositionLocalProvider(
        LocalFactoryColors      provides factoryColors,
        LocalFactoryTypeExtras  provides DefaultFactoryTypeExtras,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = FactoryTypography,
            content     = content,
        )
    }
}
