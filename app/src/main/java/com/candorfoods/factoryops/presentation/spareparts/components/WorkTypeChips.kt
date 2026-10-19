package com.candorfoods.factoryops.presentation.spareparts.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.domain.model.spareparts.WorkType

/** Human label for a work type. The wire value doubles as the label — the backend
 * stores exactly this text in mt_spareparts_log.action. */
val WorkType.label: String get() = wire

/**
 * The required "Used for" selector on the Use sheet. Nothing is selected until the
 * technician picks, and the caller keeps its confirm button disabled while
 * [selected] is null — a usage must never be logged without a reason.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkTypeChips(
    selected: WorkType?,
    onSelect: (WorkType) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WorkType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                enabled = enabled,
                label = { Text(type.label) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}
