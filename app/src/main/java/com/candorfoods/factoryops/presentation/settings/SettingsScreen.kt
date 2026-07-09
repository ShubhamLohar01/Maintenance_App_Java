package com.candorfoods.factoryops.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.candorfoods.factoryops.R
import com.candorfoods.factoryops.domain.model.AppLanguage
import com.candorfoods.factoryops.presentation.common.components.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is SettingsUiEvent.LoggedOut -> onLogout()
            }
        }
    }

    if (showLogoutDialog) {
        ConfirmDialog(
            title        = stringResource(R.string.confirm_logout_title),
            message      = stringResource(R.string.logout_confirm_message),
            confirmLabel = stringResource(R.string.action_logout),
            onConfirm    = { showLogoutDialog = false; viewModel.onLogout() },
            onDismiss    = { showLogoutDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier            = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.currentUser?.let { user ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(user.name, style = MaterialTheme.typography.titleMedium)
                        Text(user.username, style = MaterialTheme.typography.bodyMedium)
                        Text("${stringResource(R.string.settings_role)}: ${user.role.name}", style = MaterialTheme.typography.bodySmall)
                        Text("${stringResource(R.string.settings_plant)}: ${user.plantId}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            HorizontalDivider()

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_shift_schedule), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.shift_a), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.shift_b), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.shift_c), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_server), style = MaterialTheme.typography.titleSmall)
                    Text("Mode: Mock (debug build)", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("Phase 2: Coming Soon", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
                    AppLanguage.entries.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = lang == currentLanguage,
                                    onClick = { viewModel.onLanguageSelected(lang) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = lang == currentLanguage, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(lang.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick  = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled  = !uiState.isLoggingOut
            ) {
                Text(stringResource(R.string.action_logout))
            }
        }
    }
}
