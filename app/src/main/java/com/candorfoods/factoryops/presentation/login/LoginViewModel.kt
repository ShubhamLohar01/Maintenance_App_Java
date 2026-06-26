package com.candorfoods.factoryops.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.AuthRepository
import com.candorfoods.factoryops.domain.usecase.auth.LoginUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String  = "",
    val password: String  = "",
    val isLoading: Boolean = false,
    val error: String?    = null
)

sealed interface LoginUiEvent {
    object NavigateToHome : LoginUiEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<LoginUiEvent>()
    val uiEvent: SharedFlow<LoginUiEvent> = _uiEvent.asSharedFlow()

    val isLoggedIn: StateFlow<Boolean> = MutableStateFlow(authRepository.isLoggedIn()).asStateFlow()

    fun onUsernameChanged(v: String) = _uiState.update { it.copy(username = v, error = null) }
    fun onPasswordChanged(v: String) = _uiState.update { it.copy(password = v, error = null) }

    fun onLoginClicked() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = loginUseCase(_uiState.value.username, _uiState.value.password)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(LoginUiEvent.NavigateToHome)
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                is Result.Loading -> Unit
            }
        }
    }
}
