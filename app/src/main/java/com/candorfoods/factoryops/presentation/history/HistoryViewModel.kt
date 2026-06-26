package com.candorfoods.factoryops.presentation.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.candorfoods.factoryops.domain.model.ProductionRun
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.usecase.energy.GetMachineHistoryUseCase
import com.candorfoods.factoryops.navigation.Screen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val runs: List<ProductionRun> = emptyList(),
    val isLoading: Boolean         = true,
    val error: String?             = null,
    val machineId: String          = ""
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMachineHistory: GetMachineHistoryUseCase
) : ViewModel() {

    private val machineId: String = checkNotNull(savedStateHandle[Screen.History.ARG])

    private val _uiState = MutableStateFlow(HistoryUiState(machineId = machineId))
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init { loadHistory() }

    private fun loadHistory() {
        viewModelScope.launch {
            val to   = System.currentTimeMillis()
            val from = to - 30L * 24 * 3_600_000L
            getMachineHistory(machineId, from, to).collect { result ->
                when (result) {
                    is Result.Loading -> _uiState.update { it.copy(isLoading = true) }
                    is Result.Success -> _uiState.update { it.copy(isLoading = false, runs = result.data, error = null) }
                    is Result.Error   -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }
}
