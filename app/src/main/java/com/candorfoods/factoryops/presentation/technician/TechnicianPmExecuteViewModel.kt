package com.candorfoods.factoryops.presentation.technician

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.PmTaskLogStatus
import com.candorfoods.factoryops.domain.model.PmWoSpare
import com.candorfoods.factoryops.domain.model.spareparts.MAX_WO_SPARES
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPick
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPickerCatalog
import com.candorfoods.factoryops.domain.model.PmWoTaskLog
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
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

/**
 * Local draft for the currently displayed item. The per-item verdict is a single
 * "Is good?" checkbox: ticked → the item is good (remark optional); un-ticked → not good
 * (remark REQUIRED). Persisted as the task-log status (PASS = good, FAIL = not good).
 */
data class ItemExecutionDraft(
    val logId: String,
    val isGood: Boolean = false,
    val notes: String = ""
)

data class TechnicianPmExecuteUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val workOrder: PmWorkOrder? = null,
    val currentUser: User? = null,
    val currentIndex: Int = 0,
    val draft: ItemExecutionDraft? = null,
    val finalNotes: String = "",
    /** Machine-level verdict chosen on the summary screen: "PASS" | "FAIL" | null. */
    val overallResult: String? = null,
    /** Warehouse-scoped stock for the picker; empty until the sheet is opened. */
    val sparePicker: SparePartPickerCatalog = SparePartPickerCatalog(),
    val isLoadingPicker: Boolean = false,
    val error: String? = null
) {
    val totalItems: Int get() = workOrder?.taskLogs?.size ?: 0

    /** The server rejects an eleventh spare, so the Add button stops at ten. */
    val canAddSpare: Boolean get() = (workOrder?.spares?.size ?: 0) < MAX_WO_SPARES
    val isOnSummary: Boolean get() = currentIndex >= totalItems
    val currentLog: PmWoTaskLog? get() = workOrder?.taskLogs?.getOrNull(currentIndex)
}

sealed class TechnicianPmExecuteEvent {
    data object Submitted : TechnicianPmExecuteEvent()
    data object DraftSaved : TechnicianPmExecuteEvent()
    data class ShowError(val message: String) : TechnicianPmExecuteEvent()
}

@HiltViewModel
class TechnicianPmExecuteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val woRepo: PmWorkOrderRepository,
    private val getCurrentUser: GetCurrentUserUseCase
) : ViewModel() {

    private val woId: String = checkNotNull(savedStateHandle[Screen.TechnicianPmExecute.ARG])

    private val _uiState = MutableStateFlow(TechnicianPmExecuteUiState())
    val uiState: StateFlow<TechnicianPmExecuteUiState> = _uiState.asStateFlow()

    private val _events = Channel<TechnicianPmExecuteEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val user = getCurrentUser().first()
            _uiState.update { it.copy(currentUser = user) }
        }
        viewModelScope.launch {
            woRepo.observeById(woId).collect { wo ->
                if (wo == null) {
                    _uiState.update { it.copy(isLoading = false, error = "PM work order not found.") }
                    return@collect
                }
                val state = _uiState.value
                // Auto-resume on first load — jump to first PENDING item, or summary if all done.
                val resumeIndex = if (!state.isLoading) state.currentIndex
                else wo.taskLogs.indexOfFirst { it.status == PmTaskLogStatus.PENDING }
                    .let { if (it < 0) wo.taskLogs.size else it }

                val draft = wo.taskLogs.getOrNull(resumeIndex)?.let { it.toDraft() }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        workOrder = wo,
                        currentIndex = resumeIndex,
                        draft = draft,
                        finalNotes = it.finalNotes.ifBlank { wo.finalNotes.orEmpty() },
                        overallResult = it.overallResult ?: wo.overallResult
                    )
                }
            }
        }
    }

    private fun PmWoTaskLog.toDraft() = ItemExecutionDraft(
        logId = id,
        isGood = status == PmTaskLogStatus.PASS,
        notes = notes.orEmpty()
    )

    fun onIsGoodChange(isGood: Boolean) =
        _uiState.update { it.copy(draft = it.draft?.copy(isGood = isGood)) }

    fun onNotesChange(v: String) =
        _uiState.update { it.copy(draft = it.draft?.copy(notes = v)) }

    fun onFinalNotesChange(v: String) = _uiState.update { it.copy(finalNotes = v) }

    /** Machine-level PASS/FAIL selected on the summary screen. */
    fun onOverallResultChange(result: String) = _uiState.update { it.copy(overallResult = result) }

    /** Validates and persists the current item, then advances to the next index (or summary). */
    fun onNext() {
        val state = _uiState.value
        val draft = state.draft ?: return
        state.currentLog ?: return
        val user = state.currentUser ?: return

        // "Is good?" rule: ticked → remark optional; un-ticked (not good) → remark REQUIRED.
        if (!draft.isGood && draft.notes.isBlank()) {
            postError("This item is not marked good — add a remark explaining the issue, or tick \"Is good?\".")
            return
        }
        val status = if (draft.isGood) PmTaskLogStatus.PASS else PmTaskLogStatus.FAIL

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = woRepo.logTaskResult(
                logId = draft.logId,
                woId = woId,
                status = status,
                notes = draft.notes.takeIf { it.isNotBlank() },
                userId = user.id
            )
            if (result is Result.Error) {
                _events.send(TechnicianPmExecuteEvent.ShowError(result.message))
                _uiState.update { it.copy(isSubmitting = false) }
                return@launch
            }
            // Advance index. Observation flow will refresh draft from updated WO.
            _uiState.update {
                val next = it.currentIndex + 1
                val nextLog = it.workOrder?.taskLogs?.getOrNull(next)
                it.copy(
                    isSubmitting = false,
                    currentIndex = next,
                    draft = nextLog?.toDraft()
                )
            }
        }
    }

    fun onBack() {
        _uiState.update {
            if (it.currentIndex == 0) return@update it
            val prev = it.currentIndex - 1
            val prevLog = it.workOrder?.taskLogs?.getOrNull(prev)
            it.copy(currentIndex = prev, draft = prevLog?.toDraft())
        }
    }

    fun onJumpToItem(index: Int) {
        _uiState.update {
            val safe = index.coerceIn(0, it.totalItems)
            val log = it.workOrder?.taskLogs?.getOrNull(safe)
            it.copy(currentIndex = safe, draft = log?.toDraft())
        }
    }

    /** Save draft = exit. The per-item upserts already happened on Next; nothing more to persist. */
    fun onSaveDraft() {
        viewModelScope.launch { _events.send(TechnicianPmExecuteEvent.DraftSaved) }
    }

    fun onSubmit() {
        val state = _uiState.value
        val wo = state.workOrder ?: return
        if (wo.taskLogs.any { it.status == PmTaskLogStatus.PENDING }) {
            postError("Answer \"Is good?\" on every item before submit.")
            return
        }
        val overall = state.overallResult
        if (overall.isNullOrBlank()) {
            postError("Select the machine result — Pass or Fail — before submit.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            when (val r = woRepo.submit(woId, state.finalNotes.takeIf { it.isNotBlank() }, overall)) {
                is Result.Success -> _events.send(TechnicianPmExecuteEvent.Submitted)
                is Result.Error -> _events.send(TechnicianPmExecuteEvent.ShowError(r.message))
                Result.Loading -> Unit
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun onAddSpare(pick: SparePartPick, qty: Double, notes: String?) {
        val user = _uiState.value.currentUser ?: return
        if (qty <= 0.0) return
        viewModelScope.launch {
            when (val r = woRepo.logSpare(woId, pick, qty, notes, user.id)) {
                is Result.Error -> _events.send(TechnicianPmExecuteEvent.ShowError(r.message))
                else -> Unit
            }
        }
    }

    fun onDeleteSpare(spare: PmWoSpare) {
        viewModelScope.launch { woRepo.deleteSpare(spare.id, woId) }
    }

    /**
     * Load the pickable stock for this PM's machine. Called when the sheet opens
     * rather than with the work order, so a technician who logs no spares never
     * pays for the round trip.
     */
    fun refreshSparePicker() {
        val assetId = _uiState.value.workOrder?.machineId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPicker = true) }
            val catalog = woRepo.sparePicker(assetId)
            _uiState.update { it.copy(sparePicker = catalog, isLoadingPicker = false) }
        }
    }

    private fun postError(msg: String) {
        viewModelScope.launch { _events.send(TechnicianPmExecuteEvent.ShowError(msg)) }
    }
}
