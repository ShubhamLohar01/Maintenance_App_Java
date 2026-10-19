package com.candorfoods.factoryops.presentation.qc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.presentation.common.RequestNotificationPermission
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing

/**
 * QC home — a two-option chooser. "QC Verification" is the live inbox; "Checklist Records"
 * is the read-only PM + Breakdown archive (view + download). Settings lives in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QcLandingScreen(
    onQcVerification: () -> Unit,
    onChecklistRecords: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: QcLandingViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()

    // Prompt for the Android 13+ notification permission so QC handoff alerts show.
    RequestNotificationPermission()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FactoryOps — QC", style = MaterialTheme.typography.titleLarge)
                        user?.let { u ->
                            val roleLabel = when (u.role) {
                                UserRole.QC_HEAD   -> "QC Head"
                                UserRole.QC_MEMBER -> "QC Member"
                                else               -> u.role.name
                            }
                            Text(
                                "${u.name} — $roleLabel — ${u.plantId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            LandingCard(
                Icons.Filled.FactCheck,
                "QC Verification",
                "Verify breakdown & PM handoffs — awaiting, active, done",
                onQcVerification,
            )
            LandingCard(
                Icons.Filled.Folder,
                "Checklist Records",
                "PM & Breakdown records — view and download",
                onChecklistRecords,
            )
        }
    }
}

@Composable
private fun LandingCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
