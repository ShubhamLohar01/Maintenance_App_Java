package com.candorfoods.factoryops.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.candorfoods.factoryops.data.local.LanguagePreferences
import com.candorfoods.factoryops.domain.model.AppLanguage
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.domain.usecase.auth.LogoutUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val currentUser: User? = null,
    val isLoggingOut: Boolean = false
)

sealed interface SettingsUiEvent {
    object LoggedOut : SettingsUiEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val languagePreferences: LanguagePreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val currentLanguage: StateFlow<AppLanguage> = languagePreferences.currentLanguage

    private val _uiEvent = MutableSharedFlow<SettingsUiEvent>()
    val uiEvent: SharedFlow<SettingsUiEvent> = _uiEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            getCurrentUser().collect { user -> _uiState.update { it.copy(currentUser = user) } }
        }
    }

    fun onLanguageSelected(language: AppLanguage) {
        val userId = _uiState.value.currentUser?.id ?: return
        languagePreferences.setLanguageForUser(userId, language)
    }

    fun onLogout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            logoutUseCase()
            _uiState.update { it.copy(isLoggingOut = false) }
            _uiEvent.emit(SettingsUiEvent.LoggedOut)
        }
    }
}
