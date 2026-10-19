package com.candorfoods.factoryops.presentation.technician

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.utilities.UtilityType
import com.candorfoods.factoryops.domain.model.w202.W202MeterKind
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing

/**
 * "Daily Reading" utility chooser. Each row opens that utility's history + entry
 * screen (Diesel / Gas / Electricity / Water).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReadingHubScreen(
    onUtility: (UtilityType) -> Unit,
    onElectricConsumption: () -> Unit,   // reverted: legacy floor-meter "Electric Consumption" screen (W-202)
    onA185Readings: () -> Unit,          // HT / Cold Room / UV — A-185 only
    onW202Reading: (W202MeterKind) -> Unit, // Main Meter / Solar — W-202 only
    onBack: () -> Unit,
    viewModel: DailyReadingHubViewModel = hiltViewModel(),
) {
    val showA185 by viewModel.showA185.collectAsStateWithLifecycle()
    val showW202 by viewModel.showW202.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Reading", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            UtilityCard(Icons.Filled.LocalGasStation, "Diesel consumption") { onUtility(UtilityType.DIESEL) }
            UtilityCard(Icons.Filled.LocalFireDepartment, "Gas consumption") { onUtility(UtilityType.GAS) }
            // Electricity reverted to the legacy floor-meter "Electric Consumption" screen (W-202).
            // New Daily Utility Consumption electricity form is parked — see NavGraph.
            // UtilityCard(Icons.Filled.Bolt, "Electricity consumption") { onUtility(UtilityType.ELECTRICITY) }
            // Electricity now also carries W-202's AC readings (see DailyReadingSection).
            UtilityCard(Icons.Filled.Bolt, "Electricity consumption") { onElectricConsumption() }
            UtilityCard(Icons.Filled.WaterDrop, "Water consumption") { onUtility(UtilityType.WATER) }
            // Main incoming and solar meters — shown only to W-202 users.
            if (showW202) {
                UtilityCard(Icons.Filled.Speed, "Main meter reading") { onW202Reading(W202MeterKind.MAIN) }
                UtilityCard(Icons.Filled.WbSunny, "Solar reading") { onW202Reading(W202MeterKind.SOLAR) }
            }
            // HT Panel / Cold Room / UV meter readings — shown only to A-185 users.
            if (showA185) {
                UtilityCard(Icons.Filled.Thermostat, "HT / Cold Room / UV readings") { onA185Readings() }
            }
        }
    }
}

@Composable
private fun UtilityCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(Spacing.base))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
