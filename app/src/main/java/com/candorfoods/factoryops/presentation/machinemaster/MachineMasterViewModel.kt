package com.candorfoods.factoryops.presentation.machinemaster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.MachineCategory
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
    val isLoading: Boolean                 = true,
    val error: String?                     = null,
    val rows: List<MtMachine>              = emptyList(),
    val selectedBuilding: String?          = null,
    val selectedLocation: String?          = null,
    val selectedSubCategory: String?       = null,
    val expandedProduction: Boolean        = true,
    val expandedOthers: Boolean            = true,
) {
    // Derived dropdown sources — distinct values present in the response, sorted.
    // Per spec: derive from data (not a fixed enum) so the dropdowns only show
    // values the user can actually pick. With the asset register this matters —
    // category is open-ended free text.
    val buildings: List<String>
        get() = rows.map { it.building }.filter { it.isNotBlank() }.distinct().sorted()

    val locations: List<String>
        get() = rows.map { it.subLocation }.filter { it.isNotBlank() }.distinct().sorted()

    val subCategories: List<String>
        get() = rows.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

    private val filtered: List<MtMachine>
        get() = rows.filter { row ->
            (selectedBuilding == null || row.building.equals(selectedBuilding, ignoreCase = true)) &&
                (selectedLocation == null || row.subLocation.equals(selectedLocation, ignoreCase = true)) &&
                (selectedSubCategory == null || row.category.equals(selectedSubCategory, ignoreCase = true))
        }

    // Both sections always render (all assets shown). Filtered-empty sections
    // still render (screen shows "No matches" under the header).
    val sections: List<Pair<MachineCategory, List<MtMachine>>>
        get() = listOf(MachineCategory.PRODUCTION, MachineCategory.OTHERS)
            .map { cat -> cat to filtered.filter { it.section == cat } }

    fun isExpanded(category: MachineCategory): Boolean = when (category) {
        MachineCategory.PRODUCTION -> expandedProduction
        MachineCategory.OTHERS     -> expandedOthers
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

    fun onBuildingSelected(building: String?) =
        _uiState.update { it.copy(selectedBuilding = building) }

    fun onLocationSelected(location: String?) =
        _uiState.update { it.copy(selectedLocation = location) }

    fun onSubCategorySelected(subCategory: String?) =
        _uiState.update { it.copy(selectedSubCategory = subCategory) }

    fun onToggleSection(category: MachineCategory) =
        _uiState.update {
            when (category) {
                MachineCategory.PRODUCTION -> it.copy(expandedProduction = !it.expandedProduction)
                MachineCategory.OTHERS     -> it.copy(expandedOthers     = !it.expandedOthers)
            }
        }
}
