package com.candorfoods.factoryops.presentation.tickets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.candorfoods.factoryops.domain.model.tickets.RaisedTicket
import com.candorfoods.factoryops.domain.model.tickets.TicketStatus
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.Spacing

/**
 * One raised ticket. What is offered depends on where it is:
 *
 *   open          -> Acknowledge
 *   acknowledged  -> who has it + Resolve
 *   resolved      -> read-only record of who closed it and what they did
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaisedTicketDetailScreen(
    ticketId: Int,
    onBack: () -> Unit,
    viewModel: RaisedTicketDetailViewModel = hiltViewModel(),
) {
    // Pushed in rather than injected via SavedStateHandle, matching ItAssetListScreen.
    LaunchedEffect(ticketId) { viewModel.onTicketId(ticketId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // A conflict ("already acknowledged by X") is shown once, then cleared, so it
    // doesn't linger over a screen that has already refreshed to show the truth.
    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onDismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.ticket?.ticketNo ?: "Ticket",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val ticket = uiState.ticket
            when {
                uiState.isLoading && ticket == null ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                uiState.error != null && ticket == null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Couldn't load the ticket", style = MaterialTheme.typography.titleMedium)
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

                ticket != null -> TicketDetail(
                    ticket = ticket,
                    isActing = uiState.isActing,
                    onAcknowledge = viewModel::onAcknowledge,
                    onResolveClicked = viewModel::onResolveClicked,
                )
            }
        }
    }

    if (uiState.showResolveDialog) {
        ResolveDialog(
            text = uiState.correctiveAction,
            canSubmit = uiState.canSubmitResolve,
            onTextChange = viewModel::onCorrectiveActionChange,
            onSubmit = viewModel::onResolveSubmit,
            onDismiss = viewModel::onResolveDismiss,
        )
    }
}

@Composable
private fun TicketDetail(
    ticket: RaisedTicket,
    isActing: Boolean,
    onAcknowledge: () -> Unit,
    onResolveClicked: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Photo(ticket.photoUrl)

        FactoryCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Field("Asset", ticket.asset)
                Field("Area", ticket.area)
                Field("Problem", ticket.description)
                Field("Raised by", ticket.raisedBy)
                Field("Raised at", ticket.raisedAtDisplay)
                Field("Status", ticket.status.label)
                if (!ticket.assignedTo.isNullOrBlank()) Field("Assigned to", ticket.assignedTo)
                if (ticket.isResolved) Field("Closed at", ticket.closedAtDisplay)
            }
        }

        if (ticket.isResolved) {
            FactoryCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Corrective action", style = MaterialTheme.typography.titleSmall)
                    Text(
                        ticket.correctiveAction?.takeIf { it.isNotBlank() }
                            ?: "No note recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        when (ticket.status) {
            TicketStatus.OPEN -> PrimaryButton(
                text = "Acknowledge",
                onClick = onAcknowledge,
                loading = isActing,
                enabled = !isActing,
                modifier = Modifier.fillMaxWidth(),
            )
            TicketStatus.ACKNOWLEDGED -> PrimaryButton(
                text = "Resolve",
                onClick = onResolveClicked,
                style = PrimaryButtonStyle.Success,
                loading = isActing,
                enabled = !isActing,
                modifier = Modifier.fillMaxWidth(),
            )
            TicketStatus.RESOLVED -> Unit   // nothing left to do
        }
    }
}

@Composable
private fun Field(label: String, value: String?) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = FactoryTheme.colors.onSurfaceMuted,
            modifier = Modifier.fillMaxWidth(0.35f),
        )
        Text(
            value?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Photos auto-delete after 45 days, so an older ticket's URL can 404. */
@Composable
private fun Photo(url: String?) {
    val shape = RoundedCornerShape(12.dp)
    val fallback = @Composable {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.ImageNotSupported,
                    contentDescription = null,
                    tint = FactoryTheme.colors.onSurfaceMuted,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "No photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = FactoryTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
    if (url.isNullOrBlank()) {
        fallback()
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = "Ticket photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(240.dp).clip(shape),
        loading = {
            Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) {
                CircularProgressIndicator()
            }
        },
        error = { fallback() },
    )
}

@Composable
private fun ResolveDialog(
    text: String,
    canSubmit: Boolean,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolve ticket") },
        text = {
            Column {
                Text(
                    "What did you do to fix it?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FactoryTheme.colors.onSurfaceMuted,
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("e.g. Replaced the charging cable") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            // Disabled while blank: the server rejects an empty note with 400, so
            // the user should never be able to reach that error.
            TextButton(onClick = onSubmit, enabled = canSubmit) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
