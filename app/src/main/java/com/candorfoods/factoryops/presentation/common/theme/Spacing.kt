package com.candorfoods.factoryops.presentation.common.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 4-point spacing scale.
 * Use these instead of arbitrary dp values to keep visual rhythm consistent.
 */
object Spacing {
    val xs:   Dp = 4.dp
    val sm:   Dp = 8.dp
    val md:   Dp = 12.dp
    val base: Dp = 16.dp
    val lg:   Dp = 24.dp
    val xl:   Dp = 32.dp
    val xxl:  Dp = 48.dp
    val xxxl: Dp = 64.dp
}

/** Common touch-target sizes (factory floor — gloves friendly). */
object TouchTarget {
    val min:        Dp = 56.dp   // standard tap target
    val primary:    Dp = 64.dp   // primary actions (Login, Approve)
    val hero:       Dp = 88.dp   // Production Start / Stop
    val chip:       Dp = 28.dp
    val chipLarge:  Dp = 64.dp
}
