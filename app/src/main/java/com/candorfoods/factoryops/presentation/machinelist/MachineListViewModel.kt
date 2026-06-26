package com.candorfoods.factoryops.presentation.machinelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.ActiveRun
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.domain.usecase.energy.GetActiveRunsUseCase
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetMachineMasterUseCase
import com.candorfoods.factoryops.domain.usecase.sync.GetPendingSyncCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Live status of a machine. `order` drives the grouped "All" ordering. */
enum class MachineLiveStatus(val label: String, val order: Int) {
    RUNNING("Running", 0),
    BREAKDOWN("Breakdown", 1),
    UNDER_QC("Under QC", 2),
    IDLE("Idle", 3),
}

/** Status filter chosen from the 3-dot menu. */
enum class MachineStatusFilter(val label: String) {
    ALL("All"), RUNNING("Running"), BREAKDOWN("Breakdown"), UNDER_QC("Under QC"), IDLE("Idle");

    fun matches(status: MachineLiveStatus): Boolean =
        this == ALL || name == status.name
}

data class MachineListItem(
    val asset: MtMachine,
    val status: MachineLiveStatus,
    val operatorName: String?,   // running only
    val startedAtMs: Long?,      // running only
)

data class MachineListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUser: User? = null,
    val assets: List<MtMachine> = emptyList(),
    val activeRuns: List<ActiveRun> = emptyList(),
    val underBreakdownIds: Set<String> = emptySet(),
    val underQcIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val statusFilter: MachineStatusFilter = MachineStatusFilter.ALL,
    val pendingSyncCount: Int = 0,
    val isSyncing: Boolean = false,
) {
    val isOperator: Boolean get() = currentUser?.role == UserRole.OPERATOR

    private val runByAsset: Map<String, ActiveRun> get() = activeRuns.associateBy { it.assetId }

    /** Priority: a PENDING_QC machine is UNDER_QC (not BREAKDOWN). */
    private fun statusOf(assetId: String): MachineLiveStatus = when {
        runByAsset.containsKey(assetId) -> MachineLiveStatus.RUNNING
        assetId in underQcIds           -> MachineLiveStatus.UNDER_QC
        assetId in underBreakdownIds    -> MachineLiveStatus.BREAKDOWN
        else                            -> MachineLiveStatus.IDLE
    }

    val categories: List<String>
        get() = assets.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

    /** Filtered (search + category + status) and ordered (status group, then name). */
    val items: List<MachineListItem>
        get() {
            val runs = runByAsset
            val q = searchQuery.trim()
            return assets
                .asSequence()
                .map { asset ->
                    val run = runs[asset.assetId]
                    MachineListItem(asset, statusOf(asset.assetId), run?.operatorName, run?.startedAtEpochMs)
                }
                .filter { item ->
                    q.isEmpty() ||
                        item.asset.assetName.contains(q, true) ||
                        item.asset.assetId.contains(q, true) ||
                        item.asset.subLocation.contains(q, true)
                }
                .filter { selectedCategory == null || it.asset.category.equals(selectedCategory, true) }
                .filter { statusFilter.matches(it.status) }
                .sortedWith(compareBy({ it.status.order }, { it.asset.assetName.lowercase() }))
                .toList()
        }

    fun countOf(status: MachineLiveStatus): Int =
        assets.count { statusOf(it.assetId) == status }
}

@HiltViewModel
class MachineListViewModel @Inject constructor(
    private val getMachineMaster: GetMachineMasterUseCase,
    private val getActiveRuns: GetActiveRunsUseCase,
    private val getCurrentUser: GetCurrentUserUseCase,
    private val getPendingSyncCount: GetPendingSyncCountUseCase,
    private val breakdownRepository: BreakdownRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MachineListUiState())
    val uiState: StateFlow<MachineListUiState> = _uiState.asStateFlow()

    init {
        loadAssets(initial = true)
        viewModelScope.launch {
            getCurrentUser().collect { user -> _uiState.update { it.copy(currentUser = user) } }
        }
        viewModelScope.launch {
            getPendingSyncCount().collect { c -> _uiState.update { it.copy(pendingSyncCount = c) } }
        }
        viewModelScope.launch {
            breakdownRepository.observeMachinesUnderBreakdown().collect { ids ->
                _uiState.update { it.copy(underBreakdownIds = ids) }
            }
        }
        viewModelScope.launch {
            breakdownRepository.observeMachinesUnderQc().collect { ids ->
                _uiState.update { it.copy(underQcIds = ids) }
            }
        }
        viewModelScope.launch {
            while (true) {
                loadActiveRuns()
                delay(ACTIVE_RUNS_POLL_MILLIS)
            }
        }
    }

    fun onSearchChange(q: String)               = _uiState.update { it.copy(searchQuery = q) }
    fun onCategorySelected(c: String?)          = _uiState.update { it.copy(selectedCategory = c) }
    fun onStatusFilterChange(f: MachineStatusFilter) = _uiState.update { it.copy(statusFilter = f) }

    fun onRefresh() {
        loadAssets(initial = false)
        viewModelScope.launch { loadActiveRuns() }
    }

    private fun loadAssets(initial: Boolean) {
        viewModelScope.launch {
            _uiState.update { if (initial) it.copy(isLoading = true) else it.copy(isSyncing = true) }
            when (val r = getMachineMaster()) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, isSyncing = false, assets = r.data, error = null)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, isSyncing = false, error = r.message)
                }
                is Result.Loading -> Unit
            }
        }
    }

    private suspend fun loadActiveRuns() {
        when (val r = getActiveRuns()) {
            is Result.Success -> _uiState.update { it.copy(activeRuns = r.data) }
            else -> Unit
        }
    }

    private companion object {
        const val ACTIVE_RUNS_POLL_MILLIS = 30_000L
    }
}
