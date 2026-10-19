package com.candorfoods.factoryops.presentation.spareparts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.spareparts.SparePart
import com.candorfoods.factoryops.domain.model.spareparts.SparePartsMachine
import com.candorfoods.factoryops.domain.model.spareparts.WorkType
import com.candorfoods.factoryops.domain.repository.SparePartsRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

enum class SparePartsDetailPhase { Loading, Loaded, Error }

data class SparePartsDetailUiState(
    val phase: SparePartsDetailPhase = SparePartsDetailPhase.Loading,
    val machineName: String = "",
    val assetId: String? = null,
    val warehouse: String? = null,
    /** Low-stock parts first, then by name. */
    val parts: List<SparePart> = emptyList(),
    val error: String? = null,
    /** id of the part with a use/restock/delete request in flight, else null. */
    val busyPartId: Int? = null,
    val currentUser: User? = null,
) {
    /** Delete is the one role-gated action. Derived here rather than in the screen,
     * matching MachineListUiState.canAddMachine. The backend enforces it too. */
    val canDelete: Boolean
        get() = currentUser?.role == UserRole.SUPERVISOR || currentUser?.role == UserRole.ADMIN
}

sealed interface SparePartsDetailEvent {
    data class ActionSaved(val message: String) : SparePartsDetailEvent
    data class Error(val message: String) : SparePartsDetailEvent
}

/**
 * One machine's parts + quantities, with Use / Restock / History / Edit / Delete
 * per part. Re-fetches the whole list and filters by machine rather than accepting
 * the machine as a nav arg — the same "refetch and filter" approach as
 * UtilityFormViewModel's edit mode.
 */
@HiltViewModel
class SparePartsDetailViewModel @Inject constructor(
    private val repository: SparePartsRepository,
    private val getCurrentUser: GetCurrentUserUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val machineName: String = URLDecoder.decode(
        savedStateHandle.get<String>("machine").orEmpty(), "UTF-8",
    )

    private val _uiState = MutableStateFlow(SparePartsDetailUiState(machineName = machineName))
    val uiState: StateFlow<SparePartsDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SparePartsDetailEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SparePartsDetailEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            getCurrentUser().collect { user -> _uiState.update { it.copy(currentUser = user) } }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = SparePartsDetailPhase.Loading, error = null) }
            when (val r = repository.list()) {
                is Result.Success -> apply(r.data)
                is Result.Error -> _uiState.update {
                    it.copy(phase = SparePartsDetailPhase.Error, error = r.message)
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun apply(machines: List<SparePartsMachine>) {
        val machine = machines.firstOrNull { it.displayName == machineName }
        _uiState.update {
            it.copy(
                phase = SparePartsDetailPhase.Loaded,
                assetId = machine?.assetId,
                warehouse = machine?.warehouse,
                // Low stock first so the parts that need attention are never
                // scrolled past on a machine with many rows.
                parts = machine?.parts.orEmpty()
                    .sortedWith(compareByDescending<SparePart> { p -> p.lowStock }.thenBy { p -> p.partName }),
                busyPartId = null,
            )
        }
    }

    fun use(partId: Int, quantity: Int, workType: WorkType, note: String?) =
        runAction(partId, "Usage logged") { repository.use(partId, quantity, workType, note) }

    fun restock(partId: Int, quantity: Int, note: String?) =
        runAction(partId, "Stock updated") { repository.restock(partId, quantity, note) }

    fun delete(partId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyPartId = partId) }
            when (val r = repository.delete(partId)) {
                is Result.Success -> {
                    _events.tryEmit(SparePartsDetailEvent.ActionSaved("Spare part deleted"))
                    load()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(busyPartId = null) }
                    _events.tryEmit(SparePartsDetailEvent.Error(r.message))
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun runAction(partId: Int, success: String, block: suspend () -> Result<SparePart>) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyPartId = partId) }
            when (val r = block()) {
                is Result.Success -> {
                    _events.tryEmit(SparePartsDetailEvent.ActionSaved(success))
                    load()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(busyPartId = null) }
                    _events.tryEmit(SparePartsDetailEvent.Error(r.message))
                }
                Result.Loading -> Unit
            }
        }
    }
}
