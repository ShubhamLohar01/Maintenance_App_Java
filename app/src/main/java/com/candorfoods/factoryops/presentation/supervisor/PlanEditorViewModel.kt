package com.candorfoods.factoryops.presentation.supervisor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.PmPlan
import com.candorfoods.factoryops.domain.model.PmTemplate
import com.candorfoods.factoryops.domain.model.PmTemplateItem
import com.candorfoods.factoryops.domain.model.PmTriggerType
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.TradeType
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.UserSummary
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.repository.PmPlanRepository
import com.candorfoods.factoryops.domain.repository.PmTemplateRepository
import com.candorfoods.factoryops.domain.repository.UserRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
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

/** One editable checklist step of a plan. */
data class ItemDraft(
    val id: String,
    val title: String = "",
    val description: String = "",
    val expectedResult: String = "",
    val requiresPhoto: Boolean = false,
    val requiresMeasurement: Boolean = false,
    val measurementUnit: String = "",
    val measurementMin: String = "",
    val measurementMax: String = ""
)

/**
 * State for the merged Plan editor. A "plan" is a PM checklist bound to one
 * specific asset (from `mt_asset_list`) plus its schedule. The plan is named
 * after its asset — there is no separate name field. Under the hood it is stored
 * as a [PmTemplate] (checklist; name = asset_name) + a [PmPlan] (machine +
 * schedule, machineId = asset_id) pair, so the scheduler/work-order pipeline
 * keeps working.
 */
data class PlanEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    val planId: String? = null,
    val assets: List<MtMachine> = emptyList(),
    val selectedAssetId: String? = null,
    val description: String = "",
    val technicians: List<UserSummary> = emptyList(),
    val assignedTechnicianId: String? = null,
    val triggerType: PmTriggerType = PmTriggerType.TIME,
    val triggerInterval: String = "30",
    val items: List<ItemDraft> = listOf(ItemDraft(id = "draft-${UUID.randomUUID()}")),
    val error: String? = null
) {
    val selectedAssetName: String
        get() = assets.firstOrNull { it.assetId == selectedAssetId }?.assetName.orEmpty()

    val canSave: Boolean
        get() = selectedAssetId != null
            && !assignedTechnicianId.isNullOrBlank()
            && (triggerInterval.toIntOrNull() ?: 0) > 0
            && items.isNotEmpty()
            && items.all { it.title.isNotBlank() }
}

sealed class PlanEditorEvent {
    data object Saved : PlanEditorEvent()
    data class ShowError(val message: String) : PlanEditorEvent()
}

@HiltViewModel
class PlanEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mtMachineRepo: MtMachineRepository,
    private val templateRepo: PmTemplateRepository,
    private val planRepo: PmPlanRepository,
    private val userRepo: UserRepository,
    private val getCurrentUser: GetCurrentUserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanEditorUiState())
    val uiState: StateFlow<PlanEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlanEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Carried over on edit so a re-save doesn't wipe schedule history.
    private var templateId: String? = null
    private var loadedPlanCreatedAt: Long? = null
    private var loadedLastCompletedAt: Long? = null
    private var loadedLastCompletedAtHours: Double? = null

    init {
        viewModelScope.launch {
            val assets = (mtMachineRepo.list() as? Result.Success)?.data.orEmpty()
                .sortedBy { it.assetName.lowercase() }
            val technicians = userRepo.getUsersByRole(UserRole.TECHNICIAN)
            _uiState.update {
                it.copy(
                    assets = assets,
                    technicians = technicians,
                    // Sensible default so the field isn't empty; supervisor can change it.
                    assignedTechnicianId = it.assignedTechnicianId ?: technicians.firstOrNull()?.id
                )
            }

            val rawId: String? = savedStateHandle[Screen.SupervisorPlanEditor.ARG]
            val planId = rawId?.takeIf { it.isNotBlank() }
            if (planId != null) {
                loadPlan(planId)
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadPlan(id: String) {
        val plan = planRepo.getPlanById(id)
        if (plan == null) {
            _uiState.update { it.copy(isLoading = false, error = "Plan not found.") }
            return
        }
        templateId = plan.templateId
        loadedPlanCreatedAt = plan.createdAt
        loadedLastCompletedAt = plan.lastCompletedAt
        loadedLastCompletedAtHours = plan.lastCompletedAtHours

        val template = templateRepo.getTemplateById(plan.templateId)
        _uiState.update {
            it.copy(
                isLoading = false,
                isEditMode = true,
                planId = plan.id,
                selectedAssetId = plan.machineId,   // = mt_asset_list.asset_id
                description = template?.description.orEmpty(),
                assignedTechnicianId = plan.assignedTechnicianId,
                triggerType = plan.triggerType,
                triggerInterval = plan.triggerInterval.toString(),
                items = template?.items?.map { item ->
                    ItemDraft(
                        id = item.id,
                        title = item.title,
                        description = item.description,
                        expectedResult = item.expectedResult,
                        requiresPhoto = item.requiresPhoto,
                        requiresMeasurement = item.requiresMeasurement,
                        measurementUnit = item.measurementUnit.orEmpty(),
                        measurementMin = item.measurementMin?.toString().orEmpty(),
                        measurementMax = item.measurementMax?.toString().orEmpty()
                    )
                }?.ifEmpty { listOf(ItemDraft(id = "draft-${UUID.randomUUID()}")) }
                    ?: listOf(ItemDraft(id = "draft-${UUID.randomUUID()}"))
            )
        }
    }

    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }
    fun onAssetSelected(assetId: String) = _uiState.update { it.copy(selectedAssetId = assetId) }
    fun onTechnicianSelected(id: String) = _uiState.update { it.copy(assignedTechnicianId = id) }
    fun onTriggerTypeChange(t: PmTriggerType) = _uiState.update { it.copy(triggerType = t) }
    fun onIntervalChange(v: String) = _uiState.update { it.copy(triggerInterval = v.filter { c -> c.isDigit() }) }

    fun onItemChange(index: Int, transform: (ItemDraft) -> ItemDraft) {
        _uiState.update { state ->
            state.copy(items = state.items.toMutableList().also { list ->
                if (index in list.indices) list[index] = transform(list[index])
            })
        }
    }

    fun onAddItem() {
        _uiState.update { state ->
            state.copy(items = state.items + ItemDraft(id = "draft-${UUID.randomUUID()}"))
        }
    }

    fun onRemoveItem(index: Int) {
        _uiState.update { state ->
            val newItems = state.items.toMutableList().also {
                if (index in it.indices) it.removeAt(index)
            }
            state.copy(items = newItems.ifEmpty { listOf(ItemDraft(id = "draft-${UUID.randomUUID()}")) })
        }
    }

    fun onMoveUp(index: Int) {
        if (index <= 0) return
        _uiState.update { state ->
            val list = state.items.toMutableList()
            val tmp = list[index - 1]; list[index - 1] = list[index]; list[index] = tmp
            state.copy(items = list)
        }
    }

    fun onMoveDown(index: Int) {
        _uiState.update { state ->
            if (index >= state.items.size - 1) return@update state
            val list = state.items.toMutableList()
            val tmp = list[index + 1]; list[index + 1] = list[index]; list[index] = tmp
            state.copy(items = list)
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val now = System.currentTimeMillis()
            val user = getCurrentUser().first()

            val asset = state.assets.firstOrNull { it.assetId == state.selectedAssetId }
            if (asset == null) {
                _uiState.update { it.copy(isSaving = false, error = "Pick a machine.") }
                _events.send(PlanEditorEvent.ShowError("Pick a machine."))
                return@launch
            }

            val tmplId = templateId ?: "tmpl-${UUID.randomUUID()}"
            val planId = state.planId ?: "plan-${UUID.randomUUID()}"

            // Remember the generated ids so a retry (after a failed server sync) reuses
            // them instead of minting new ones. The backend upserts on id, so reusing
            // keeps a retry idempotent — no duplicate plan/template locally or on server.
            templateId = tmplId
            if (state.planId == null) _uiState.update { it.copy(planId = planId) }

            // Under-the-hood template: the plan is named after its asset. Assets don't
            // carry a MachineType enum, so it defaults to OTHER; duration/trade-type keep
            // harmless defaults (both removed from the UI).
            val template = PmTemplate(
                id = tmplId,
                name = asset.assetName,
                machineType = MachineType.OTHER,
                description = state.description.trim(),
                estimatedDurationMinutes = DEFAULT_DURATION_MIN,
                defaultTradeType = TradeType.MECHANICAL,
                createdBy = user?.id ?: "user-sup-001",
                createdAt = now,
                updatedAt = now,
                isActive = true
            )
            val items = state.items.mapIndexed { idx, draft ->
                PmTemplateItem(
                    id = if (draft.id.startsWith("draft-")) "item-${UUID.randomUUID()}" else draft.id,
                    templateId = tmplId,
                    orderIndex = idx,
                    title = draft.title.trim(),
                    description = draft.description.trim(),
                    expectedResult = draft.expectedResult.trim(),
                    requiresPhoto = draft.requiresPhoto,
                    requiresMeasurement = draft.requiresMeasurement,
                    measurementUnit = draft.measurementUnit.takeIf { it.isNotBlank() },
                    measurementMin = draft.measurementMin.toDoubleOrNull(),
                    measurementMax = draft.measurementMax.toDoubleOrNull()
                )
            }

            when (val tmplResult = templateRepo.saveTemplate(template, items)) {
                is Result.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = tmplResult.message) }
                    _events.send(PlanEditorEvent.ShowError(tmplResult.message))
                    return@launch
                }
                else -> Unit
            }

            val interval = state.triggerInterval.toIntOrNull() ?: 30
            val plan = PmPlan(
                id = planId,
                machineId = asset.assetId,            // = mt_asset_list.asset_id
                templateId = tmplId,
                triggerType = state.triggerType,
                triggerInterval = interval,
                lastCompletedAt = loadedLastCompletedAt,
                lastCompletedAtHours = loadedLastCompletedAtHours,
                nextDueAt = computeNextDue(now, interval),
                assignedTechnicianId = state.assignedTechnicianId!!,
                isActive = true,
                createdAt = loadedPlanCreatedAt ?: now
            )
            when (val planResult = planRepo.savePlan(plan)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(PlanEditorEvent.Saved)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = planResult.message) }
                    _events.send(PlanEditorEvent.ShowError(planResult.message))
                }
                Result.Loading -> Unit
            }
        }
    }

    /** Next-due anchored on last completion when present, else now. */
    private fun computeNextDue(now: Long, intervalDays: Int): Long {
        val base = loadedLastCompletedAt ?: now
        return base + intervalDays * DAY_MS
    }

    companion object {
        private const val DAY_MS = 86_400_000L
        private const val DEFAULT_DURATION_MIN = 60
    }
}
