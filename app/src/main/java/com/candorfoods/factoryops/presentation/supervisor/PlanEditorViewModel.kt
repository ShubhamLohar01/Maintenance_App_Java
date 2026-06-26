package com.candorfoods.factoryops.presentation.supervisor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.PmPlan
import com.candorfoods.factoryops.domain.model.PmTemplate
import com.candorfoods.factoryops.domain.model.PmTriggerType
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MachineRepository
import com.candorfoods.factoryops.domain.repository.PmPlanRepository
import com.candorfoods.factoryops.domain.repository.PmTemplateRepository
import com.candorfoods.factoryops.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PlanEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    val planId: String? = null,
    val machines: List<Machine> = emptyList(),
    val allTemplates: List<PmTemplate> = emptyList(),
    val selectedMachineId: String? = null,
    val selectedTemplateId: String? = null,
    val triggerType: PmTriggerType = PmTriggerType.TIME,
    val triggerInterval: String = "30",
    val assignedTechnicianId: String = "user-002",   // default Priya — Phase 3 backend will provide a roster
    val error: String? = null
) {
    val templatesForSelectedMachine: List<PmTemplate>
        get() {
            val machine = machines.firstOrNull { it.id == selectedMachineId }
                ?: return emptyList()
            return allTemplates.filter { it.machineType == machine.machineType }
        }

    val canSave: Boolean
        get() = selectedMachineId != null
            && selectedTemplateId != null
            && (triggerInterval.toIntOrNull() ?: 0) > 0
}

sealed class PlanEditorEvent {
    data object Saved : PlanEditorEvent()
    data class ShowError(val message: String) : PlanEditorEvent()
}

@HiltViewModel
class PlanEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val machineRepo: MachineRepository,
    private val templateRepo: PmTemplateRepository,
    private val planRepo: PmPlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanEditorUiState())
    val uiState: StateFlow<PlanEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlanEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val machines = machineRepo.observeAllMachines().first()
            val templates = templateRepo.observeActiveTemplates().first()
            _uiState.update {
                it.copy(machines = machines, allTemplates = templates, isLoading = false)
            }

            val rawId: String? = savedStateHandle[Screen.SupervisorPlanEditor.ARG]
            val planId = rawId?.takeIf { it.isNotBlank() }
            if (planId != null) {
                val plan = planRepo.getPlanById(planId)
                if (plan != null) {
                    _uiState.update {
                        it.copy(
                            isEditMode = true,
                            planId = plan.id,
                            selectedMachineId = plan.machineId,
                            selectedTemplateId = plan.templateId,
                            triggerType = plan.triggerType,
                            triggerInterval = plan.triggerInterval.toString(),
                            assignedTechnicianId = plan.assignedTechnicianId
                        )
                    }
                }
            }
        }
    }

    fun onMachineSelected(id: String) {
        _uiState.update { state ->
            state.copy(
                selectedMachineId = id,
                // Reset template if it doesn't match the new machine type.
                selectedTemplateId = state.selectedTemplateId
                    ?.takeIf { tid ->
                        val tmpl = state.allTemplates.firstOrNull { it.id == tid }
                        val machine = state.machines.firstOrNull { it.id == id }
                        tmpl?.machineType == machine?.machineType
                    }
            )
        }
    }

    fun onTemplateSelected(id: String) = _uiState.update { it.copy(selectedTemplateId = id) }
    fun onTriggerTypeChange(t: PmTriggerType) = _uiState.update { it.copy(triggerType = t) }
    fun onIntervalChange(v: String) = _uiState.update { it.copy(triggerInterval = v.filter { c -> c.isDigit() }) }
    fun onTechnicianIdChange(v: String) = _uiState.update { it.copy(assignedTechnicianId = v) }

    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val now = System.currentTimeMillis()
            val planId = state.planId ?: "plan-${UUID.randomUUID()}"
            val interval = state.triggerInterval.toIntOrNull() ?: 30
            val nextDueAt = now + interval * 86_400_000L

            val plan = PmPlan(
                id = planId,
                machineId = state.selectedMachineId!!,
                templateId = state.selectedTemplateId!!,
                triggerType = state.triggerType,
                triggerInterval = interval,
                lastCompletedAt = null,
                lastCompletedAtHours = null,
                nextDueAt = nextDueAt,
                assignedTechnicianId = state.assignedTechnicianId,
                isActive = true,
                createdAt = now
            )
            when (val result = planRepo.savePlan(plan)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(PlanEditorEvent.Saved)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = result.message) }
                    _events.send(PlanEditorEvent.ShowError(result.message))
                }
                Result.Loading -> Unit
            }
        }
    }
}
