package com.candorfoods.factoryops.presentation.common.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Brand & semantic raw tokens ──────────────────────────────────────────────
// Light
val BrandTeal       = Color(0xFF0F6E56)
val SuccessLight    = Color(0xFF16A34A)
val WarningLight    = Color(0xFFD97706)
val DangerLight     = Color(0xFFDC2626)
val InfoLight       = Color(0xFF2563EB)
val SurfaceLight    = Color(0xFFFFFFFF)
val SurfaceVarLight = Color(0xFFF8F8F6)
val OnSurfaceLight  = Color(0xFF1C1B17)
val OnSurfaceMutedL = Color(0xFF57564F)
val BorderLight     = Color(0xFFE2E1DA)

// Dark
val BrandTealDark   = Color(0xFF2DD4BF)
val SuccessDark     = Color(0xFF4ADE80)
val WarningDark     = Color(0xFFFCD34D)
val DangerDark      = Color(0xFFF87171)
val InfoDark        = Color(0xFF60A5FA)
val SurfaceDark     = Color(0xFF1A1A18)
val SurfaceVarDark  = Color(0xFF252522)
val OnSurfaceDark   = Color(0xFFF5F4EE)
val OnSurfaceMutedD = Color(0xFF9F9E96)
val BorderDark      = Color(0xFF3A3937)

// ─── Theme-aware semantic colors ─────────────────────────────────────────────
data class FactoryColors(
    val statusRunning: Color,
    val statusIdle:    Color,
    val statusStopped: Color,
    val statusFlagged: Color,
    val statusPendingQc: Color,
    val syncOk:        Color,
    val syncPending:   Color,
    val syncFailed:    Color,
    val info:          Color,
    val onSurfaceMuted: Color,
    val border:        Color,
)

val LightFactoryColors = FactoryColors(
    statusRunning   = SuccessLight,
    statusIdle      = OnSurfaceMutedL,
    statusStopped   = DangerLight,
    statusFlagged   = WarningLight,
    statusPendingQc = WarningLight,
    syncOk          = SuccessLight,
    syncPending     = WarningLight,
    syncFailed      = DangerLight,
    info            = InfoLight,
    onSurfaceMuted  = OnSurfaceMutedL,
    border          = BorderLight,
)

val DarkFactoryColors = FactoryColors(
    statusRunning   = SuccessDark,
    statusIdle      = OnSurfaceMutedD,
    statusStopped   = DangerDark,
    statusFlagged   = WarningDark,
    statusPendingQc = WarningDark,
    syncOk          = SuccessDark,
    syncPending     = WarningDark,
    syncFailed      = DangerDark,
    info            = InfoDark,
    onSurfaceMuted  = OnSurfaceMutedD,
    border          = BorderDark,
)

val LocalFactoryColors = staticCompositionLocalOf { LightFactoryColors }

object FactoryTheme {
    val colors: FactoryColors
        @Composable @ReadOnlyComposable
        get() = LocalFactoryColors.current
}

// ─── Legacy aliases (kept for older callers; prefer FactoryTheme.colors) ─────
val StatusRunning = SuccessLight
val StatusIdle    = OnSurfaceMutedL
val StatusStopped = DangerLight
val StatusFlagged = WarningLight
val SyncOk        = SuccessLight
val SyncPending   = WarningLight
val SyncFailed    = DangerLight

// Compose-side palette names some files still reference.
val Green80 = SuccessDark
val Green40 = SuccessLight
val Amber80 = WarningDark
val Amber40 = WarningLight
val Red80   = DangerDark
val Red40   = DangerLight
val Blue80  = InfoDark
val Blue40  = InfoLight
val Gray80  = OnSurfaceMutedD
val Gray40  = OnSurfaceMutedL
