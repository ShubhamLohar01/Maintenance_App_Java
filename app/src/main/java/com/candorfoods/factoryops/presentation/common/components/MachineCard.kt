package com.candorfoods.factoryops.presentation.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.machinemaster.SubCategoryChip
import com.candorfoods.factoryops.util.NumberFormatter

@Composable
fun MachineCard(
    machine: MtMachine,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    underBreakdown: Boolean = false,
) {
    val muted = FactoryTheme.colors.onSurfaceMuted
    val location = listOf(machine.building, machine.subLocation)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val power = machine.ratedKw?.let { NumberFormatter.formatKw(it) } ?: machine.powerLoadRaw ?: "—"

    FactoryCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Text(
                machine.assetName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (underBreakdown) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "● UNDER BREAKDOWN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = FactoryTheme.colors.statusStopped,
                )
            }
            machine.category.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                SubCategoryChip(subCategory = it)
            }
            Spacer(Modifier.height(2.dp))
            Text(machine.assetId, style = MaterialTheme.typography.bodySmall, color = muted)
            if (location.isNotBlank()) {
                Text(location, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Spacer(Modifier.height(4.dp))
            Text(power, style = MaterialTheme.typography.bodySmall)
        }
    }
}
