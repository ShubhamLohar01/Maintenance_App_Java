package com.candorfoods.factoryops.presentation.technician

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.FloorReadingRow
import com.candorfoods.factoryops.domain.model.isMeterlessFloor
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.w202.AcReadingPoint
import com.candorfoods.factoryops.domain.model.w202.W202_PLANT
import com.candorfoods.factoryops.domain.model.w202.acReadingPoints
import com.candorfoods.factoryops.domain.repository.FloorReadingRepository
import com.candorfoods.factoryops.domain.repository.ReportsRepository
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetMachineMasterUseCase
import com.candorfoods.factoryops.util.excel.ReadingSheets
import com.candorfoods.factoryops.util.excel.SpreadsheetExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class DailyReadingPhase { Idle, Loading, Loaded, Submitting }

/** The date a "Daily Reading" round records: YESTERDAY (IST). A round reports the
 *  previous day's full consumption — today isn't over yet. Requested explicitly so
 *  the screen never depends on the backend's default (which may lag behind). */
private val IST = ZoneId.of("Asia/Kolkata")
private fun previousReadingDate(): String = LocalDate.now(IST).minusDays(1).toString()

private const val TITLE = "Electric consumption"
/** At or below this many AC rows the group opens on load — collapsing one row is noise. */
private const val AC_AUTO_EXPAND_MAX = 3
/** How far back a download reaches — a month of rounds, not the whole table. */
private const val EXPORT_DAYS = 30L

/** One floor row as edited on screen; [input] is the raw text in the meter-reading box. */
data class FloorRowUi(
    val floor: String,
    val systemReading: Double,
    val input: String,
)

/**
 * One AC unit on the same form (W-202 only). There is no system column: nothing in
 * mt_machine_daily_kwh covers AC units, so the only prior value is [saved] — what was
 * stored for this date — shown so a correction reads as one.
 */
data class AcRowUi(
    val point: AcReadingPoint,
    val input: String = "",
    val saved: Double? = null,
) {
    val entered: Double? get() = input.trim().toDoubleOrNull()
    val hasInput: Boolean get() = input.isNotBlank()
    val isInvalid: Boolean get() = hasInput && entered == null
}

data class DailyReadingUiState(
    val phase: DailyReadingPhase = DailyReadingPhase.Idle,
    val building: String = "",
    val readingDate: String = "",
    val rows: List<FloorRowUi> = emptyList(),
    /** AC units on this form. Empty for every building except W-202. */
    val acRows: List<AcRowUi> = emptyList(),
    /** Whether the AC group is open. Set on load: a short list opens, a long one collapses
     *  so it can't bury the floors above it. */
    val isAcExpanded: Boolean = false,
    val error: String? = null,
    /** One-shot status line from a download, shown as a snackbar then cleared. */
    val downloadMessage: String? = null,
    val isDownloading: Boolean = false,
) {
    /**
     * Submit is allowed once at least one floor holds a valid number. Idle floors / floors
     * with no physical meter may stay blank — only the filled floors are sent (the backend
     * upserts whatever rows it receives), so we must not gate on every floor being filled.
     */
    val canSubmit: Boolean
        get() = phase == DailyReadingPhase.Loaded &&
            !acRows.any { it.isInvalid } &&
            (filledFloors.isNotEmpty() || filledAc.isNotEmpty())

    val filledFloors: List<FloorRowUi>
        get() = rows.filter { it.input.trim().toDoubleOrNull() != null }

    val filledAc: List<AcRowUi> get() = acRows.filter { it.hasInput }

    /** What the Submit button counts — floors and ACs go up in one round. */
    val filledCount: Int get() = filledFloors.size + filledAc.size
}

sealed interface DailyReadingUiEvent {
    data class Saved(val count: Int) : DailyReadingUiEvent
    data class Error(val message: String) : DailyReadingUiEvent
}

@HiltViewModel
class DailyReadingViewModel @Inject constructor(
    private val repository: FloorReadingRepository,
    private val reportsRepository: ReportsRepository,
    private val getMachineMaster: GetMachineMasterUseCase,
    private val exporter: SpreadsheetExporter,
) : ViewModel() {

    /**
     * Download the last [EXPORT_DAYS] days of saved floor readings.
     *
     * This screen is a form for one day, so the history comes from the reports endpoint.
     * AC rows live in the same table and this screen now owns them too, so the export
     * carries both halves (acOnly = null) rather than the floors alone.
     */
    fun onDownload() {
        val state = _uiState.value
        if (state.isDownloading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true) }
            val to = state.readingDate.ifBlank { previousReadingDate() }
            val from = LocalDate.parse(to).minusDays(EXPORT_DAYS).toString()
            val message = when (
                val r = reportsRepository.floorReadingsReport(
                    state.building.ifBlank { null }, from, to,
                )
            ) {
                is Result.Success ->
                    exporter.export(ReadingSheets.floorReadings(TITLE, r.data, acOnly = null), TITLE)
                is Result.Error -> r.message
                is Result.Loading -> "Still loading"
            }
            _uiState.update { it.copy(isDownloading = false, downloadMessage = message) }
        }
    }

    fun onDownloadMessageShown() = _uiState.update { it.copy(downloadMessage = null) }

    private val _uiState = MutableStateFlow(DailyReadingUiState())
    val uiState: StateFlow<DailyReadingUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<DailyReadingUiEvent>()
    val uiEvent: SharedFlow<DailyReadingUiEvent> = _uiEvent.asSharedFlow()

    /** Tap "Fetch system reading" — also re-tappable to refresh the system values. */
    fun fetchSystemReading() {
        if (_uiState.value.phase == DailyReadingPhase.Loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(phase = DailyReadingPhase.Loading, error = null) }
            // date = yesterday (explicit): fetch the previous day's floor-wise readings, not
            // today's. building = null: technician screen — the backend resolves the plant from the JWT.
            when (val r = repository.fetchSystemReadings(previousReadingDate(), null)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        phase = DailyReadingPhase.Loaded,
                        building = r.data.building,
                        readingDate = r.data.readingDate,
                        // Canteen / gates / lab have no meter — see isMeterlessFloor.
                        rows = r.data.floors.filterNot { isMeterlessFloor(it.floor) }.map { f ->
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
                    return@launch
                }
                else -> return@launch
            }
            loadAcRows()
        }
    }

    /**
     * W-202's AC units, appended to the same round. They aren't in the /floor-readings
     * /system response (that lists floors from the asset register's sub_location), so the
     * units come from the register and their prior values from the reports endpoint —
     * the same two reads the standalone AC screen used to make.
     *
     * Best-effort throughout: a failure here costs the AC group, not the floor form.
     */
    private suspend fun loadAcRows() {
        val state = _uiState.value
        if (!state.building.equals(W202_PLANT, ignoreCase = true)) {
            _uiState.update { it.copy(acRows = emptyList()) }
            return
        }

        val points = (getMachineMaster() as? Result.Success)
            ?.data?.acReadingPoints(state.building) ?: emptyList()
        if (points.isEmpty()) {
            _uiState.update { it.copy(acRows = emptyList()) }
            return
        }

        val saved = (
            reportsRepository.floorReadingsReport(
                plant = state.building,
                from = state.readingDate,
                to = state.readingDate,
            ) as? Result.Success
            )?.data?.associate { it.floor to it.meterReading } ?: emptyMap()

        val rows = points.map { p ->
            val prior = saved[p.floorKey]
            AcRowUi(point = p, input = prior?.let(::formatReading).orEmpty(), saved = prior)
        }
        _uiState.update { st ->
            st.copy(acRows = rows, isAcExpanded = rows.size <= AC_AUTO_EXPAND_MAX)
        }
    }

    fun onAcInputChange(assetId: String, value: String) {
        val sanitized = sanitizeNumeric(value)
        _uiState.update { st ->
            st.copy(
                acRows = st.acRows.map {
                    if (it.point.assetId == assetId) it.copy(input = sanitized) else it
                },
                error = null,
            )
        }
    }

    fun toggleAcSection() = _uiState.update { it.copy(isAcExpanded = !it.isAcExpanded) }

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
        // Floors and ACs share mt_floor_utility_readings; an AC is keyed by its prefixed
        // floorKey, so both halves of the round go up in a single POST.
        val rows = state.filledFloors.mapNotNull { row ->
            row.input.trim().toDoubleOrNull()?.let { FloorReadingRow(floor = row.floor, meterReading = it) }
        } + state.filledAc.mapNotNull { row ->
            row.entered?.let { FloorReadingRow(floor = row.point.floorKey, meterReading = it) }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(phase = DailyReadingPhase.Submitting, error = null) }
            // Echo the exact date that was fetched/displayed — never null. The backend
            // defaults an omitted reading_date on its own (currently "yesterday"), so
            // sending null here would silently save under a DIFFERENT day than what's
            // on screen the moment that default ever differs from what was fetched.
            when (val r = repository.submitReadings(state.readingDate, state.building, rows)) {
                is Result.Success -> {
                    _uiState.update { it.copy(phase = DailyReadingPhase.Loaded) }
                    _uiEvent.emit(DailyReadingUiEvent.Saved(r.data.saved))
                    // Re-read so each AC's "saved" reflects what the server now holds.
                    loadAcRows()
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
