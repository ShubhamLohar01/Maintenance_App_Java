package com.candorfoods.factoryops.presentation.user

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.MtUser
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing

/** HEAD/ADMIN user directory: list + add + edit + delete over /mt-users. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUsersScreen(
    onBack: () -> Unit,
    viewModel: ManageUsersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Manage Users") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.canManage) {
                FloatingActionButton(onClick = viewModel::onAddClicked) {
                    Icon(Icons.Default.Add, contentDescription = "Add new user")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.currentUser != null && !uiState.canManage ->
                    Centered("Only Head can manage users.")
                uiState.isLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null ->
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            uiState.error!!,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Button(onClick = viewModel::onRefresh) { Text("Retry") }
                    }
                uiState.users.isEmpty() ->
                    Centered("No users yet. Tap + to add one.")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(uiState.users, key = { it.id }) { user ->
                        UserRow(
                            user = user,
                            onEdit = { viewModel.onEditClicked(user) },
                            onDelete = { viewModel.onDeleteClicked(user) },
                        )
                    }
                }
            }
        }
    }

    if (uiState.showSheet) {
        UserFormSheet(
            existing = uiState.editing,
            isSaving = uiState.isSaving,
            error = uiState.saveError,
            onDismiss = viewModel::onSheetDismiss,
            onSubmit = viewModel::onSubmit,
        )
    }

    uiState.pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismiss,
            title = { Text("Delete user?") },
            text = { Text("Remove ${target.name} (${target.username})? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmDelete, enabled = !uiState.isDeleting) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteDismiss) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BoxScope.Centered(text: String) {
    Text(
        text,
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
}

@Composable
private fun UserRow(user: MtUser, onEdit: () -> Unit, onDelete: () -> Unit) {
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(user.username)
                        user.role?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                        user.location?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
    }
}
