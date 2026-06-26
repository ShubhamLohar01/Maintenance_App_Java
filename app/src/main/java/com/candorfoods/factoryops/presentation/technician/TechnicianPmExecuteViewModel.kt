package com.candorfoods.factoryops.presentation.technician

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.PmTaskLogStatus
import com.candorfoods.factoryops.domain.model.PmWoSpare
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
 * Local draft for the currently displayed item. Mirrors task-log fields plus a measurement
 * string so the user's typed value can be edited freely before parsing.
 */
data class ItemExecutionDraft(
    val logId: String,
    val status: PmTaskLogStatus = PmTaskLogStatus.PENDING,
    val measurementText: String = "",
    val photoLocalPath: String? = null,
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
    val spareSuggestions: List<String> = emptyList(),
    val error: String? = null
) {
    val totalItems: Int get() = workOrder?.taskLogs?.size ?: 0
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
                        finalNotes = it.finalNotes.ifBlank { wo.finalNotes.orEmpty() }
                    )
                }
            }
        }
    }

    private fun PmWoTaskLog.toDraft() = ItemExecutionDraft(
        logId = id,
        status = status,
        measurementText = measurementValue?.toString().orEmpty(),
        photoLocalPath = photoLocalPath,
        notes = notes.orEmpty()
    )

    fun onStatusSelect(status: PmTaskLogStatus) =
        _uiState.update { it.copy(draft = it.draft?.copy(status = status)) }

    fun onMeasurementChange(v: String) =
        _uiState.update {
            // allow digits, dot, minus
            val cleaned = v.filter { c -> c.isDigit() || c == '.' || c == '-' }
            it.copy(draft = it.draft?.copy(measurementText = cleaned))
        }

    fun onPhotoCaptured(path: String?) =
        _uiState.update { it.copy(draft = it.draft?.copy(photoLocalPath = path)) }

    fun onNotesChange(v: String) =
        _uiState.update { it.copy(draft = it.draft?.copy(notes = v)) }

    fun onFinalNotesChange(v: String) = _uiState.update { it.copy(finalNotes = v) }

    /** Validates and persists the current item, then advances to the next index (or summary). */
    fun onNext() {
        val state = _uiState.value
        val draft = state.draft ?: return
        val log = state.currentLog ?: return
        val user = state.currentUser ?: return

        // Validation
        if (draft.status == PmTaskLogStatus.PENDING) {
            postError("Pick Pass, Fail, or N/A before continuing.")
            return
        }
        if (log.requiresPhoto && draft.photoLocalPath == null) {
            postError("This item requires a photo.")
            return
        }
        val measurement = draft.measurementText.toDoubleOrNull()
        if (log.requiresMeasurement && measurement == null && draft.status != PmTaskLogStatus.NOT_APPLICABLE) {
            postError("Enter a numeric measurement (or mark N/A).")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = woRepo.logTaskResult(
                logId = draft.logId,
                woId = woId,
                status = draft.status,
                measurementValue = measurement,
                photoLocalPath = draft.photoLocalPath,
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
            viewModelScope.launch {
                _events.send(TechnicianPmExecuteEvent.ShowError("All items must be completed before submit."))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            when (val r = woRepo.submit(woId, state.finalNotes.takeIf { it.isNotBlank() })) {
                is Result.Success -> _events.send(TechnicianPmExecuteEvent.Submitted)
                is Result.Error -> _events.send(TechnicianPmExecuteEvent.ShowError(r.message))
                Result.Loading -> Unit
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun onAddSpare(name: String, qty: Double, unit: String, notes: String?) {
        val user = _uiState.value.currentUser ?: return
        if (name.isBlank() || qty <= 0.0) return
        viewModelScope.launch {
            woRepo.logSpare(
                woId = woId,
                spareName = name,
                quantityUsed = qty,
                unit = unit,
                notes = notes,
                userId = user.id
            )
        }
    }

    fun onDeleteSpare(spare: PmWoSpare) {
        viewModelScope.launch { woRepo.deleteSpare(spare.id, woId) }
    }

    fun refreshSpareSuggestions(query: String) {
        viewModelScope.launch {
            val list = woRepo.suggestSpareNames(query, limit = 10)
            _uiState.update { it.copy(spareSuggestions = list) }
        }
    }

    private fun postError(msg: String) {
        viewModelScope.launch { _events.send(TechnicianPmExecuteEvent.ShowError(msg)) }
    }
}
