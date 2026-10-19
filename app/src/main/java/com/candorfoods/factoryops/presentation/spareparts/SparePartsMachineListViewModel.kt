package com.candorfoods.factoryops.presentation.spareparts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.spareparts.SparePart
import com.candorfoods.factoryops.domain.model.spareparts.SparePartsMachine
import com.candorfoods.factoryops.domain.repository.SparePartsRepository
import com.candorfoods.factoryops.util.excel.SparePartsSheet
import com.candorfoods.factoryops.util.excel.SpreadsheetExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One flat row: a part plus the machine it sits under. Used for the search results
 * on screen AND as the export row, so the spreadsheet can never disagree with what
 * the user is looking at.
 */
data class SparePartSearchResult(
    val part: SparePart,
    val machineName: String,
    val warehouse: String?,
    val assetId: String? = null,
)

data class SparePartsMachineListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val search: String = "",
    val warehouse: String? = null,        // null = All
    val lowStockOnly: Boolean = false,
    /** Machine cards, after warehouse + low-stock filtering. Empty while searching. */
    val visibleMachines: List<SparePartsMachine> = emptyList(),
    /** Flat part hits. Empty unless [search] is non-blank. */
    val partResults: List<SparePartSearchResult> = emptyList(),
    /**
     * Every part currently passing the filters, flat — what Download writes.
     * Populated in BOTH display modes (machine cards and search results), so the
     * export always matches the screen without re-deriving the filters.
     */
    val exportRows: List<SparePartSearchResult> = emptyList(),
    val lowStockCount: Int = 0,
    val warehouses: List<String> = emptyList(),
    val isDownloading: Boolean = false,
    val downloadMessage: String? = null,
)

/**
 * Landing screen for the "Spare Parts" tile.
 *
 * Two display modes off one fetch: with an empty search box the body is machine
 * cards; with text in it the body flips to flat part results, so "belt" finds
 * every belt across every machine rather than making the user open each card.
 */
@HiltViewModel
class SparePartsMachineListViewModel @Inject constructor(
    private val repository: SparePartsRepository,
    private val exporter: SpreadsheetExporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SparePartsMachineListUiState())
    val uiState: StateFlow<SparePartsMachineListUiState> = _uiState.asStateFlow()

    private var allMachines: List<SparePartsMachine> = emptyList()

    init {
        load()
    }

    /** Also re-callable to refresh — e.g. after adding a part, or on returning
     * from the detail screen. */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = repository.list()) {
                is Result.Success -> {
                    allMachines = r.data
                    _uiState.update { it.copy(isLoading = false) }
                    recompute()
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun onSearchChange(value: String) {
        _uiState.update { it.copy(search = value) }
        recompute()
    }

    fun onWarehouseChange(value: String?) {
        _uiState.update { it.copy(warehouse = value) }
        recompute()
    }

    fun onLowStockOnlyChange(value: Boolean) {
        _uiState.update { it.copy(lowStockOnly = value) }
        recompute()
    }

    private fun recompute() {
        val s = _uiState.value
        val byWarehouse = allMachines.filter { s.warehouse == null || it.warehouse == s.warehouse }
        val query = s.search.trim()

        val machines: List<SparePartsMachine>
        val results: List<SparePartSearchResult>
        val exportRows: List<SparePartSearchResult>
        if (query.isEmpty()) {
            machines = if (s.lowStockOnly) byWarehouse.filter { it.lowStockCount > 0 } else byWarehouse
            results = emptyList()
            // Same filter the cards show, flattened one part per row for the export.
            exportRows = machines.flatMap { machine ->
                machine.parts
                    .filter { !s.lowStockOnly || it.lowStock }
                    .map { machine.toRow(it) }
            }
        } else {
            // A machine-name hit surfaces all of its parts; a part-name hit surfaces
            // just that part. Either way the user gets rows they can act on.
            machines = emptyList()
            results = byWarehouse.flatMap { machine ->
                val machineHit = machine.displayName.contains(query, ignoreCase = true) ||
                    machine.machineName.contains(query, ignoreCase = true)
                machine.parts
                    .filter { machineHit || it.partName.contains(query, ignoreCase = true) }
                    .filter { !s.lowStockOnly || it.lowStock }
                    .map { machine.toRow(it) }
            }
            // In search mode the results ARE the flat rows — no second derivation.
            exportRows = results
        }

        _uiState.update {
            it.copy(
                visibleMachines = machines,
                partResults = results,
                exportRows = exportRows,
                lowStockCount = byWarehouse.sumOf { m -> m.lowStockCount },
                warehouses = allMachines.mapNotNull { m -> m.warehouse }.distinct().sorted(),
            )
        }
    }

    private fun SparePartsMachine.toRow(part: SparePart) =
        SparePartSearchResult(part, displayName, warehouse, assetId)

    /** Write the rows currently on screen to an .xlsx in the device's Downloads. */
    fun onDownload() {
        if (_uiState.value.isDownloading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true) }
            val sheet = SparePartsSheet.stock(_uiState.value.exportRows)
            val message = exporter.export(sheet, "Spare parts")
            _uiState.update { it.copy(isDownloading = false, downloadMessage = message) }
        }
    }

    fun onDownloadMessageShown() = _uiState.update { it.copy(downloadMessage = null) }
}
