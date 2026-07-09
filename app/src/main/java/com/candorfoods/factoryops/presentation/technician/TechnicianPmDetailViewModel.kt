package com.candorfoods.factoryops.presentation.technician

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.placeholderMachine
import com.candorfoods.factoryops.domain.model.toMachine
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TechnicianPmDetailUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val workOrder: PmWorkOrder? = null,
    val machine: Machine? = null,
    val error: String? = null
)

sealed class TechnicianPmDetailEvent {
    data object Acknowledged : TechnicianPmDetailEvent()
    data object Started : TechnicianPmDetailEvent()
    data class ShowError(val message: String) : TechnicianPmDetailEvent()
}

@HiltViewModel
class TechnicianPmDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val woRepo: PmWorkOrderRepository,
    private val mtMachineRepo: MtMachineRepository
) : ViewModel() {

    private val woId: String = checkNotNull(savedStateHandle[Screen.TechnicianPmDetail.ARG])

    private val _uiState = MutableStateFlow(TechnicianPmDetailUiState())
    val uiState: StateFlow<TechnicianPmDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<TechnicianPmDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // machineId is an mt_asset_list.asset_id; resolve once (network) then reuse.
    private var cachedMachine: Machine? = null

    init {
        viewModelScope.launch {
            woRepo.observeById(woId).collect { wo ->
                if (wo == null) {
                    _uiState.update { it.copy(isLoading = false, error = "PM work order not found.") }
                    return@collect
                }
                if (cachedMachine == null) {
                    cachedMachine = (mtMachineRepo.getByAssetId(wo.machineId) as? Result.Success)?.data?.toMachine()
                        ?: placeholderMachine(wo.machineId, wo.templateName)
                }
                _uiState.update { it.copy(isLoading = false, workOrder = wo, machine = cachedMachine) }
            }
        }
    }

    fun onAcknowledge() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            when (val r = woRepo.acknowledge(woId)) {
                is Result.Success -> _events.send(TechnicianPmDetailEvent.Acknowledged)
                is Result.Error -> _events.send(TechnicianPmDetailEvent.ShowError(r.message))
                Result.Loading -> Unit
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun onStart() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            when (val r = woRepo.start(woId)) {
                is Result.Success -> _events.send(TechnicianPmDetailEvent.Started)
                is Result.Error -> _events.send(TechnicianPmDetailEvent.ShowError(r.message))
                Result.Loading -> Unit
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }
}
