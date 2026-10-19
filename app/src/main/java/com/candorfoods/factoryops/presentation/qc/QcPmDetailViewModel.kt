package com.candorfoods.factoryops.presentation.qc

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.placeholderMachine
import com.candorfoods.factoryops.domain.model.toMachine
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class QcPmDetailUiState(
    val workOrder: PmWorkOrder? = null,
    val machine: Machine? = null,
    val currentUser: User? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val showChecklistSheet: Boolean = false,
    val checklist: QcChecklistDraft = QcChecklistDraft()
) {
    val isHeldByMe: Boolean
        get() = workOrder?.qcAcknowledgedBy != null && workOrder.qcAcknowledgedBy == currentUser?.id
    val isHeldByOther: Boolean
        get() = workOrder?.qcAcknowledgedBy != null && workOrder.qcAcknowledgedBy != currentUser?.id
    val isQcHead: Boolean get() = currentUser?.role == UserRole.QC_HEAD

    /** Only the QC role acts on QC; every other role (Supervisor/Head/…) is view-only. */
    val isQcRole: Boolean
        get() = currentUser?.role == UserRole.QC_HEAD || currentUser?.role == UserRole.QC_MEMBER
}

sealed interface QcPmDetailUiEvent {
    data class ShowSnackbar(val message: String) : QcPmDetailUiEvent
    object Acknowledged : QcPmDetailUiEvent
    object Overridden : QcPmDetailUiEvent
    object Approved : QcPmDetailUiEvent
    object Disapproved : QcPmDetailUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QcPmDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pmRepo: PmWorkOrderRepository,
    private val mtMachineRepo: MtMachineRepository,
    getCurrentUser: GetCurrentUserUseCase
) : ViewModel() {

    private val woId: String = checkNotNull(savedStateHandle[Screen.QcPmDetail.ARG])

    private val json = Json { encodeDefaults = true }

    private val _uiState = MutableStateFlow(QcPmDetailUiState())
    val uiState: StateFlow<QcPmDetailUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<QcPmDetailUiEvent>()
    val uiEvent: SharedFlow<QcPmDetailUiEvent> = _uiEvent.asSharedFlow()

    // machineId is an mt_asset_list.asset_id; resolve once (network) then reuse.
    private var cachedMachine: Machine? = null

    init {
        // Best-effort: re-pull this WO so QC reviews the latest server state (supervisor
        // approval, another QC's pickup) rather than a stale local copy.
        viewModelScope.launch { runCatching { pmRepo.refreshWorkOrder(woId) } }
        viewModelScope.launch {
            getCurrentUser().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
        viewModelScope.launch {
            pmRepo.observeById(woId).collect { wo ->
                if (wo == null) {
                    _uiState.update { it.copy(workOrder = null, machine = null, isLoading = false) }
                    return@collect
                }
                if (cachedMachine == null) {
                    cachedMachine = (mtMachineRepo.getByAssetId(wo.machineId) as? Result.Success)?.data?.toMachine()
                        ?: placeholderMachine(wo.machineId, wo.templateName)
                }
                _uiState.update { it.copy(workOrder = wo, machine = cachedMachine, isLoading = false) }
            }
        }
    }

    fun onAcknowledgeForQc() {
        val state = _uiState.value
        if (!state.isQcRole) return
        val user = state.currentUser ?: return
        val wo = state.workOrder ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            when (val r = pmRepo.qcAcknowledge(wo.id, user.id, user.name)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _uiEvent.emit(QcPmDetailUiEvent.Acknowledged)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(QcPmDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun onOverrideAcknowledgeAsHead() {
        val state = _uiState.value
        val user = state.currentUser ?: return
        val wo = state.workOrder ?: return
        if (user.role != UserRole.QC_HEAD) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            when (val r = pmRepo.qcOverrideAcknowledge(wo.id, user.id, user.name)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _uiEvent.emit(QcPmDetailUiEvent.Overridden)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(QcPmDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun onOpenChecklistSheet() = _uiState.update {
        it.copy(showChecklistSheet = true, checklist = QcChecklistDraft(), error = null)
    }

    fun onDismissChecklistSheet() = _uiState.update { it.copy(showChecklistSheet = false) }

    fun onChecklistChanged(draft: QcChecklistDraft) =
        _uiState.update { it.copy(checklist = draft, error = null) }

    fun onApprove() {
        val state = _uiState.value
        if (!state.isQcRole) return
        val user = state.currentUser ?: return
        val wo = state.workOrder ?: return
        val draft = state.checklist
        if (!draft.canApprove) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val checklistJson = json.encodeToString(draft.toSnapshot())
            when (val r = pmRepo.qcApprove(
                woId = wo.id,
                userId = user.id,
                userName = user.name,
                checklistJson = checklistJson,
                afterPhotoLocalPath = draft.afterPhotoLocalPath,
                notes = draft.notes.takeIf { it.isNotBlank() }
            )) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false, showChecklistSheet = false) }
                    _uiEvent.emit(QcPmDetailUiEvent.Approved)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(QcPmDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun onDisapprove(reason: String) {
        val state = _uiState.value
        if (!state.isQcRole) return
        val user = state.currentUser ?: return
        val wo = state.workOrder ?: return
        val draft = state.checklist
        val trimmed = reason.trim()
        if (!draft.canDisapprove) return
        if (trimmed.length !in 4..500) {
            viewModelScope.launch {
                _uiEvent.emit(QcPmDetailUiEvent.ShowSnackbar("Reason must be 4–500 characters."))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val checklistJson = json.encodeToString(draft.toSnapshot())
            when (val r = pmRepo.qcDisapprove(
                woId = wo.id,
                userId = user.id,
                userName = user.name,
                checklistJson = checklistJson,
                afterPhotoLocalPath = draft.afterPhotoLocalPath,
                reason = trimmed
            )) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false, showChecklistSheet = false) }
                    _uiEvent.emit(QcPmDetailUiEvent.Disapproved)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(QcPmDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }
}
