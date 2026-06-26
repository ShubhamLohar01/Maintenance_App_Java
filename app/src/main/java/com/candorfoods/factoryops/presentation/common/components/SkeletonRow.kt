package com.candorfoods.factoryops.presentation.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.Spacing

/**
 * Static gray-box mock of a list row shown while data is loading.
 *
 * No shimmer animation — Phase 2B scope is "medium polish, not too much."
 * Use [SkeletonList] for the common case of N stacked skeleton rows.
 */
@Composable
fun SkeletonRow(
    modifier: Modifier = Modifier,
) {
    val barColor = FactoryTheme.colors.border

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, FactoryTheme.colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp)
                    .background(barColor, RoundedCornerShape(4.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .background(barColor, RoundedCornerShape(4.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(12.dp)
                    .background(barColor, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** Vertical stack of [count] skeleton rows separated by [Spacing.md]. */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 3,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(count) { SkeletonRow() }
    }
}
