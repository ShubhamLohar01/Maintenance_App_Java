package com.candorfoods.factoryops.presentation.technician

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Criticality
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.MachineRepository
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TechnicianHomeUiState(
    val currentUser: User? = null,
    val isLoading: Boolean = true,
    val openTickets: List<TicketRowUiModel> = emptyList(),
    val activeTickets: List<TicketRowUiModel> = emptyList(),
    val resolvedTickets: List<TicketRowUiModel> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TechnicianHomeViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val breakdownRepository: BreakdownRepository,
    private val machineRepository: MachineRepository,
    private val mtMachineRepository: MtMachineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TechnicianHomeUiState())
    val uiState: StateFlow<TechnicianHomeUiState> = _uiState.asStateFlow()

    /** asset_id → asset, from the register, so flags on assets resolve a real name. */
    private val assets = MutableStateFlow<Map<String, MtMachine>>(emptyMap())

    init {
        viewModelScope.launch {
            machineRepository.syncMachines()
            breakdownRepository.seedMockOpenFlagsIfEmpty()
            // Pull open breakdowns from the backend into Room so the list shows
            // flags raised on other devices and tap-to-detail resolves them.
            breakdownRepository.refreshOpenFlags("both")
            (mtMachineRepository.list() as? Result.Success)?.data?.let { list ->
                assets.value = list.associateBy { it.assetId }
            }
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

    private fun startObserving(user: User) {
        val machines: Flow<Map<String, Machine>> =
            machineRepository.observeAllMachines().map { list -> list.associateBy { it.id } }

        viewModelScope.launch {
            // Asset-safe: all OPEN flags (incl. asset-register ones), names from the register.
            combine(
                breakdownRepository.observeAllOpenFlags(),
                machines,
                assets
            ) { flags, machineMap, assetMap -> flags.toTicketRows(machineMap, assetMap) }
                .collect { rows ->
                    _uiState.update { it.copy(openTickets = rows, isLoading = false) }
                }
        }
        viewModelScope.launch {
            combine(
                breakdownRepository.observeAcknowledgedFlagsByUser(user.id, user.name),
                machines,
                assets
            ) { flags, machineMap, assetMap -> flags.toTicketRows(machineMap, assetMap) }
                .collect { rows -> _uiState.update { it.copy(activeTickets = rows) } }
        }
        viewModelScope.launch {
            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 3_600_000L
            combine(
                breakdownRepository.observeResolvedFlagsByPlant(user.plantId, thirtyDaysAgo),
                machines,
                assets
            ) { flags, machineMap, assetMap -> flags.toTicketRows(machineMap, assetMap) }
                .collect { rows -> _uiState.update { it.copy(resolvedTickets = rows) } }
        }
        // PM tasks now live on their own screen (TechnicianPmScreen), reached from the
        // home dashboard "PM Tasks" tile — no longer a tab here.
    }

    private fun List<BreakdownFlag>.toTicketRows(
        machineMap: Map<String, Machine>,
        assetMap: Map<String, MtMachine>,
    ): List<TicketRowUiModel> =
        map { flag ->
            // Resolve from the asset register first (new flows), then the legacy
            // machines table, then fall back to the raw id — never drop the row.
            val asset = assetMap[flag.machineId]
            val legacy = machineMap[flag.machineId]
            TicketRowUiModel(
                flag = flag,
                machineCode = legacy?.code ?: flag.machineId,
                machineName = asset?.assetName ?: legacy?.name ?: flag.machineId,
                criticality = legacy?.criticality ?: flag.severity.toCriticality(),
            )
        }

    private fun Severity.toCriticality(): Criticality = when (this) {
        Severity.CRITICAL -> Criticality.A
        Severity.MAJOR    -> Criticality.B
        Severity.MINOR    -> Criticality.C
    }
}
