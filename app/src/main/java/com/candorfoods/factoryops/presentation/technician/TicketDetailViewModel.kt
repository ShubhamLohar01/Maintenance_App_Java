package com.candorfoods.factoryops.presentation.technician

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.MachineRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.domain.usecase.breakdown.AcknowledgeBreakdownUseCase
import com.candorfoods.factoryops.domain.model.BreakdownSpare
import com.candorfoods.factoryops.domain.model.spareparts.MAX_WO_SPARES
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPick
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPickerCatalog
import com.candorfoods.factoryops.domain.repository.SparePartsRepository
import com.candorfoods.factoryops.domain.usecase.breakdown.ResolveBreakdownUseCase
import com.candorfoods.factoryops.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TicketDetailUiState(
    val flag: BreakdownFlag? = null,
    val machine: Machine? = null,
    val currentUser: User? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val showResolveSheet: Boolean = false,
    val partsUsedDraft: String = "",
    val afterPhotoDraft: String? = null,
    /** Spares picked for this repair, not yet posted. */
    val spares: List<BreakdownSpare> = emptyList(),
    /** Warehouse-scoped stock for the picker; loaded when the sheet opens. */
    val sparePicker: SparePartPickerCatalog = SparePartPickerCatalog(),
    val isLoadingPicker: Boolean = false,
) {
    /** The server rejects an eleventh spare, so the Add button stops at ten. */
    val canAddSpare: Boolean get() = spares.size < MAX_WO_SPARES
}

sealed interface TicketDetailUiEvent {
    data class ShowSnackbar(val message: String) : TicketDetailUiEvent
    object Acknowledged : TicketDetailUiEvent
    object Resolved : TicketDetailUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TicketDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val breakdownRepository: BreakdownRepository,
    machineRepository: MachineRepository,
    private val acknowledge: AcknowledgeBreakdownUseCase,
    private val resolve: ResolveBreakdownUseCase,
    private val sparePartsRepository: SparePartsRepository,
    getCurrentUser: GetCurrentUserUseCase
) : ViewModel() {

    val ticketId: String = checkNotNull(savedStateHandle[Screen.TicketDetail.ARG])

    private val _uiState = MutableStateFlow(TicketDetailUiState())
    val uiState: StateFlow<TicketDetailUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<TicketDetailUiEvent>()
    val uiEvent: SharedFlow<TicketDetailUiEvent> = _uiEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            getCurrentUser().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
        // The ticket may have been raised on another device (or opened before the
        // list synced), so it may not be in Room yet. Pull open breakdowns first; the
        // observer below then emits it. Best-effort — offline keeps whatever is local.
        viewModelScope.launch {
            breakdownRepository.observeSpares(ticketId).collect { spares ->
                _uiState.update { it.copy(spares = spares) }
            }
        }
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
                        // Stay in the loading state while the row is missing so the
                        // "not found" message isn't shown before the sync completes.
                        it.copy(flag = flag, machine = machine, isLoading = if (flag != null) false else it.isLoading)
                    }
                }
        }
    }

    fun onAcknowledge() {
        val state = _uiState.value
        val user = state.currentUser ?: return
        val flag = state.flag ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            when (val r = acknowledge(flag.id, user.id, user.name)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _uiEvent.emit(TicketDetailUiEvent.Acknowledged)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(TicketDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun onOpenResolveSheet() = _uiState.update {
        // The spares list is deliberately NOT cleared: a technician who opened the
        // sheet, picked parts and backed out to check something should find them
        // still there. resolveFlag() clears them once the server has taken them.
        it.copy(showResolveSheet = true, partsUsedDraft = "", afterPhotoDraft = null, error = null)
    }

    /**
     * Pickable stock for this ticket's machine. Loaded when the picker opens rather
     * than with the ticket, so a repair that consumes nothing costs no round trip.
     */
    fun refreshSparePicker() {
        val assetId = _uiState.value.flag?.machineId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPicker = true) }
            val catalog = sparePartsRepository.picker(assetId)
            _uiState.update { it.copy(sparePicker = catalog, isLoadingPicker = false) }
        }
    }

    fun onAddSpare(pick: SparePartPick, quantity: Double, notes: String?) {
        viewModelScope.launch {
            val r = breakdownRepository.logSpare(ticketId, pick, quantity, notes)
            if (r is Result.Error) _uiEvent.emit(TicketDetailUiEvent.ShowSnackbar(r.message))
        }
    }

    fun onDeleteSpare(spare: BreakdownSpare) {
        viewModelScope.launch { breakdownRepository.deleteSpare(spare.id) }
    }
    fun onDismissResolveSheet() = _uiState.update { it.copy(showResolveSheet = false) }
    fun onPartsUsedChanged(v: String) = _uiState.update { it.copy(partsUsedDraft = v, error = null) }
    fun onAfterPhotoCaptured(path: String?) = _uiState.update { it.copy(afterPhotoDraft = path) }

    fun onResolve() {
        val state = _uiState.value
        val user = state.currentUser ?: return
        val flag = state.flag ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            when (val r = resolve(flag.id, user.id, user.name, state.partsUsedDraft, state.afterPhotoDraft)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false, showResolveSheet = false) }
                    _uiEvent.emit(TicketDetailUiEvent.Resolved)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(TicketDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }
}
