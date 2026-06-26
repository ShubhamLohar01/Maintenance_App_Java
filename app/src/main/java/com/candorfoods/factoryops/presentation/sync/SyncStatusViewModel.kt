package com.candorfoods.factoryops.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.SyncQueueItem
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.EnergyRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncStatusUiState(
    val pendingCount: Int             = 0,
    val syncQueue: List<SyncQueueItem> = emptyList(),
    val pendingBreakdowns: List<BreakdownFlag> = emptyList(),
    val isSyncing: Boolean            = false
)

@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val energyRepository: EnergyRepository,
    private val breakdownRepository: BreakdownRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncStatusUiState())
    val uiState: StateFlow<SyncStatusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            energyRepository.getPendingSyncCount().collect { count ->
                _uiState.update { it.copy(pendingCount = count) }
            }
        }
        viewModelScope.launch {
            breakdownRepository.getSyncQueue().collect { queue ->
                _uiState.update { it.copy(syncQueue = queue) }
            }
        }
        viewModelScope.launch {
            breakdownRepository.getPendingBreakdowns().collect { flags ->
                _uiState.update { it.copy(pendingBreakdowns = flags) }
            }
        }
    }

    fun onRetrySync() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            energyRepository.syncPendingRuns()
            _uiState.update { it.copy(isSyncing = false) }
        }
    }
}
