package com.candorfoods.factoryops.presentation.supervisor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.PmPlan
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.repository.PmPlanRepository
import com.candorfoods.factoryops.domain.repository.PmTemplateRepository
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SupervisorHomeUiState(
    val currentUser: User? = null,
    val isLoading: Boolean = true,
    val plans: List<SupervisorPlanRow> = emptyList(),
    val workOrders: List<SupervisorWoRow> = emptyList(),
    val pendingApprovals: List<SupervisorWoRow> = emptyList()
)

data class SupervisorPlanRow(
    val plan: PmPlan,
    val machineName: String,
    val machineCode: String,
    val templateName: String,
    val templateMachineType: String
)

data class SupervisorWoRow(
    val wo: PmWorkOrder,
    val machineName: String,
    val machineCode: String
)

@HiltViewModel
class SupervisorHomeViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val templateRepo: PmTemplateRepository,
    private val planRepo: PmPlanRepository,
    private val woRepo: PmWorkOrderRepository,
    private val mtMachineRepo: MtMachineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupervisorHomeUiState())
    val uiState: StateFlow<SupervisorHomeUiState> = _uiState.asStateFlow()

    /** asset_id → asset, from mt_asset_list, so a plan/WO resolves its real name. */
    private val assets = MutableStateFlow<Map<String, MtMachine>>(emptyMap())

    init {
        viewModelScope.launch {
            (mtMachineRepo.list() as? Result.Success)?.data?.let { list ->
                assets.value = list.associateBy { it.assetId }
            }
        }

        viewModelScope.launch {
            getCurrentUser()
                .filterNotNull()
                .collect { user ->
                    _uiState.update { it.copy(currentUser = user) }
                }
        }

        // Plans. A plan is bound to an asset (machineId = asset_id) and carries its
        // own checklist (persisted as a template under the hood). Names resolve from
        // the asset register, falling back to the stored checklist name.
        viewModelScope.launch {
            combine(
                planRepo.observeAllPlans(),
                templateRepo.observeActiveTemplates(),
                assets
            ) { plans, templates, assetMap ->
                val templateById = templates.associateBy { it.id }
                plans.map { plan ->
                    val asset = assetMap[plan.machineId]
                    val t = templateById[plan.templateId]
                    SupervisorPlanRow(
                        plan = plan,
                        machineName = asset?.assetName ?: t?.name ?: "(machine)",
                        machineCode = plan.machineId,
                        templateName = t?.name ?: "(checklist)",
                        templateMachineType = (t?.machineType?.name ?: asset?.category ?: "")
                    )
                }
            }.collect { rows ->
                _uiState.update { it.copy(plans = rows, isLoading = false) }
            }
        }

        // Active WOs. machineId is an asset_id; name from the register, else the
        // WO's templateName snapshot (which = the asset name for asset-based plans).
        viewModelScope.launch {
            combine(woRepo.observeAll(), assets) { wos, assetMap ->
                wos.sortedBy { it.scheduledDate }.map { wo ->
                    SupervisorWoRow(
                        wo = wo,
                        machineName = assetMap[wo.machineId]?.assetName ?: wo.templateName,
                        machineCode = wo.machineId
                    )
                }
            }.collect { rows ->
                _uiState.update { it.copy(workOrders = rows) }
            }
        }

        // Phase 2C-2 — pending approvals = WOs with status=SUBMITTED awaiting supervisor decision.
        viewModelScope.launch {
            combine(
                woRepo.observeByStatus(com.candorfoods.factoryops.domain.model.PmWoStatus.SUBMITTED),
                assets
            ) { wos, assetMap ->
                wos.sortedBy { it.submittedAt ?: Long.MAX_VALUE }.map { wo ->
                    SupervisorWoRow(
                        wo = wo,
                        machineName = assetMap[wo.machineId]?.assetName ?: wo.templateName,
                        machineCode = wo.machineId
                    )
                }
            }.collect { rows ->
                _uiState.update { it.copy(pendingApprovals = rows) }
            }
        }
    }
}
