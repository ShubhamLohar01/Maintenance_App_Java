package com.candorfoods.factoryops.presentation.technician

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Criticality
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.AssignedPmPlan
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.repository.MachineRepository
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.repository.PmPlanRepository
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.util.DateTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the technician's dedicated PM screen (reached from the home dashboard tile),
 * which presents its work as three tabs:
 *  - Today   — assigned plans due today or overdue, most-overdue first. The daily job.
 *  - All     — every assigned plan, plus the active work orders not yet due today.
 *  - History — everything the technician has finished, all time, grouped by month.
 *
 * Plans (mt_pm_plan) and work orders are combined in one place so a Today row can carry
 * the work order it should open, and so every tab count comes from one snapshot.
 */
data class TechnicianPmUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val plans: List<PmPlanRowUiModel> = emptyList(),
    /** Today's actionable work: work orders due today or overdue, still the technician's. */
    val today: List<PmTaskRowUiModel> = emptyList(),
    /** Safety net — plans past due that never produced a work order, so they can't vanish. */
    val todayUngenerated: List<PmPlanRowUiModel> = emptyList(),
    val plansError: String? = null,
    val active: List<PmTaskRowUiModel> = emptyList(),
    val history: List<PmHistoryGroupUiModel> = emptyList(),
) {
    val todayCount: Int get() = today.size + todayUngenerated.size

    val historyCount: Int get() = history.sumOf { it.rows.size }
}

/** Finished work orders sharing a calendar month, e.g. "August 2026". */
data class PmHistoryGroupUiModel(
    val monthLabel: String,
    val rows: List<PmTaskRowUiModel>,
)

@HiltViewModel
class TechnicianPmViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val machineRepository: MachineRepository,
    private val mtMachineRepository: MtMachineRepository,
    private val pmWoRepository: PmWorkOrderRepository,
    private val pmPlanRepository: PmPlanRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TechnicianPmUiState())
    val uiState: StateFlow<TechnicianPmUiState> = _uiState.asStateFlow()

    /** asset_id → asset, from the register, so a WO/plan resolves a real machine name. */
    private val assets = MutableStateFlow<Map<String, MtMachine>>(emptyMap())

    /** Assigned plans fetched from the backend; combined with [assets] into display rows. */
    private val rawPlans = MutableStateFlow<List<AssignedPmPlan>>(emptyList())

    /** Signed-in user, kept so pull-to-refresh can re-run the backend pulls. */
    private var currentUser: User? = null

    init {
        viewModelScope.launch {
            (mtMachineRepository.list() as? Result.Success)?.data?.let { list ->
                assets.value = list.associateBy { it.assetId }
            }
        }
        viewModelScope.launch {
            getCurrentUser().filterNotNull().collect { user -> observe(user) }
        }
    }

    /** Pull-to-refresh: re-run both backend pulls (plans + this tech's work orders). */
    fun refresh() {
        val user = currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            refreshFromBackend(user)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /** Best-effort backend pulls; the observe flows then reflect Room offline-first. */
    private suspend fun refreshFromBackend(user: User) {
        runCatching { pmWoRepository.refreshForTechnician(user.id) }
        // Assigned PM plans (mt_pm_plan). Surface a failure so a missing/erroring
        // GET /pm/plans is visible instead of looking like "no PMs assigned".
        when (val r = pmPlanRepository.getAssignedPlans(user.id)) {
            is Result.Success -> { rawPlans.value = r.data; _uiState.update { it.copy(plansError = null) } }
            is Result.Error   -> _uiState.update { it.copy(plansError = r.message) }
            Result.Loading    -> Unit
        }
    }

    private fun observe(user: User) {
        currentUser = user
        viewModelScope.launch { refreshFromBackend(user) }

        val machines: Flow<Map<String, Machine>> =
            machineRepository.observeAllMachines().map { list -> list.associateBy { it.id } }

        viewModelScope.launch {
            combine(
                rawPlans,
                assets,
                machines,
                pmWoRepository.observeForTechnician(user.id, ALL_STATUSES),
            ) { plans, assetMap, machineMap, wos ->
                val woRows = wos.map { it.toRow(machineMap, assetMap) }
                val latestByPlan = latestWorkOrderByPlan(wos)
                val planRows = plans
                    .map { it.toPlanRow(assetMap, latestByPlan[it.plan.id]) }
                    .sortedBy { it.nextDueAt }
                Triple(planRows, woRows, wos)
            }.collect { (planRows, woRows, wos) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        plans = planRows,
                        today = todayWorkOrders(woRows),
                        todayUngenerated = ungeneratedDuePlans(planRows, wos),
                        active = woRows.filter { r -> r.wo.status in ACTIVE }
                            .sortedBy { r -> r.wo.scheduledDate },
                        history = historyGroups(woRows),
                    )
                }
            }
        }
    }

    private fun AssignedPmPlan.toPlanRow(
        assetMap: Map<String, MtMachine>,
        openWo: PmWorkOrder?,
    ): PmPlanRowUiModel {
        val asset = assetMap[plan.machineId]
        return PmPlanRowUiModel(
            planId = plan.id,
            machineName = asset?.assetName ?: machineName.ifBlank { plan.machineId },
            machineCode = asset?.assetId ?: plan.machineId,
            nextDueAt = plan.nextDueAt,
            triggerType = plan.triggerType,
            triggerInterval = plan.triggerInterval,
            description = description,
            items = items,
            woId = openWo?.id,
            woStatus = openWo?.status,
        )
    }

    private fun PmWorkOrder.toRow(
        machineMap: Map<String, Machine>,
        assetMap: Map<String, MtMachine>,
    ): PmTaskRowUiModel {
        // machineId is an asset_id: resolve from the register first, then the legacy
        // table, then the WO's snapshot name — never drop the task.
        val asset = assetMap[machineId]
        val legacy = machineMap[machineId]
        return PmTaskRowUiModel(
            wo = this,
            machineName = asset?.assetName ?: legacy?.name ?: templateName,
            machineCode = asset?.assetId ?: legacy?.code ?: machineId,
            machineLocation = asset?.subLocation ?: legacy?.location ?: "",
            criticality = legacy?.criticality ?: Criticality.C,
        )
    }

    companion object {
        private val ACTIVE = setOf(
            PmWoStatus.NOTIFIED, PmWoStatus.ACKNOWLEDGED, PmWoStatus.IN_PROGRESS,
        )
        private val IN_REVIEW = setOf(
            PmWoStatus.SUBMITTED, PmWoStatus.SUPERVISOR_APPROVED, PmWoStatus.PENDING_QC,
        )
        private val COMPLETED = setOf(
            PmWoStatus.QC_APPROVED, PmWoStatus.CLOSED,
        )

        /** Work the technician has finished doing — whether or not review has caught up. */
        private val HISTORY = IN_REVIEW + COMPLETED

        private val ALL_STATUSES = (ACTIVE + HISTORY).toList()

        /**
         * plan_id → its most recent work order, whatever the status. A plan repeats, so
         * the newest cycle is the one that describes where it stands; restricting this to
         * active statuses made an already-submitted task read "Not generated yet".
         */
        fun latestWorkOrderByPlan(wos: List<PmWorkOrder>): Map<String, PmWorkOrder> =
            wos.groupBy { it.planId }
                .mapValues { (_, list) -> list.maxBy { it.scheduledDate } }

        /**
         * Today's job: work orders due today or overdue that are still the technician's to
         * do, most-overdue first. Keyed on the work order's own scheduled date, not the
         * plan's — a plan's next due date only rolls forward when the cycle closes, so it
         * both keeps submitted work looking pending and hides work whose plan has moved on.
         */
        fun todayWorkOrders(rows: List<PmTaskRowUiModel>): List<PmTaskRowUiModel> =
            rows.filter { it.wo.status in ACTIVE && it.daysToDue <= 0 }
                .sortedWith(compareBy({ it.daysToDue }, { it.machineName }))

        /**
         * Plans past due that have no work order at all. The backend generates one on
         * every read, so this is normally empty — it exists so a generation failure shows
         * up as a visible row instead of silently dropping the technician's work.
         */
        fun ungeneratedDuePlans(
            plans: List<PmPlanRowUiModel>,
            wos: List<PmWorkOrder>,
        ): List<PmPlanRowUiModel> {
            val planned = wos.mapTo(mutableSetOf()) { it.planId }
            return plans.filter { it.daysToDue <= 0 && it.planId !in planned }
                .sortedWith(compareBy({ it.daysToDue }, { it.machineName }))
        }

        /** Finished work, newest first, grouped into calendar months. */
        fun historyGroups(rows: List<PmTaskRowUiModel>): List<PmHistoryGroupUiModel> =
            rows.filter { it.wo.status in HISTORY }
                .sortedByDescending { it.doneAt }
                .groupBy { DateTimeFormatter.formatMonthYear(it.doneAt) }
                .map { (label, group) -> PmHistoryGroupUiModel(label, group) }
    }
}
