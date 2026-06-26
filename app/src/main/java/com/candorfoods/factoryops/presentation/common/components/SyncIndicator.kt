package com.candorfoods.factoryops.presentation.common.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme

private enum class SyncState { Synced, Syncing, Pending, Failed }

@Composable
fun SyncIndicator(
    pendingCount: Int,
    isSyncing: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    hasFailed: Boolean = false,
) {
    val state = when {
        hasFailed         -> SyncState.Failed
        isSyncing         -> SyncState.Syncing
        pendingCount > 0  -> SyncState.Pending
        else              -> SyncState.Synced
    }

    Row(
        modifier = modifier
            .clickable(onClick = onTap)
            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Crossfade(
            targetState = state,
            animationSpec = tween(durationMillis = 200),
            label = "SyncIndicatorCrossfade",
        ) { current ->
            when (current) {
                SyncState.Synced  -> SyncedIcon()
                SyncState.Syncing -> SyncingIcon()
                SyncState.Pending -> PendingBadge(count = pendingCount)
                SyncState.Failed  -> FailedIcon()
            }
        }
    }
}

@Composable
private fun SyncedIcon() {
    Icon(
        imageVector = Icons.Default.CloudDone,
        contentDescription = "Synced",
        tint = FactoryTheme.colors.syncOk,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun SyncingIcon() {
    val rotation by rememberInfiniteTransition(label = "sync_spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "spin",
    )
    Icon(
        imageVector = Icons.Default.Sync,
        contentDescription = "Syncing",
        tint = FactoryTheme.colors.syncOk,
        modifier = Modifier
            .size(20.dp)
            .rotate(rotation),
    )
}

@Composable
private fun PendingBadge(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(
                color = FactoryTheme.colors.syncPending,
                shape = RoundedCornerShape(percent = 50),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = "Pending",
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "$count",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun FailedIcon() {
    Icon(
        imageVector = Icons.Default.SyncProblem,
        contentDescription = "Sync failed",
        tint = FactoryTheme.colors.syncFailed,
        modifier = Modifier.size(20.dp),
    )
}
