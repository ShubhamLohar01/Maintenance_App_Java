package com.candorfoods.factoryops.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.ElectricAssetSchedule
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.ScheduleInterval
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

/** Bottom-sheet state for editing one asset's daily recording windows. */
data class ScheduleEditorState(
    val assetId: String,
    val assetName: String,
    /** The windows this asset runs each scheduled day. Never empty while the sheet is open. */
    val intervals: List<ScheduleInterval>,
    val active: Boolean,
    val hasExisting: Boolean,
    /** Weekdays the windows fire (0=Sunday … 6=Saturday). A day off is skipped. */
    val days: Set<Int>,
    /** Run all day: the interval list is hidden and each active day counts as 24h. */
    val is24h: Boolean,
) {
    /** Total hours per scheduled day — 24 for an all-day run, else the sum of windows. */
    val totalHours: Double
        get() = if (is24h) 24.0 else intervals.sumOf { it.hours }

    /** The last interval can't be removed; a schedule with no window is just no schedule. */
    val canRemoveInterval: Boolean get() = intervals.size > 1

    /**
     * First problem with the windows, or null when they're fine. Overlaps name both
     * offenders rather than failing with a generic message.
     */
    val validationError: String?
        get() {
            if (is24h) return null
            if (intervals.isEmpty()) return "Add at least one interval."
            intervals.firstOrNull { !it.isValid }?.let {
                return "End time must be after start time (${label(it.startMin)}–${label(it.endMin)})."
            }
            val sorted = intervals.sortedBy { it.startMin }
            sorted.zipWithNext().firstOrNull { (a, b) -> a.overlaps(b) }?.let { (a, b) ->
                return "Intervals overlap: ${label(a.startMin)}–${label(a.endMin)} and " +
                    "${label(b.startMin)}–${label(b.endMin)}."
            }
            return null
        }
}

/** Minute-of-day → "10:00 AM". Shared by the editor's messages and the list card. */
fun label(min: Int): String {
    val h24 = (min / 60) % 24
    val m = min % 60
    val period = if (h24 < 12) "AM" else "PM"
    val h12 = h24 % 12
    return "%d:%02d %s".format(if (h12 == 0) 12 else h12, m, period)
}

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
    val selectedBuilding: String? = null,   // null = all warehouses
    val selectedFloor: String? = null,      // null = all floors
    val searchQuery: String = "",           // blank = no name search
    val editor: ScheduleEditorState? = null,
    val assetEdit: AssetEditState? = null,
) {
    /** Only a supervisor may create/edit/delete schedules or asset details. HEAD is read-only. */
    val canEdit: Boolean get() = currentUser?.role == UserRole.SUPERVISOR

    /** Warehouses present in the data — drives the warehouse filter. */
    val buildings: List<String>
        get() = schedules.map { it.building }.filter { it.isNotBlank() }.distinct().sorted()

    /** Floors offered by the floor filter — scoped to the selected warehouse. */
    val floors: List<String>
        get() = schedules
            .filter { selectedBuilding == null || it.building.equals(selectedBuilding, true) }
            .map { it.subLocation }.filter { it.isNotBlank() }.distinct().sorted()

    /** The list actually rendered: warehouse + floor filters and name search applied (AND). */
    val filteredSchedules: List<ElectricAssetSchedule>
        get() {
            val q = searchQuery.trim()
            return schedules
                .filter { selectedBuilding == null || it.building.equals(selectedBuilding, true) }
                .filter { selectedFloor == null || it.subLocation.equals(selectedFloor, true) }
                .filter {
                    q.isBlank() ||
                        it.assetName.contains(q, ignoreCase = true) ||
                        it.assetId.contains(q, ignoreCase = true)
                }
        }
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

    // ----- Filters ---------------------------------------------------------------

    /** Warehouse filter; switching it resets the floor filter (floors are per-building). */
    fun onBuildingFilter(building: String?) =
        _uiState.update { it.copy(selectedBuilding = building, selectedFloor = null) }

    fun onFloorFilter(floor: String?) = _uiState.update { it.copy(selectedFloor = floor) }

    /** Free-text machine-name / asset-ID search, applied on top of the warehouse/floor filters. */
    fun onSearchChange(query: String) = _uiState.update { it.copy(searchQuery = query) }

    // ----- Schedule editor ------------------------------------------------------

    fun onOpenEditor(item: ElectricAssetSchedule) {
        _uiState.update {
            it.copy(editor = ScheduleEditorState(
                assetId = item.assetId,
                assetName = item.assetName,
                intervals = item.intervals.ifEmpty { listOf(DEFAULT_INTERVAL) },
                active = if (item.hasSchedule) item.active else true,
                hasExisting = item.hasSchedule,
                // New schedules default to every day; existing ones keep their saved days.
                days = item.days.ifEmpty { ElectricAssetSchedule.ALL_DAYS },
                is24h = item.is24h,
            ))
        }
    }

    fun onCloseEditor() = _uiState.update { it.copy(editor = null) }

    fun onStartChange(index: Int, min: Int) = mutateInterval(index) { it.copy(startMin = min) }
    fun onEndChange(index: Int, min: Int) = mutateInterval(index) { it.copy(endMin = min) }

    /**
     * Append a window after the last one. It starts an hour past the current latest end
     * so the new row is valid and non-overlapping the moment it appears — the supervisor
     * adjusts it rather than having to fix a clash first. Clamped to the end of the day.
     */
    fun onAddInterval() = _uiState.update { s ->
        val ed = s.editor ?: return@update s
        if (ed.intervals.size >= MAX_INTERVALS) return@update s
        val lastEnd = ed.intervals.maxOfOrNull { it.endMin } ?: 0
        val start = (lastEnd + GAP_MIN).coerceAtMost(ScheduleInterval.MINUTES_IN_DAY - MIN_SPAN_MIN)
        val end = (start + DEFAULT_SPAN_MIN).coerceAtMost(ScheduleInterval.MINUTES_IN_DAY)
        s.copy(editor = ed.copy(intervals = ed.intervals + ScheduleInterval(start, end)))
    }

    fun onRemoveInterval(index: Int) = _uiState.update { s ->
        val ed = s.editor ?: return@update s
        if (!ed.canRemoveInterval) return@update s
        s.copy(editor = ed.copy(intervals = ed.intervals.filterIndexed { i, _ -> i != index }))
    }

    private fun mutateInterval(index: Int, transform: (ScheduleInterval) -> ScheduleInterval) =
        _uiState.update { s ->
            val ed = s.editor ?: return@update s
            val next = ed.intervals.toMutableList()
            val current = next.getOrNull(index) ?: return@update s
            next[index] = transform(current)
            s.copy(editor = ed.copy(intervals = next))
        }

    fun onActiveChange(active: Boolean) = _uiState.update { s -> s.copy(editor = s.editor?.copy(active = active)) }
    fun on24hChange(is24h: Boolean) = _uiState.update { s -> s.copy(editor = s.editor?.copy(is24h = is24h)) }

    /** Toggle one weekday (0=Sunday … 6=Saturday) on/off in the editor. */
    fun onToggleDay(day: Int) = _uiState.update { s ->
        val ed = s.editor ?: return@update s
        val next = if (day in ed.days) ed.days - day else ed.days + day
        s.copy(editor = ed.copy(days = next))
    }

    fun onSaveSchedule() {
        val ed = _uiState.value.editor ?: return
        if (!_uiState.value.canEdit) return
        // A 24h run ignores the windows, so validationError skips them for that case.
        ed.validationError?.let {
            emit(ScheduleUiEvent.ShowSnackbar(it))
            return
        }
        if (ed.days.isEmpty()) {
            emit(ScheduleUiEvent.ShowSnackbar("Select at least one day."))
            return
        }
        // A full-day window is sent for a 24h run so the backend's duration maths holds.
        val intervals =
            if (ed.is24h) listOf(ScheduleInterval(0, FULL_DAY_MIN))
            else ed.intervals.sortedBy { it.startMin }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val r = scheduleRepository.setSchedule(ed.assetId, intervals, ed.active, ed.days, ed.is24h)
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
        const val FULL_DAY_MIN = 1440       // 24:00 — sent as the window for a 24h run
        /** First window offered on a brand-new schedule: 10:00–20:00. */
        val DEFAULT_INTERVAL = ScheduleInterval(600, 1200)
        /** A split shift is a handful of windows, not a timetable — keeps the sheet usable. */
        const val MAX_INTERVALS = 6
        const val GAP_MIN = 60              // breathing room after the previous window
        const val DEFAULT_SPAN_MIN = 120    // length of a freshly added window
        const val MIN_SPAN_MIN = 30
    }
}
