package com.candorfoods.factoryops.presentation.qc

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.MachineRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class QcConfirmKind { APPROVE, DISAPPROVE }

data class QcTicketDetailUiState(
    val flag: BreakdownFlag? = null,
    val machine: Machine? = null,
    val currentUser: User? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val pendingConfirm: QcConfirmKind? = null
) {
    val isHeldByMe: Boolean
        get() = flag?.qcAcknowledgedBy != null && flag.qcAcknowledgedBy == currentUser?.id
    val isHeldByOther: Boolean
        get() = flag?.qcAcknowledgedBy != null && flag.qcAcknowledgedBy != currentUser?.id
    val isQcHead: Boolean get() = currentUser?.role == UserRole.QC_HEAD
}

sealed interface QcTicketDetailUiEvent {
    data class ShowSnackbar(val message: String) : QcTicketDetailUiEvent
    object Acknowledged : QcTicketDetailUiEvent
    object Overridden : QcTicketDetailUiEvent
    object Approved : QcTicketDetailUiEvent
    object Disapproved : QcTicketDetailUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QcTicketDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val breakdownRepository: BreakdownRepository,
    machineRepository: MachineRepository,
    getCurrentUser: GetCurrentUserUseCase
) : ViewModel() {

    val ticketId: String = checkNotNull(savedStateHandle[Screen.QcTicketDetail.ARG])

    private val _uiState = MutableStateFlow(QcTicketDetailUiState())
    val uiState: StateFlow<QcTicketDetailUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<QcTicketDetailUiEvent>()
    val uiEvent: SharedFlow<QcTicketDetailUiEvent> = _uiEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            getCurrentUser().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
        // Pull open breakdowns so a ticket opened from any QC list / another device
        // resolves locally. Best-effort — offline keeps whatever is already in Room.
        viewModelScope.launch {
            breakdownRepository.refreshOpenFlags("both")
            if (_uiState.value.flag == null) _uiState.update { it.copy(isLoading = false) }
        }
        viewModelScope.launch {
            breakdownRepository.observeFlagById(ticketId)
                .flatMapLatest { flag ->
                    if (flag == null) flowOf(null to null)
                    else machineRepository.getMachineById(flag.machineId).map { m -> flag to m }
                }
                .collect { (flag, machine) ->
                    _uiState.update {
                        it.copy(flag = flag, machine = machine, isLoading = if (flag != null) false else it.isLoading)
                    }
                }
        }
    }

    fun onAcknowledgeForQc() {
        val state = _uiState.value
        val user = state.currentUser ?: return
        val flag = state.flag ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            when (val r = breakdownRepository.qcAcknowledge(flag.id, user.id, user.name, user.username)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _uiEvent.emit(QcTicketDetailUiEvent.Acknowledged)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(QcTicketDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun onOverrideAcknowledgeAsHead() {
        val state = _uiState.value
        val user = state.currentUser ?: return
        val flag = state.flag ?: return
        if (user.role != UserRole.QC_HEAD) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            when (val r = breakdownRepository.qcOverrideAcknowledge(flag.id, user.id, user.name)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _uiEvent.emit(QcTicketDetailUiEvent.Overridden)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(QcTicketDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    // ----- Confirm dialogs -----------------------------------------------------

    fun onRequestApprove() = _uiState.update { it.copy(pendingConfirm = QcConfirmKind.APPROVE) }
    fun onRequestDisapprove() = _uiState.update { it.copy(pendingConfirm = QcConfirmKind.DISAPPROVE) }
    fun onCancelConfirm() = _uiState.update { it.copy(pendingConfirm = null) }

    fun onConfirmApprove() {
        val state = _uiState.value
        val user = state.currentUser ?: return
        val flag = state.flag ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, pendingConfirm = null) }
            when (val r = breakdownRepository.qcApprove(
                flagId = flag.id,
                userId = user.id,
                userName = user.name,
                checklistJson = EMPTY_CHECKLIST_JSON,
                afterPhotoLocalPath = null,
                notes = null
            )) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _uiEvent.emit(QcTicketDetailUiEvent.Approved)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(QcTicketDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun onConfirmDisapprove(reason: String) {
        val state = _uiState.value
        val user = state.currentUser ?: return
        val flag = state.flag ?: return
        val trimmed = reason.trim()
        if (trimmed.length !in 4..500) {
            viewModelScope.launch {
                _uiEvent.emit(QcTicketDetailUiEvent.ShowSnackbar(
                    "Reason must be 4–500 characters."
                ))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, pendingConfirm = null) }
            when (val r = breakdownRepository.qcDisapprove(
                flagId = flag.id,
                userId = user.id,
                userName = user.name,
                checklistJson = EMPTY_CHECKLIST_JSON,
                afterPhotoLocalPath = null,
                reason = trimmed
            )) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _uiEvent.emit(QcTicketDetailUiEvent.Disapproved)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(QcTicketDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private companion object {
        const val EMPTY_CHECKLIST_JSON = "{}"
    }
}
