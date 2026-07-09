package com.candorfoods.factoryops.presentation.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.candorfoods.factoryops.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.presentation.common.components.LoadingOverlay
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize

@Composable
fun LoginScreen(
    onNavigateToHome: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is LoginUiEvent.NavigateToHome -> onNavigateToHome()
            }
        }
    }
    LoadingOverlay(isLoading = uiState.isLoading) {
        LoginContent(
            uiState              = uiState,
            onUsernameChanged    = viewModel::onUsernameChanged,
            onPasswordChanged    = viewModel::onPasswordChanged,
            onLoginClicked       = viewModel::onLoginClicked
        )
    }
}

@Composable
private fun LoginContent(
    uiState: LoginUiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClicked: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Scaffold { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("FactoryOps", style = MaterialTheme.typography.headlineLarge)
                Text("Candor Foods", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value           = uiState.username,
                    onValueChange   = onUsernameChanged,
                    label           = { Text(stringResource(R.string.label_username)) },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect    = false,
                        keyboardType   = KeyboardType.Ascii,
                        imeAction      = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier        = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value           = uiState.password,
                    onValueChange   = onPasswordChanged,
                    label           = { Text(stringResource(R.string.label_password)) },
                    singleLine      = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect    = false,
                        keyboardType   = KeyboardType.Password,
                        imeAction      = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onLoginClicked() }),
                    trailingIcon    = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (uiState.error != null) {
                    Text(
                        uiState.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                PrimaryButton(
                    text = stringResource(R.string.action_login),
                    onClick = onLoginClicked,
                    enabled = !uiState.isLoading,
                    size = PrimaryButtonSize.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
