package com.candorfoods.factoryops.presentation.common.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.Spacing

/**
 * Standard wrapper around [Card] for FactoryOps screens.
 *
 * - Light theme: 2dp elevation on the surface color, 1dp border in [FactoryTheme.colors.border].
 * - Dark theme:  no elevation (shadows invisible), surfaceVariant background.
 * - [statusBorder] paints a 4dp colored stripe on the left edge for status-tinted cards
 *   (e.g. machine cards in the operator home grid).
 * - [containerColor], when non-null, overrides the default theme-aware container color —
 *   used for status-tinted cards (e.g. T4 escalation red wash on PM cards).
 * - When [onClick] is non-null, the card animates to 0.98 scale on press (80ms tween).
 */
@Composable
fun FactoryCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    statusBorder: Color? = null,
    containerColor: Color? = null,
    contentPadding: Dp = Spacing.base,           // 16dp
    cornerRadius: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.98f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "FactoryCardPressScale",
    )

    val resolvedContainerColor = containerColor ?: if (dark) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    val elevation = if (dark) 0.dp else 2.dp

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Card(
        modifier = modifier.scale(scale).then(clickModifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = resolvedContainerColor),
        border = BorderStroke(width = 1.dp, color = FactoryTheme.colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Box {
            if (statusBorder != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(statusBorder),
                )
            }
            Box(
                modifier = Modifier.padding(
                    start = if (statusBorder != null) contentPadding + 4.dp else contentPadding,
                    top = contentPadding,
                    end = contentPadding,
                    bottom = contentPadding,
                ),
                contentAlignment = Alignment.TopStart,
            ) {
                content()
            }
        }
    }
}
