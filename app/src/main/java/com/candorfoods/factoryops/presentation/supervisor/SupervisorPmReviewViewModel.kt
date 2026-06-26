package com.candorfoods.factoryops.presentation.supervisor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.repository.MachineRepository
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
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

data class SupervisorPmReviewUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val workOrder: PmWorkOrder? = null,
    val machine: Machine? = null,
    val currentUser: User? = null,
    val error: String? = null
)

sealed class SupervisorPmReviewEvent {
    data object Approved : SupervisorPmReviewEvent()
    data object Rejected : SupervisorPmReviewEvent()
    data class ShowError(val message: String) : SupervisorPmReviewEvent()
}

@HiltViewModel
class SupervisorPmReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val woRepo: PmWorkOrderRepository,
    private val machineRepo: MachineRepository,
    private val getCurrentUser: GetCurrentUserUseCase
) : ViewModel() {

    private val woId: String = checkNotNull(savedStateHandle[Screen.SupervisorPmReview.ARG])

    private val _uiState = MutableStateFlow(SupervisorPmReviewUiState())
    val uiState: StateFlow<SupervisorPmReviewUiState> = _uiState.asStateFlow()

    private val _events = Channel<SupervisorPmReviewEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val user = getCurrentUser().first()
            _uiState.update { it.copy(currentUser = user) }
        }
        viewModelScope.launch {
            woRepo.observeById(woId).collect { wo ->
                if (wo == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Work order not found.") }
                    return@collect
                }
                val machine = machineRepo.observeAllMachines().first()
                    .firstOrNull { it.id == wo.machineId }
                _uiState.update { it.copy(isLoading = false, workOrder = wo, machine = machine) }
            }
        }
    }

    fun onApprove() {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            when (val r = woRepo.supervisorApprove(woId, user.id, user.name)) {
                is Result.Success -> _events.send(SupervisorPmReviewEvent.Approved)
                is Result.Error -> _events.send(SupervisorPmReviewEvent.ShowError(r.message))
                Result.Loading -> Unit
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun onReject(notes: String) {
        val trimmed = notes.trim()
        if (trimmed.length !in 4..500) {
            viewModelScope.launch {
                _events.send(SupervisorPmReviewEvent.ShowError("Reason must be 4–500 characters."))
            }
            return
        }
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            when (val r = woRepo.supervisorReject(woId, user.id, user.name, trimmed)) {
                is Result.Success -> _events.send(SupervisorPmReviewEvent.Rejected)
                is Result.Error -> _events.send(SupervisorPmReviewEvent.ShowError(r.message))
                Result.Loading -> Unit
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }
}
