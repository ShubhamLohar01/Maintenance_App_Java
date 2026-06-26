package com.candorfoods.factoryops.presentation.technician

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.FloorReadingRow
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.FloorReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DailyReadingPhase { Idle, Loading, Loaded, Submitting }

/** One floor row as edited on screen; [input] is the raw text in the meter-reading box. */
data class FloorRowUi(
    val floor: String,
    val systemReading: Double,
    val input: String,
)

data class DailyReadingUiState(
    val phase: DailyReadingPhase = DailyReadingPhase.Idle,
    val building: String = "",
    val readingDate: String = "",
    val rows: List<FloorRowUi> = emptyList(),
    val error: String? = null,
) {
    /**
     * Submit is allowed once at least one floor holds a valid number. Idle floors / floors
     * with no physical meter may stay blank — only the filled floors are sent (the backend
     * upserts whatever rows it receives), so we must not gate on every floor being filled.
     */
    val canSubmit: Boolean
        get() = phase == DailyReadingPhase.Loaded &&
            rows.any { it.input.trim().toDoubleOrNull() != null }
}

sealed interface DailyReadingUiEvent {
    data class Saved(val count: Int) : DailyReadingUiEvent
    data class Error(val message: String) : DailyReadingUiEvent
}

@HiltViewModel
class DailyReadingViewModel @Inject constructor(
    private val repository: FloorReadingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyReadingUiState())
    val uiState: StateFlow<DailyReadingUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<DailyReadingUiEvent>()
    val uiEvent: SharedFlow<DailyReadingUiEvent> = _uiEvent.asSharedFlow()

    /** Tap "Fetch system reading" — also re-tappable to refresh the system values. */
    fun fetchSystemReading() {
        if (_uiState.value.phase == DailyReadingPhase.Loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(phase = DailyReadingPhase.Loading, error = null) }
            // building = null: technician screen — the backend resolves the plant from the JWT.
            when (val r = repository.fetchSystemReadings(null, null)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        phase = DailyReadingPhase.Loaded,
                        building = r.data.building,
                        readingDate = r.data.readingDate,
                        rows = r.data.floors.map { f ->
                            FloorRowUi(
                                floor = f.floor,
                                systemReading = f.systemReading,
                                input = f.meterReading?.let { v -> formatReading(v) } ?: "",
                            )
                        },
                        error = null,
                    )
                }
                is Result.Error -> {
                    _uiState.update { it.copy(phase = DailyReadingPhase.Idle, error = r.message) }
                    _uiEvent.emit(DailyReadingUiEvent.Error(r.message))
                }
                else -> Unit
            }
        }
    }

    fun onInputChange(floor: String, value: String) {
        val sanitized = sanitizeNumeric(value)
        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { if (it.floor == floor) it.copy(input = sanitized) else it },
                error = null,
            )
        }
    }

    /** Tap "Submit" — sends only the floors that were filled, echoing the fetched building. */
    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit || state.phase == DailyReadingPhase.Submitting) return
        val rows = state.rows.mapNotNull { row ->
            row.input.trim().toDoubleOrNull()?.let { FloorReadingRow(floor = row.floor, meterReading = it) }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(phase = DailyReadingPhase.Submitting, error = null) }
            when (val r = repository.submitReadings(null, state.building, rows)) {
                is Result.Success -> {
                    _uiState.update { it.copy(phase = DailyReadingPhase.Loaded) }
                    _uiEvent.emit(DailyReadingUiEvent.Saved(r.data.saved))
                }
                is Result.Error -> {
                    _uiState.update { it.copy(phase = DailyReadingPhase.Loaded, error = r.message) }
                    _uiEvent.emit(DailyReadingUiEvent.Error(r.message))
                }
                else -> Unit
            }
        }
    }
}

/** "90.0" → "90", "88.3" → "88.3" — drops the noisy trailing ".0" for whole readings. */
internal fun formatReading(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** Keep digits and a single decimal point so the input always parses to a Double. */
internal fun sanitizeNumeric(raw: String): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    if (firstDot == -1) return filtered
    return filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
}
