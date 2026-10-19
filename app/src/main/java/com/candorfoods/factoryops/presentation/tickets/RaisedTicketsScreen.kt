package com.candorfoods.factoryops.presentation.tickets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.candorfoods.factoryops.domain.model.tickets.RaisedTicket
import com.candorfoods.factoryops.domain.model.tickets.TicketStatus
import com.candorfoods.factoryops.domain.repository.TicketScope
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.Spacing

/**
 * Complaints raised over WhatsApp, for the it_technician.
 *
 * Live = still needs work (oldest first — the longest wait is the next job).
 * History = resolved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaisedTicketsScreen(
    onTicketClick: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: RaisedTicketsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs = listOf(TicketScope.LIVE to "Live", TicketScope.HISTORY to "History")
    val selected = tabs.indexOfFirst { it.first == uiState.scope }.coerceAtLeast(0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Raised Tickets", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selected) {
                tabs.forEachIndexed { index, (scope, label) ->
                    Tab(
                        selected = selected == index,
                        onClick = { viewModel.onScopeChange(scope) },
                        text = { Text(label) },
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    uiState.error != null -> Column(
                        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Couldn't load tickets", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            uiState.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = FactoryTheme.colors.onSurfaceMuted,
                        )
                        Spacer(Modifier.height(Spacing.base))
                        PrimaryButton(
                            text = "Retry",
                            onClick = viewModel::onRetry,
                            style = PrimaryButtonStyle.Outlined,
                        )
                    }

                    // An empty queue is the normal, good state — not an error.
                    uiState.isEmpty -> Box(
                        Modifier.fillMaxSize().padding(Spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (uiState.scope == TicketScope.LIVE) "No open tickets. All clear."
                            else "No resolved tickets yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FactoryTheme.colors.onSurfaceMuted,
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.base),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(uiState.tickets, key = { it.id }) { ticket ->
                            TicketRow(ticket = ticket, onClick = { onTicketClick(ticket.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketRow(ticket: RaisedTicket, onClick: () -> Unit) {
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(ticket.photoUrl)
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ticket.ticketNo,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    StatusChip(ticket.status)
                }
                Spacer(Modifier.height(2.dp))
                Text(ticket.assetWithArea, style = MaterialTheme.typography.bodyMedium)
                if (!ticket.description.isNullOrBlank()) {
                    Text(
                        ticket.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = FactoryTheme.colors.onSurfaceMuted,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    // Resolved tickets are history: when it closed matters more
                    // than how long ago it was raised.
                    if (ticket.isResolved) "Closed ${ticket.closedAtDisplay}" else ticket.age(),
                    style = MaterialTheme.typography.labelSmall,
                    color = FactoryTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

/**
 * Photos are public S3 URLs that expire after 45 days, so a history ticket can
 * legitimately 404. Failure falls back to an icon rather than an empty gap.
 */
@Composable
private fun Thumbnail(url: String?) {
    val shape = RoundedCornerShape(8.dp)
    val placeholder = @Composable {
        Box(
            Modifier.size(56.dp).clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ImageNotSupported,
                contentDescription = null,
                tint = FactoryTheme.colors.onSurfaceMuted,
            )
        }
    }
    if (url.isNullOrBlank()) {
        placeholder()
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = "Ticket photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(56.dp).clip(shape),
        loading = {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        },
        error = { placeholder() },
    )
}

@Composable
private fun StatusChip(status: TicketStatus) {
    val colors = FactoryTheme.colors
    val tint: Color = when (status) {
        TicketStatus.OPEN -> colors.statusStopped
        TicketStatus.ACKNOWLEDGED -> colors.statusFlagged
        TicketStatus.RESOLVED -> colors.statusRunning
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(status.label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
