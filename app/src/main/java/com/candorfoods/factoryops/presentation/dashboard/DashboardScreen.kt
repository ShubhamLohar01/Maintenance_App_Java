package com.candorfoods.factoryops.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing

/**
 * Role launcher: a grid of boxes that open existing sections. Tiles come from
 * [dashboardTilesFor]; this screen only knows how to render and route them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    role: UserRole,
    onOpen: (route: String) -> Unit,
    onComingSoon: (label: String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val badgeCounts by viewModel.badgeCounts.collectAsStateWithLifecycle()
    val tiles = remember(role) { dashboardTilesFor(role) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FactoryOps — Maintenance", style = MaterialTheme.typography.titleLarge)
                        currentUser?.let {
                            Text(
                                "${it.name} — ${it.plantId}",
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.base),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(tiles, key = { it.label }) { tile ->
                DashboardTileCard(
                    label = tile.label,
                    icon = tile.icon,
                    badgeCount = tile.badge?.let { badgeCounts[it] } ?: 0,
                    onClick = {
                        when (val action = tile.action) {
                            is DashboardAction.Open       -> onOpen(action.route)
                            is DashboardAction.ComingSoon -> onComingSoon(tile.label)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DashboardTileCard(
    label: String,
    icon: ImageVector,
    badgeCount: Int,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.aspectRatio(1f)) {
        FactoryCard(
            modifier = Modifier.fillMaxSize(),
            onClick = onClick,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Red notification-count badge, top-right corner of the tile.
        if (badgeCount > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp),
            ) {
                Text(
                    if (badgeCount > 99) "99+" else badgeCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
