package com.candorfoods.factoryops.presentation.breakdown

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.usecase.breakdown.RaiseBreakdownFlagUseCase
import com.candorfoods.factoryops.navigation.Screen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BreakdownFlagUiState(
    val severity: Severity     = Severity.MAJOR,
    val description: String    = "",
    val beforePhotoLocalPath: String? = null,
    val isLoading: Boolean     = false,
    val error: String?         = null
)

sealed interface BreakdownFlagUiEvent {
    object FlagRaised : BreakdownFlagUiEvent
    data class ShowSnackbar(val message: String) : BreakdownFlagUiEvent
}

@HiltViewModel
class BreakdownFlagViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val raiseBreakdownFlag: RaiseBreakdownFlagUseCase
) : ViewModel() {

    val machineId: String = checkNotNull(savedStateHandle[Screen.BreakdownFlag.ARG])

    private val _uiState = MutableStateFlow(BreakdownFlagUiState())
    val uiState: StateFlow<BreakdownFlagUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<BreakdownFlagUiEvent>()
    val uiEvent: SharedFlow<BreakdownFlagUiEvent> = _uiEvent.asSharedFlow()

    fun onSeverityChanged(s: Severity)    = _uiState.update { it.copy(severity = s) }
    fun onDescriptionChanged(d: String)   = _uiState.update { it.copy(description = d, error = null) }
    fun onPhotoSelected(path: String?)    = _uiState.update { it.copy(beforePhotoLocalPath = path) }

    fun onSubmit() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val s = _uiState.value
            when (val r = raiseBreakdownFlag(machineId, s.severity, s.description, s.beforePhotoLocalPath)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(BreakdownFlagUiEvent.FlagRaised)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = r.message) }
                    _uiEvent.emit(BreakdownFlagUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
