package com.candorfoods.factoryops.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.ElectricAssetSchedule
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.AssetScheduleRepository
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Bottom-sheet state for editing one asset's daily recording window. */
data class ScheduleEditorState(
    val assetId: String,
    val assetName: String,
    val startMin: Int,
    val endMin: Int,
    val active: Boolean,
    val hasExisting: Boolean,
)

/** Bottom-sheet state for editing the asset's own details (name/power/floor/condition). */
data class AssetEditState(
    val assetId: String,
    val assetName: String,
    val powerLoad: String,
    val subLocation: String,
    val condition: String,
)

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val currentUser: User? = null,
    val schedules: List<ElectricAssetSchedule> = emptyList(),
    val editor: ScheduleEditorState? = null,
    val assetEdit: AssetEditState? = null,
) {
    /** Only a supervisor may create/edit/delete schedules or asset details. HEAD is read-only. */
    val canEdit: Boolean get() = currentUser?.role == UserRole.SUPERVISOR
}

sealed interface ScheduleUiEvent {
    data class ShowSnackbar(val message: String) : ScheduleUiEvent
}

@HiltViewModel
class ScheduleElectricAssetsViewModel @Inject constructor(
    private val scheduleRepository: AssetScheduleRepository,
    private val mtMachineRepository: MtMachineRepository,
    getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<ScheduleUiEvent>()
    val uiEvent: SharedFlow<ScheduleUiEvent> = _uiEvent.asSharedFlow()

    // Full asset backing the current asset-edit sheet — kept so a save preserves the
    // fields the sheet doesn't expose (model_no, serial_no, quantity, remarks, ...).
    private var editingAsset: MtMachine? = null

    init {
        viewModelScope.launch {
            getCurrentUser().collect { user -> _uiState.update { it.copy(currentUser = user) } }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = scheduleRepository.list("both")) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, schedules = r.data, error = null)
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                is Result.Loading -> Unit
            }
        }
    }

    // ----- Schedule editor ------------------------------------------------------

    fun onOpenEditor(item: ElectricAssetSchedule) {
        _uiState.update {
            it.copy(editor = ScheduleEditorState(
                assetId = item.assetId,
                assetName = item.assetName,
                startMin = item.startMin ?: DEFAULT_START_MIN,
                endMin = item.endMin ?: DEFAULT_END_MIN,
                active = if (item.hasSchedule) item.active else true,
                hasExisting = item.hasSchedule,
            ))
        }
    }

    fun onCloseEditor() = _uiState.update { it.copy(editor = null) }
    fun onStartChange(min: Int) = _uiState.update { s -> s.copy(editor = s.editor?.copy(startMin = min)) }
    fun onEndChange(min: Int) = _uiState.update { s -> s.copy(editor = s.editor?.copy(endMin = min)) }
    fun onActiveChange(active: Boolean) = _uiState.update { s -> s.copy(editor = s.editor?.copy(active = active)) }

    fun onSaveSchedule() {
        val ed = _uiState.value.editor ?: return
        if (!_uiState.value.canEdit) return
        if (ed.endMin <= ed.startMin) {
            emit(ScheduleUiEvent.ShowSnackbar("End time must be after start time."))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val r = scheduleRepository.setSchedule(ed.assetId, ed.startMin, ed.endMin, ed.active)
            _uiState.update { it.copy(isSubmitting = false) }
            when (r) {
                is Result.Success -> { _uiState.update { it.copy(editor = null) }; load() }
                is Result.Error -> emit(ScheduleUiEvent.ShowSnackbar(r.message))
                is Result.Loading -> Unit
            }
        }
    }

    fun onClearSchedule() {
        val ed = _uiState.value.editor ?: return
        if (!_uiState.value.canEdit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val r = scheduleRepository.clearSchedule(ed.assetId)
            _uiState.update { it.copy(isSubmitting = false) }
            when (r) {
                is Result.Success -> { _uiState.update { it.copy(editor = null) }; load() }
                is Result.Error -> emit(ScheduleUiEvent.ShowSnackbar(r.message))
                is Result.Loading -> Unit
            }
        }
    }

    // ----- Asset detail editor --------------------------------------------------

    fun onOpenAssetEdit(item: ElectricAssetSchedule) {
        if (!_uiState.value.canEdit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            when (val r = mtMachineRepository.getByAssetId(item.assetId)) {
                is Result.Success -> {
                    val asset = r.data
                    if (asset == null) {
                        emit(ScheduleUiEvent.ShowSnackbar("Asset not found."))
                    } else {
                        editingAsset = asset
                        _uiState.update {
                            it.copy(editor = null, assetEdit = AssetEditState(
                                assetId = asset.assetId,
                                assetName = asset.assetName,
                                powerLoad = asset.powerLoadRaw.orEmpty(),
                                subLocation = asset.subLocation,
                                condition = asset.condition.orEmpty(),
                            ))
                        }
                    }
                }
                is Result.Error -> emit(ScheduleUiEvent.ShowSnackbar(r.message))
                is Result.Loading -> Unit
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun onCloseAssetEdit() { editingAsset = null; _uiState.update { it.copy(assetEdit = null) } }
    fun onAssetNameChange(v: String) = _uiState.update { s -> s.copy(assetEdit = s.assetEdit?.copy(assetName = v)) }
    fun onPowerLoadChange(v: String) = _uiState.update { s -> s.copy(assetEdit = s.assetEdit?.copy(powerLoad = v)) }
    fun onSubLocationChange(v: String) = _uiState.update { s -> s.copy(assetEdit = s.assetEdit?.copy(subLocation = v)) }
    fun onConditionChange(v: String) = _uiState.update { s -> s.copy(assetEdit = s.assetEdit?.copy(condition = v)) }

    fun onSaveAsset() {
        val draft = _uiState.value.assetEdit ?: return
        val base = editingAsset ?: return
        if (!_uiState.value.canEdit) return
        if (draft.assetName.isBlank()) {
            emit(ScheduleUiEvent.ShowSnackbar("Asset name can't be empty."))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val updated = base.copy(
                assetName = draft.assetName.trim(),
                powerLoadRaw = draft.powerLoad.trim().ifBlank { null },
                subLocation = draft.subLocation.trim(),
                condition = draft.condition.trim().ifBlank { null },
            )
            val r = mtMachineRepository.updateAsset(updated)
            _uiState.update { it.copy(isSubmitting = false) }
            when (r) {
                is Result.Success -> { editingAsset = null; _uiState.update { it.copy(assetEdit = null) }; load() }
                is Result.Error -> emit(ScheduleUiEvent.ShowSnackbar(r.message))
                is Result.Loading -> Unit
            }
        }
    }

    private fun emit(event: ScheduleUiEvent) {
        viewModelScope.launch { _uiEvent.emit(event) }
    }

    private companion object {
        const val DEFAULT_START_MIN = 600   // 10:00
        const val DEFAULT_END_MIN = 1200    // 20:00
    }
}
