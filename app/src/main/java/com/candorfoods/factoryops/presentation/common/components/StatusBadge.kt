package com.candorfoods.factoryops.presentation.common.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.candorfoods.factoryops.domain.model.MachineStatus
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.TouchTarget

private const val CONTAINER_TINT_ALPHA = 0.18f
private const val STATUS_TWEEN_MS = 200

@Composable
private fun colorFor(status: MachineStatus): Color = when (status) {
    MachineStatus.RUNNING    -> FactoryTheme.colors.statusRunning
    MachineStatus.IDLE       -> FactoryTheme.colors.statusIdle
    MachineStatus.STOPPED    -> FactoryTheme.colors.statusStopped
    MachineStatus.PENDING_QC -> FactoryTheme.colors.statusPendingQc
}

private fun labelFor(status: MachineStatus): String = when (status) {
    MachineStatus.RUNNING    -> "RUNNING"
    MachineStatus.IDLE       -> "IDLE"
    MachineStatus.STOPPED    -> "STOPPED"
    MachineStatus.PENDING_QC -> "QC"
}

@Composable
fun StatusBadge(
    status: MachineStatus,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    icon: ImageVector? = null,
) {
    val target = colorFor(status)
    val tween = tween<Color>(durationMillis = STATUS_TWEEN_MS)

    val contentColor by animateColorAsState(
        targetValue = target,
        animationSpec = tween,
        label = "StatusBadgeContent",
    )
    val containerColor by animateColorAsState(
        targetValue = target.copy(alpha = CONTAINER_TINT_ALPHA),
        animationSpec = tween,
        label = "StatusBadgeContainer",
    )

    val height = if (large) TouchTarget.chipLarge else TouchTarget.chip
    val pill = RoundedCornerShape(percent = 50)
    val iconSize = if (large) 24.dp else 16.dp
    val textStyle = if (large) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.labelMedium
    }

    Row(
        modifier = modifier
            .height(height)
            .background(color = containerColor, shape = pill)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(iconSize),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = labelFor(status),
            color = contentColor,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            fontSize = if (large) 18.sp else 12.sp,
        )
    }
}
