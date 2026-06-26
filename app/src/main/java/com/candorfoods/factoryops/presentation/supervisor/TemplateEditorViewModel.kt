package com.candorfoods.factoryops.presentation.supervisor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.domain.model.PmTemplate
import com.candorfoods.factoryops.domain.model.PmTemplateItem
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.TradeType
import com.candorfoods.factoryops.domain.repository.PmTemplateRepository
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

data class TemplateEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    val templateId: String? = null,
    val name: String = "",
    val machineType: MachineType = MachineType.OTHER,
    val description: String = "",
    val estimatedDurationMinutes: String = "60",
    val defaultTradeType: TradeType = TradeType.MECHANICAL,
    val items: List<ItemDraft> = listOf(ItemDraft(id = "draft-${UUID.randomUUID()}")),
    val error: String? = null
) {
    val canSave: Boolean
        get() = name.isNotBlank() && items.isNotEmpty() && items.all { it.title.isNotBlank() }
}

sealed class TemplateEditorEvent {
    data object Saved : TemplateEditorEvent()
    data class ShowError(val message: String) : TemplateEditorEvent()
}

@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val templateRepo: PmTemplateRepository,
    private val getCurrentUser: GetCurrentUserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplateEditorUiState())
    val uiState: StateFlow<TemplateEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<TemplateEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val rawId: String? = savedStateHandle[Screen.SupervisorTemplateEditor.ARG]
        val templateId = rawId?.takeIf { it.isNotBlank() }
        if (templateId != null) {
            loadTemplate(templateId)
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun loadTemplate(id: String) {
        viewModelScope.launch {
            val template = templateRepo.getTemplateById(id)
            if (template == null) {
                _uiState.update { it.copy(isLoading = false, error = "Template not found.") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isEditMode = true,
                    templateId = template.id,
                    name = template.name,
                    machineType = template.machineType,
                    description = template.description,
                    estimatedDurationMinutes = template.estimatedDurationMinutes.toString(),
                    defaultTradeType = template.defaultTradeType,
                    items = template.items.map { item ->
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
                    }.ifEmpty { listOf(ItemDraft(id = "draft-${UUID.randomUUID()}")) }
                )
            }
        }
    }

    fun onNameChange(v: String)        = _uiState.update { it.copy(name = v) }
    fun onMachineTypeChange(v: MachineType) = _uiState.update { it.copy(machineType = v) }
    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }
    fun onDurationChange(v: String)    = _uiState.update { it.copy(estimatedDurationMinutes = v.filter { c -> c.isDigit() }) }
    fun onTradeTypeChange(v: TradeType) = _uiState.update { it.copy(defaultTradeType = v) }

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
            // Always keep at least 1 item slot.
            state.copy(items = newItems.ifEmpty { listOf(ItemDraft(id = "draft-${UUID.randomUUID()}")) })
        }
    }

    fun onMoveUp(index: Int) {
        if (index <= 0) return
        _uiState.update { state ->
            val list = state.items.toMutableList()
            val tmp = list[index - 1]
            list[index - 1] = list[index]
            list[index] = tmp
            state.copy(items = list)
        }
    }

    fun onMoveDown(index: Int) {
        _uiState.update { state ->
            if (index >= state.items.size - 1) return@update state
            val list = state.items.toMutableList()
            val tmp = list[index + 1]
            list[index + 1] = list[index]
            list[index] = tmp
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
            val templateId = state.templateId ?: "tmpl-${UUID.randomUUID()}"

            val template = PmTemplate(
                id = templateId,
                name = state.name.trim(),
                machineType = state.machineType,
                description = state.description.trim(),
                estimatedDurationMinutes = state.estimatedDurationMinutes.toIntOrNull() ?: 60,
                defaultTradeType = state.defaultTradeType,
                createdBy = user?.id ?: "user-sup-001",
                createdAt = if (state.isEditMode) now else now,
                updatedAt = now,
                isActive = true
            )
            val items = state.items.mapIndexed { idx, draft ->
                PmTemplateItem(
                    id = if (draft.id.startsWith("draft-")) "item-${UUID.randomUUID()}" else draft.id,
                    templateId = templateId,
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

            when (val result = templateRepo.saveTemplate(template, items)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(TemplateEditorEvent.Saved)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = result.message) }
                    _events.send(TemplateEditorEvent.ShowError(result.message))
                }
                Result.Loading -> Unit
            }
        }
    }
}
