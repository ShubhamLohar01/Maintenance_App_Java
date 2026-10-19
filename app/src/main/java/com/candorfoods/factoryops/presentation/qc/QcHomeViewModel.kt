package com.candorfoods.factoryops.presentation.qc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Criticality
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.MachineRepository
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.util.DateTimeFormatter
import com.candorfoods.factoryops.util.canonicalPlant
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
    val doneToday: List<QcInboxRow> = emptyList(),
    /** Every item this QC user has ever decided (all-time, newest first). */
    val history: List<QcInboxRow> = emptyList(),
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
            // into Room so the QC inbox is populated cross-device. "both" asks the
            // server not to narrow; it still clamps a plant-bound account to its own
            // plant, so a QC user never caches the other plant's flags.
            breakdownRepository.refreshOpenFlags("both")
            // Same for PM work orders awaiting QC (submitted + approved on other devices).
            runCatching { pmRepository.refreshByStatuses(listOf(PmWoStatus.PENDING_QC)) }
            loadAssets()
            loadHistory()   // network archive — after assets so machine names resolve
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
                        flags.forEach { f ->
                            if (inPlant(f.machineId, user, machineMap, assetMap)) {
                                add(breakdownRow(f, machineMap, assetMap))
                            }
                        }
                        wos.forEach { w ->
                            if (inPlant(w.machineId, user, machineMap, assetMap)) {
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

    /**
     * History = every QC-decided record this account may see, all-time — a NETWORK fetch,
     * not the local "Done today" Room slice. The phone only records a QC decision locally
     * when it makes one, so anything decided on the server / another device (e.g. by
     * another QC user) only appears via these endpoints:
     *   • breakdowns → GET /breakdowns/qc/history (all decided in the range)
     *   • PM         → GET /work-orders?status=CLOSED (QC-approved work orders)
     *
     * `plant_id = "both"` means "do not narrow", NOT "give me every plant": the server
     * resolves it against the caller's own role and location, so a HEAD/SUPERVISOR gets
     * both plants and a plant-bound QC account gets only its own. No client-side filter
     * is applied here on purpose — unlike the live tabs, these rows come straight off the
     * (already scoped) network and never sit in Room, so there is no stale cross-plant
     * cache for a guard to catch.
     */
    private fun loadHistory() {
        viewModelScope.launch {
            val to = java.time.LocalDate.now().toString()
            val bd = (breakdownRepository.qcActivityHistory(HISTORY_FROM, to, "both") as? Result.Success)?.data
                ?: emptyList()
            val pm = (pmRepository.qcClosedHistory() as? Result.Success)?.data ?: emptyList()
            val assetMap = assets.value
            val rows = buildList {
                bd.forEach { f -> add(breakdownRow(f, emptyMap(), assetMap)) }
                pm.forEach { w -> add(pmRow(w, emptyMap(), assetMap)) }
            }.sortedByDescending { it.sortMillis }
            _uiState.update { it.copy(history = rows) }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            machineRepository.syncMachines()
            breakdownRepository.refreshOpenFlags("both")
            runCatching { pmRepository.refreshByStatuses(listOf(PmWoStatus.PENDING_QC)) }
            loadAssets()
            loadHistory()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private companion object {
        /** Wide lower bound so the QC "History" tab is effectively all-time. */
        const val HISTORY_FROM = "2000-01-01"
    }

    /**
     * Is this asset inside the QC user's plant?
     *
     * A HEAD or SUPERVISOR is cross-plant and sees everything; every other role —
     * including the QC roles — is bound to one plant. The asset register
     * ([MtMachine.building]) is the authority, because that is what the backend
     * scopes by; the legacy `machines` table is only a fallback for rows that
     * predate the register.
     *
     * This is a SECOND line of defence, not the primary one. The server already
     * scopes /breakdowns and /work-orders to the caller's plant; this guard exists
     * for rows Room cached before that scoping landed, or from a session under a
     * different account. Which is why it fails OPEN in the two cases where it
     * cannot judge:
     *
     *  - the register hasn't loaded (or its fetch failed — loadAssets() swallows
     *    errors and leaves the map empty). Filtering on an empty map would blank
     *    the entire inbox, which is far worse than briefly trusting the server.
     *  - the asset is in neither map. That means a register gap to fix, and the
     *    row is a real item somebody must action.
     *
     * A row we CAN place, in the wrong plant, is dropped — that is the leak this
     * closes. The old code had it backwards: it kept every unresolvable row
     * ("include rather than drop"), and since asset-based work orders are now the
     * normal case, that exception had quietly swallowed the rule.
     */
    private fun inPlant(
        machineId: String,
        user: User,
        machineMap: Map<String, Machine>,
        assetMap: Map<String, MtMachine>,
    ): Boolean {
        if (user.role == UserRole.HEAD || user.role == UserRole.SUPERVISOR) return true
        if (assetMap.isEmpty() && machineMap.isEmpty()) return true   // nothing to judge by
        val mine = canonicalPlant(user.plantId)
        if (mine.isBlank()) return true                               // no plant on the account
        assetMap[machineId]?.let { return canonicalPlant(it.building) == mine }
        machineMap[machineId]?.let { return canonicalPlant(it.plantId) == mine }
        return true                                                   // unknown asset — keep it
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
            // History flags come from the server with their own assetName — fall back to it
            // when the asset isn't in the locally-loaded register map.
            machineName = asset?.assetName ?: legacy?.name ?: flag.assetName ?: flag.machineId,
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
