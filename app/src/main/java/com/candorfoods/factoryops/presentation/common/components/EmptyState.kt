package com.candorfoods.factoryops.presentation.common.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.Spacing

/**
 * Centered empty state for list screens — icon + title + optional subtitle.
 *
 * Fades in over 200ms on first composition. No SVG illustrations per Phase 2B scope.
 *
 * Caller renders this when a list/tab has zero rows. Sample:
 * ```
 * if (uiState.tickets.isEmpty()) {
 *     EmptyState(
 *         icon = Icons.Default.Inbox,
 *         title = "No tickets assigned",
 *         subtitle = "New breakdowns will appear here.",
 *     )
 * }
 * ```
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "EmptyStateFade",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.lg)
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = FactoryTheme.colors.onSurfaceMuted,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FactoryTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
