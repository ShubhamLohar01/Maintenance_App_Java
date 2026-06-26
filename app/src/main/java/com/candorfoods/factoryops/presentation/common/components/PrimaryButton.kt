package com.candorfoods.factoryops.presentation.common.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.presentation.common.theme.TouchTarget

enum class PrimaryButtonStyle { Primary, Success, Danger, Outlined }

enum class PrimaryButtonSize { Min, Primary, Hero }

private fun PrimaryButtonSize.dp(): Dp = when (this) {
    PrimaryButtonSize.Min     -> TouchTarget.min       // 56
    PrimaryButtonSize.Primary -> TouchTarget.primary   // 64
    PrimaryButtonSize.Hero    -> TouchTarget.hero      // 88
}

private const val DISABLED_ALPHA = 0.40f

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PrimaryButtonStyle = PrimaryButtonStyle.Primary,
    size: PrimaryButtonSize = PrimaryButtonSize.Min,
    loading: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !loading) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "PrimaryButtonPressScale",
    )

    val height = size.dp()
    val effectiveEnabled = enabled && !loading

    val (container, content) = when (style) {
        PrimaryButtonStyle.Primary  -> MaterialTheme.colorScheme.primary       to MaterialTheme.colorScheme.onPrimary
        PrimaryButtonStyle.Success  -> FactoryTheme.colors.statusRunning       to Color.White
        PrimaryButtonStyle.Danger   -> MaterialTheme.colorScheme.error          to MaterialTheme.colorScheme.onError
        PrimaryButtonStyle.Outlined -> Color.Transparent                        to MaterialTheme.colorScheme.primary
    }

    val scaledModifier = modifier
        .scale(scale)
        .defaultMinSize(minHeight = height)
        .height(height)

    if (style == PrimaryButtonStyle.Outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = scaledModifier,
            enabled = effectiveEnabled,
            interactionSource = interaction,
            border = BorderStroke(
                width = 1.dp,
                color = if (effectiveEnabled) content else content.copy(alpha = DISABLED_ALPHA),
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor         = content,
                disabledContentColor = content.copy(alpha = DISABLED_ALPHA),
            ),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            ButtonInner(text = text, icon = icon, loading = loading, contentColor = content)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = scaledModifier,
            enabled = effectiveEnabled,
            interactionSource = interaction,
            colors = ButtonDefaults.buttonColors(
                containerColor         = container,
                contentColor           = content,
                disabledContainerColor = container.copy(alpha = DISABLED_ALPHA),
                disabledContentColor   = content.copy(alpha = DISABLED_ALPHA),
            ),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            ButtonInner(text = text, icon = icon, loading = loading, contentColor = content)
        }
    }
}

@Composable
private fun ButtonInner(
    text: String,
    icon: ImageVector?,
    loading: Boolean,
    contentColor: Color,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight()) {
        // Always lay the text + icon out so the button's intrinsic width does not
        // collapse when `loading` flips on. Hide them with alpha when loading.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.alpha(if (loading) 0f else 1f),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (loading) {
            CircularProgressIndicator(
                color = contentColor,
                strokeWidth = 2.5.dp,
                modifier = Modifier
                    .size(20.dp)
                    .padding(0.dp),
            )
        }
    }
}
