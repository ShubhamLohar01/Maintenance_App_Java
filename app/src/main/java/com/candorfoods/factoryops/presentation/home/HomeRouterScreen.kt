package com.candorfoods.factoryops.presentation.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeRouterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _giveUp = MutableStateFlow(false)
    val giveUp: StateFlow<Boolean> = _giveUp.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getCurrentUser().collect { u ->
                _user.value = u
            }
        }
        viewModelScope.launch {
            // Defensive timeout: if no user record materialises in 4s,
            // the router gives up and routes back to Login.
            kotlinx.coroutines.delay(4000)
            if (_user.value == null) _giveUp.value = true
        }
    }
}

@Composable
fun HomeRouterScreen(
    onResolved: (UserRole) -> Unit,
    onGiveUp: () -> Unit,
    viewModel: HomeRouterViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val giveUp by viewModel.giveUp.collectAsStateWithLifecycle()

    LaunchedEffect(user) {
        user?.let { onResolved(it.role) }
    }
    LaunchedEffect(giveUp, user) {
        if (giveUp && user == null) onGiveUp()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (giveUp && user == null) {
            Text("Session lost. Returning to login…", style = MaterialTheme.typography.bodyMedium)
        } else {
            CircularProgressIndicator()
        }
    }
}
