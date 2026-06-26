package com.candorfoods.factoryops.presentation.supervisor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.PmPlan
import com.candorfoods.factoryops.domain.model.PmTemplate
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.repository.MachineRepository
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
    val templates: List<PmTemplate> = emptyList(),
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
    private val machineRepo: MachineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupervisorHomeUiState())
    val uiState: StateFlow<SupervisorHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getCurrentUser()
                .filterNotNull()
                .collect { user ->
                    _uiState.update { it.copy(currentUser = user) }
                }
        }

        // Templates with their items resolved (each row needs item count).
        viewModelScope.launch {
            templateRepo.observeActiveTemplates().collect { tmpls ->
                _uiState.update { it.copy(templates = tmpls, isLoading = false) }
            }
        }

        // Plans joined to machine + template for display.
        viewModelScope.launch {
            combine(
                planRepo.observeAllPlans(),
                machineRepo.observeAllMachines(),
                templateRepo.observeActiveTemplates()
            ) { plans, machines, templates ->
                val machineById = machines.associateBy { it.id }
                val templateById = templates.associateBy { it.id }
                plans.mapNotNull { plan ->
                    val m = machineById[plan.machineId] ?: return@mapNotNull null
                    val t = templateById[plan.templateId]
                    SupervisorPlanRow(
                        plan = plan,
                        machineName = m.name,
                        machineCode = m.code,
                        templateName = t?.name ?: "(template missing)",
                        templateMachineType = (t?.machineType ?: m.machineType).name
                    )
                }
            }.collect { rows ->
                _uiState.update { it.copy(plans = rows) }
            }
        }

        // Active WOs joined to machine.
        viewModelScope.launch {
            combine(
                woRepo.observeAll(),
                machineRepo.observeAllMachines()
            ) { wos, machines ->
                val machineById: Map<String, Machine> = machines.associateBy { it.id }
                wos.sortedBy { it.scheduledDate }
                    .mapNotNull { wo ->
                        val m = machineById[wo.machineId] ?: return@mapNotNull null
                        SupervisorWoRow(wo = wo, machineName = m.name, machineCode = m.code)
                    }
            }.collect { rows ->
                _uiState.update { it.copy(workOrders = rows) }
            }
        }

        // Phase 2C-2 — pending approvals = WOs with status=SUBMITTED awaiting supervisor decision.
        viewModelScope.launch {
            combine(
                woRepo.observeByStatus(com.candorfoods.factoryops.domain.model.PmWoStatus.SUBMITTED),
                machineRepo.observeAllMachines()
            ) { wos, machines ->
                val machineById = machines.associateBy { it.id }
                wos.sortedBy { it.submittedAt ?: Long.MAX_VALUE }
                    .mapNotNull { wo ->
                        val m = machineById[wo.machineId] ?: return@mapNotNull null
                        SupervisorWoRow(wo = wo, machineName = m.name, machineCode = m.code)
                    }
            }.collect { rows ->
                _uiState.update { it.copy(pendingApprovals = rows) }
            }
        }
    }
}
