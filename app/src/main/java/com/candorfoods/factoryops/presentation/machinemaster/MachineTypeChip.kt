package com.candorfoods.factoryops.presentation.machinemaster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.TouchTarget
import kotlin.math.absoluteValue

/**
 * Colored chip for a free-text sub_category. The old fixed MachineType enum is
 * gone (the asset register has open-ended sub_categories like "CCTV", "Light",
 * "Production Equipment"), so the chip picks a stable color from a palette by
 * hashing the label — same sub_category always renders the same color.
 */
@Composable
private fun chipColor(subCategory: String): Color {
    val palette = listOf(
        FactoryTheme.colors.statusRunning,
        FactoryTheme.colors.statusIdle,
        FactoryTheme.colors.statusFlagged,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
    )
    if (subCategory.isBlank()) {
        return MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    }
    val idx = subCategory.lowercase().hashCode().absoluteValue % palette.size
    return palette[idx]
}

@Composable
fun SubCategoryChip(
    subCategory: String,
    modifier: Modifier = Modifier,
) {
    val label = subCategory.ifBlank { "—" }
    val accent = chipColor(subCategory)
    Row(
        modifier = modifier
            .height(TouchTarget.chip)
            .background(color = accent.copy(alpha = 0.18f), shape = RoundedCornerShape(percent = 50))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
