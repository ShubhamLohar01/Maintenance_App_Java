package com.candorfoods.factoryops.presentation.it

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.ItAssetCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.matches
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetMachineMasterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItAssetListUiState(
    val category: ItAssetCategory = ItAssetCategory.ELECTRICAL,
    val isLoading: Boolean = true,
    val error: String? = null,
    val rows: List<MtMachine> = emptyList(),
    val searchQuery: String = "",
    val selectedBuilding: String? = null,   // null = both / all warehouses
    val selectedFloor: String? = null,      // null = all floors / sub-locations
    val showAddSheet: Boolean = false,
    val isAddingAsset: Boolean = false,
    val addError: String? = null,
) {
    /** Register rows of this category only — every derivation below runs off this. */
    private val inCategory: List<MtMachine> get() = rows.filter { category.matches(it) }

    /** Warehouse capsule options — buildings that actually hold assets of this category. */
    val buildings: List<String>
        get() = inCategory.map { it.building }.filter { it.isNotBlank() }.distinct().sorted()

    /** Floor dropdown options, scoped to the selected warehouse. */
    val floors: List<String>
        get() = inCategory
            .filter { selectedBuilding == null || it.building.equals(selectedBuilding, ignoreCase = true) }
            .map { it.subLocation }.filter { it.isNotBlank() }.distinct().sorted()

    /** Floors present per building — feeds the add sheet so typos don't mint phantom floors. */
    val subLocationsByBuilding: Map<String, List<String>>
        get() = rows
            .filter { it.building.isNotBlank() && it.subLocation.isNotBlank() }
            .groupBy({ it.building }, { it.subLocation })
            .mapValues { (_, floors) -> floors.distinct().sorted() }

    val items: List<MtMachine>
        get() = inCategory.asSequence()
            .filter { selectedBuilding == null || it.building.equals(selectedBuilding, ignoreCase = true) }
            .filter { selectedFloor == null || it.subLocation.equals(selectedFloor, ignoreCase = true) }
            .filter { matchesSearch(it) }
            .sortedWith(compareBy({ it.building.lowercase() }, { it.assetName.lowercase() }))
            .toList()

    private fun matchesSearch(m: MtMachine): Boolean {
        val q = searchQuery.trim()
        if (q.isBlank()) return true
        return m.assetName.contains(q, ignoreCase = true) ||
            m.assetId.contains(q, ignoreCase = true) ||
            m.subLocation.contains(q, ignoreCase = true)
    }
}

/**
 * One IT category's slice of the asset register, plus adding into it. The whole
 * register is fetched and filtered in-app (rather than GET /mt-machines?category=)
 * so a casing variant already in the data still shows, and so the add sheet can
 * suggest floors from every building.
 *
 * The category is pushed in by the screen (NavGraph parses the route argument),
 * not injected — that keeps SavedStateHandle out of the ViewModel and its tests.
 */
@HiltViewModel
class ItAssetListViewModel @Inject constructor(
    private val getMachineMaster: GetMachineMasterUseCase,
    private val mtMachineRepository: MtMachineRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItAssetListUiState())
    val uiState: StateFlow<ItAssetListUiState> = _uiState.asStateFlow()

    init { load() }

    /** Set the category this screen shows. Filters reset — they belong to a category. */
    fun onCategory(category: ItAssetCategory) = _uiState.update {
        if (it.category == category) it
        else it.copy(
            category = category,
            searchQuery = "",
            selectedBuilding = null,
            selectedFloor = null,
        )
    }

    fun onRetry() = load()

    fun onSearchChange(query: String) = _uiState.update { it.copy(searchQuery = query) }

    /** Switching warehouse resets the floor filter — floors are per-building. */
    fun onBuildingSelected(building: String?) =
        _uiState.update { it.copy(selectedBuilding = building, selectedFloor = null) }

    fun onFloorSelected(floor: String?) = _uiState.update { it.copy(selectedFloor = floor) }

    fun onAddClicked() = _uiState.update { it.copy(showAddSheet = true, addError = null) }

    fun onAddDismiss() = _uiState.update { it.copy(showAddSheet = false, addError = null) }

    /**
     * POST the new asset. The category is forced to this screen's category regardless
     * of what the sheet built, so an IT-added asset can never land elsewhere.
     */
    fun onSubmitNewAsset(asset: MtMachine) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingAsset = true, addError = null) }
            val payload = asset.copy(category = _uiState.value.category.backendValue)
            when (val r = mtMachineRepository.addAsset(payload)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isAddingAsset = false, showAddSheet = false) }
                    load()   // refresh so the new asset appears
                }
                is Result.Error -> _uiState.update {
                    it.copy(isAddingAsset = false, addError = r.message)
                }
                is Result.Loading -> _uiState.update { it.copy(isAddingAsset = false) }
            }
        }
    }

    private fun load() {
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
}
