package com.candorfoods.factoryops.presentation.qc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Criticality
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.MachineRepository
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.util.DateTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QcHomeUiState(
    val currentUser: User? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val awaiting: List<QcInboxRow> = emptyList(),
    val myActive: List<QcInboxRow> = emptyList(),
    val doneToday: List<QcInboxRow> = emptyList()
)

@HiltViewModel
class QcHomeViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val breakdownRepository: BreakdownRepository,
    private val pmRepository: PmWorkOrderRepository,
    private val machineRepository: MachineRepository,
    private val mtMachineRepository: MtMachineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QcHomeUiState())
    val uiState: StateFlow<QcHomeUiState> = _uiState.asStateFlow()

    /** asset_id → asset, from the register, so flags on assets resolve a real name
     * (breakdowns key on asset_id, which is NOT in the legacy `machines` table). */
    private val assets = MutableStateFlow<Map<String, MtMachine>>(emptyMap())

    private var observerJob: Job? = null

    init {
        viewModelScope.launch {
            machineRepository.syncMachines()
            // Pull breakdowns (incl. PENDING_QC raised/resolved on other devices)
            // into Room so the QC inbox is populated cross-device.
            breakdownRepository.refreshOpenFlags("both")
            loadAssets()
        }
        viewModelScope.launch {
            getCurrentUser()
                .filterNotNull()
                .collect { user ->
                    _uiState.update { it.copy(currentUser = user) }
                    startObserving(user)
                }
        }
    }

    private suspend fun loadAssets() {
        (mtMachineRepository.list() as? Result.Success)?.data?.let { list ->
            assets.value = list.associateBy { it.assetId }
        }
    }

    private fun startObserving(user: User) {
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            val machines: Flow<Map<String, Machine>> =
                machineRepository.observeAllMachines().map { list -> list.associateBy { it.id } }

            // Awaiting QC = unassigned PENDING_QC items in the user's plant
            launch {
                combine(
                    breakdownRepository.observePendingQcByPlant(user.plantId),
                    pmRepository.observePendingQc(),
                    machines,
                    assets
                ) { flags, wos, machineMap, assetMap ->
                    buildList {
                        flags.forEach { f -> add(breakdownRow(f, machineMap, assetMap)) }
                        wos.forEach { w ->
                            // Asset-based WOs have no legacy entry to plant-scope by;
                            // include them rather than drop them from QC.
                            val legacy = machineMap[w.machineId]
                            if (legacy == null || legacy.plantId == user.plantId) {
                                add(pmRow(w, machineMap, assetMap))
                            }
                        }
                    }.sortedByDescending { it.sortMillis }
                }.collect { rows ->
                    _uiState.update { it.copy(awaiting = rows, isLoading = false) }
                }
            }

            // My Active = items this user has picked up
            launch {
                combine(
                    breakdownRepository.observeQcAcknowledgedByUser(user.id),
                    pmRepository.observeQcAcknowledgedByUser(user.id),
                    machines,
                    assets
                ) { flags, wos, machineMap, assetMap ->
                    buildList {
                        flags.forEach { f -> add(breakdownRow(f, machineMap, assetMap)) }
                        wos.forEach { w -> add(pmRow(w, machineMap, assetMap)) }
                    }.sortedByDescending { it.sortMillis }
                }.collect { rows ->
                    _uiState.update { it.copy(myActive = rows) }
                }
            }

            // Done today = items decided today by this user
            launch {
                val todayStart = DateTimeFormatter.startOfDayMillis(System.currentTimeMillis())
                combine(
                    breakdownRepository.observeQcDoneByUserSince(user.id, todayStart),
                    pmRepository.observeQcDoneByUserSince(user.id, todayStart),
                    machines,
                    assets
                ) { flags, wos, machineMap, assetMap ->
                    buildList {
                        flags.forEach { f -> add(breakdownRow(f, machineMap, assetMap)) }
                        wos.forEach { w -> add(pmRow(w, machineMap, assetMap)) }
                    }.sortedByDescending { it.sortMillis }
                }.collect { rows ->
                    _uiState.update { it.copy(doneToday = rows) }
                }
            }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            machineRepository.syncMachines()
            breakdownRepository.refreshOpenFlags("both")
            loadAssets()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /** Resolve a breakdown row, asset register first then legacy machines, never
     * dropping the row (asset-register flags have no legacy `machines` entry). */
    private fun breakdownRow(
        flag: BreakdownFlag,
        machineMap: Map<String, Machine>,
        assetMap: Map<String, MtMachine>,
    ): QcInboxRow.Breakdown {
        val asset = assetMap[flag.machineId]
        val legacy = machineMap[flag.machineId]
        return QcInboxRow.Breakdown(
            flag = flag,
            machineCode = legacy?.code ?: flag.machineId,
            machineName = asset?.assetName ?: legacy?.name ?: flag.machineId,
            machineLocation = asset?.subLocation ?: legacy?.location ?: "",
            criticality = legacy?.criticality ?: flag.severity.toCriticality(),
        )
    }

    private fun Severity.toCriticality(): Criticality = when (this) {
        Severity.CRITICAL -> Criticality.A
        Severity.MAJOR    -> Criticality.B
        Severity.MINOR    -> Criticality.C
    }

    /** Resolve a PM row, asset register first then legacy machines, then the WO's
     *  snapshot name — never dropping the row (machineId is an asset_id). */
    private fun pmRow(
        wo: PmWorkOrder,
        machineMap: Map<String, Machine>,
        assetMap: Map<String, MtMachine>,
    ): QcInboxRow.Pm {
        val asset = assetMap[wo.machineId]
        val legacy = machineMap[wo.machineId]
        return QcInboxRow.Pm(
            wo = wo,
            machineCode = asset?.assetId ?: legacy?.code ?: wo.machineId,
            machineName = asset?.assetName ?: legacy?.name ?: wo.templateName,
            machineLocation = asset?.subLocation ?: legacy?.location ?: "",
            criticality = legacy?.criticality ?: Criticality.C
        )
    }
}
