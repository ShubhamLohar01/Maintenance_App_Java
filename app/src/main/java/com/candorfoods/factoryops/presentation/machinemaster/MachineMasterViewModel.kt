package com.candorfoods.factoryops.presentation.machinemaster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetMachineMasterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MachineMasterUiState(
    val isLoading: Boolean            = true,
    val error: String?                = null,
    val rows: List<MtMachine>         = emptyList(),
    val searchQuery: String           = "",
    val selectedBuilding: String?     = null,   // null = both / all warehouses
    val selectedFloor: String?        = null,   // null = all floors / sub-locations
    val selectedCategory: String?     = null,   // null = "All" categories
) {
    // Warehouse capsule options — distinct buildings present in the register, sorted.
    val buildings: List<String>
        get() = rows.map { it.building }.filter { it.isNotBlank() }.distinct().sorted()

    // Floor dropdown options — distinct sub-locations, scoped to the selected warehouse.
    val floors: List<String>
        get() = rows
            .filter { selectedBuilding == null || it.building.equals(selectedBuilding, ignoreCase = true) }
            .map { it.subLocation }.filter { it.isNotBlank() }.distinct().sorted()

    // Category tabs — distinct sub-categories present, sorted. Open-ended free text.
    val categories: List<String>
        get() = rows.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

    // Filtered + category-sorted list. Grouping into category headers is done in the UI
    // (only when no single category is selected) to mirror the operator machine list.
    val items: List<MtMachine>
        get() = rows.asSequence()
            .filter { selectedBuilding == null || it.building.equals(selectedBuilding, ignoreCase = true) }
            .filter { selectedFloor == null || it.subLocation.equals(selectedFloor, ignoreCase = true) }
            .filter { selectedCategory == null || it.category.equals(selectedCategory, ignoreCase = true) }
            .filter { matchesSearch(it) }
            .sortedWith(compareBy({ it.category.lowercase() }, { it.assetName.lowercase() }))
            .toList()

    private fun matchesSearch(m: MtMachine): Boolean {
        val q = searchQuery.trim()
        if (q.isBlank()) return true
        return m.assetName.contains(q, ignoreCase = true) ||
            m.assetId.contains(q, ignoreCase = true) ||
            m.subLocation.contains(q, ignoreCase = true)
    }
}

@HiltViewModel
class MachineMasterViewModel @Inject constructor(
    private val getMachineMaster: GetMachineMasterUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MachineMasterUiState())
    val uiState: StateFlow<MachineMasterUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = getMachineMaster()) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, rows = r.data, error = null)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = r.message)
                }
                is Result.Loading -> _uiState.update { it.copy(isLoading = true) }
            }
        }
    }

    fun onRetry() = load()

    fun onSearchChange(query: String) =
        _uiState.update { it.copy(searchQuery = query) }

    // Switching warehouse resets the floor filter (floors are per-building).
    fun onBuildingSelected(building: String?) =
        _uiState.update { it.copy(selectedBuilding = building, selectedFloor = null) }

    fun onFloorSelected(floor: String?) =
        _uiState.update { it.copy(selectedFloor = floor) }

    fun onCategorySelected(category: String?) =
        _uiState.update { it.copy(selectedCategory = category) }
}
