package com.candorfoods.factoryops.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.data.local.OthersTagPreferences
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.repository.MachineRepository
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.domain.usecase.machine.GetAssignedMachinesUseCase
import com.candorfoods.factoryops.util.OtherMachineKeywords
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row in the admin's machine list. `isTagged` flips the toggle button. */
data class AdminMachineRow(
    val machine: Machine,
    val isTagged: Boolean,
)

data class AdminHomeUiState(
    val currentUser: User?            = null,
    val rows: List<AdminMachineRow>   = emptyList(),
    val taggedCount: Int              = 0,
    val isLoading: Boolean            = true,
    val error: String?                = null,
    val searchQuery: String           = "",
    val showAddSheet: Boolean         = false,
    val isSaving: Boolean             = false,
    val saveError: String?            = null,
    val lastAddedName: String?        = null,
    val lastToggleMessage: String?    = null,
) {
    val filteredRows: List<AdminMachineRow>
        get() {
            val q = searchQuery.trim()
            if (q.isEmpty()) return rows
            return rows.filter {
                it.machine.name.contains(q, ignoreCase = true) ||
                    it.machine.code.contains(q, ignoreCase = true) ||
                    it.machine.location.contains(q, ignoreCase = true)
            }
        }
}

@HiltViewModel
class AdminHomeViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val getAssignedMachines: GetAssignedMachinesUseCase,
    private val machineRepository: MachineRepository,
    private val mtMachineRepository: MtMachineRepository,
    private val othersTagPreferences: OthersTagPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminHomeUiState())
    val uiState: StateFlow<AdminHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getCurrentUser().collect { user -> _uiState.update { it.copy(currentUser = user) } }
        }

        // Reactive: re-emit rows whenever the machine list OR the tag set changes.
        viewModelScope.launch {
            combine(getAssignedMachines(), othersTagPreferences.tags) { result, tags ->
                Pair(result, tags)
            }.collect { (result, tags) ->
                when (result) {
                    is Result.Loading -> _uiState.update { it.copy(isLoading = true) }
                    is Result.Error   -> _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                    is Result.Success -> {
                        seedTagsOnFirstLaunchIfNeeded(result.data)
                        val rows = result.data.map { m ->
                            AdminMachineRow(machine = m, isTagged = tags.contains(m.id))
                        }
                        _uiState.update {
                            it.copy(
                                isLoading    = false,
                                rows         = rows,
                                taggedCount  = rows.count { r -> r.isTagged },
                                error        = null,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * First time admin opens this screen, pre-tag every machine whose name matches
     * the keyword list (AC, fan, light, freezer, exhaust, etc.). After that the
     * admin's manual edits are authoritative — we never re-seed.
     */
    private fun seedTagsOnFirstLaunchIfNeeded(machines: List<Machine>) {
        if (!othersTagPreferences.hasNeverBeenSeeded()) return
        val auto = machines
            .filter { OtherMachineKeywords.matches(it.name) }
            .map { it.id }
            .toSet()
        if (auto.isNotEmpty()) othersTagPreferences.replaceAll(auto)
        othersTagPreferences.markSeeded()
    }

    fun onSearchChange(query: String) =
        _uiState.update { it.copy(searchQuery = query) }

    fun onToggleTag(row: AdminMachineRow) {
        if (row.isTagged) {
            othersTagPreferences.untag(row.machine.id)
            _uiState.update { it.copy(lastToggleMessage = "Restored \"${row.machine.name}\" to operator list") }
        } else {
            othersTagPreferences.tag(row.machine.id)
            _uiState.update { it.copy(lastToggleMessage = "Moved \"${row.machine.name}\" to Others") }
        }
    }

    fun onToggleMessageShown() =
        _uiState.update { it.copy(lastToggleMessage = null) }

    fun onRefresh() {
        viewModelScope.launch { machineRepository.syncMachines() }
    }

    // Secondary feature: keep the "+ Add machine" sheet for future use. Saves
    // into the mock OthersCatalogSeed (mock-only). When the backend gains a
    // POST endpoint, swap mtMachineRepository.addOther for a real call.
    fun onOpenAddSheet()  = _uiState.update { it.copy(showAddSheet = true, saveError = null) }
    fun onCloseAddSheet() = _uiState.update { it.copy(showAddSheet = false, saveError = null) }

    fun onAdd(name: String, building: String, subLocation: String, subCategory: String, powerLoad: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            when (val r = mtMachineRepository.addOther(name, building, subLocation, subCategory, powerLoad)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        isSaving      = false,
                        showAddSheet  = false,
                        lastAddedName = r.data.assetName,
                    )
                }
                is Result.Error -> _uiState.update {
                    it.copy(isSaving = false, saveError = r.message)
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun onSnackbarShown() = _uiState.update { it.copy(lastAddedName = null) }
}
