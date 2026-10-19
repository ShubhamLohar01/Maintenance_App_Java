package com.candorfoods.factoryops.presentation.w202

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.w202.W202MeterKind
import com.candorfoods.factoryops.domain.model.w202.W202MeterReading
import com.candorfoods.factoryops.domain.model.w202.W202Prefill
import com.candorfoods.factoryops.domain.repository.W202ReadingsRepository
import com.candorfoods.factoryops.presentation.utilities.formatUtilityNumber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The live diff drives the on-screen consumption for one Initial/Final pair. */
private fun diff(initial: String, final: String): Double? {
    val i = initial.toNum(); val f = final.toNum()
    return if (i != null && f != null) f - i else null
}
private fun String.toNum(): Double? = trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()

/** The standing power factor at W-202, used until the technician types another. */
const val W202_DEFAULT_POWER_FACTOR = 0.98

data class W202FormState(
    val kind: W202MeterKind = W202MeterKind.MAIN,
    val readingDate: String = LocalDate.now().toString(),
    val initialKwh: String = "", val finalKwh: String = "",
    val initialKvah: String = "", val finalKvah: String = "",
    /** MAIN only — SOLAR's is derived and shown from [solarPowerFactor]. */
    val powerFactor: String = "",
    val remark: String = "",
    val sourceDate: String? = null,      // "carried from <date>" hint; MAIN only
    val isEditing: Boolean = false,      // this date already had a saved row
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    val kwhConsumption: Double? get() = diff(initialKwh, finalKwh)
    val kvahConsumption: Double? get() = diff(initialKvah, finalKvah)

    /**
     * Solar's power factor preview — KWH ÷ KVAH consumption, matching the server's
     * derivation. Null when either is missing or KVAH consumption is zero, so the screen
     * shows a dash instead of dividing by zero.
     */
    val solarPowerFactor: Double?
        get() {
            val kwh = kwhConsumption ?: return null
            val kvah = kvahConsumption ?: return null
            return if (kvah == 0.0) null else kwh / kvah
        }

    /** A pair is invalid only when both sides are present and Final < Initial. */
    private fun pairInvalid(initial: String, final: String): Boolean {
        val i = initial.toNum(); val f = final.toNum()
        return i != null && f != null && f < i
    }
    val kwhError: Boolean get() = pairInvalid(initialKwh, finalKwh)
    val kvahError: Boolean get() = pairInvalid(initialKvah, finalKvah)

    private val anyFilled: Boolean
        get() = listOf(initialKwh, finalKwh, initialKvah, finalKvah).any { it.isNotBlank() }

    /** Submit when nothing violates Final ≥ Initial and there's at least one value to save. */
    val canSubmit: Boolean get() = anyFilled && !kwhError && !kvahError
}

sealed interface W202FormEvent {
    data object Saved : W202FormEvent
    data class Error(val message: String) : W202FormEvent
}

@HiltViewModel
class W202ReadingFormViewModel @Inject constructor(
    private val repo: W202ReadingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val kind = W202MeterKind.fromSlug(savedStateHandle.get<String>("kind"))
    private val argDate: String? = savedStateHandle.get<String>("date")?.ifBlank { null }

    private val _state = MutableStateFlow(
        W202FormState(
            kind = kind,
            readingDate = argDate ?: LocalDate.now().toString(),
            powerFactor = if (kind.powerFactorEditable) formatUtilityNumber(W202_DEFAULT_POWER_FACTOR, 2) else "",
        )
    )
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<W202FormEvent>()
    val events = _events.asSharedFlow()

    init { loadDay(_state.value.readingDate) }

    // ── field editing ────────────────────────────────────────────────────────────
    fun onInitialKwh(v: String) = _state.update { it.copy(initialKwh = v) }
    fun onFinalKwh(v: String) = _state.update { it.copy(finalKwh = v) }
    fun onInitialKvah(v: String) = _state.update { it.copy(initialKvah = v) }
    fun onFinalKvah(v: String) = _state.update { it.copy(finalKvah = v) }
    fun onPowerFactor(v: String) = _state.update { it.copy(powerFactor = v) }
    fun onRemark(v: String) = _state.update { it.copy(remark = v) }

    fun setDate(date: String) {
        _state.update { it.copy(readingDate = date) }
        loadDay(date)
    }

    /**
     * Load a day: prefill gives the openings (carried forward for MAIN) and whether the date
     * was already saved; if it was, pull the stored row so the technician resumes exactly
     * what's on the server.
     */
    private fun loadDay(date: String) {
        viewModelScope.launch {
            when (val r = repo.prefill(kind, date)) {
                is Result.Success -> applyPrefill(r.data)
                is Result.Error -> Unit          // non-fatal: leave blanks, technician types them
                Result.Loading -> Unit
            }
            if (_state.value.isEditing) {
                when (val h = repo.list(kind, date, date)) {
                    is Result.Success -> h.data.firstOrNull()?.let(::applyRow)
                    is Result.Error -> Unit
                    Result.Loading -> Unit
                }
            }
        }
    }

    private fun applyPrefill(p: W202Prefill) = _state.update { s ->
        s.copy(
            sourceDate = p.sourceDate,
            isEditing = p.alreadySaved,
            // Carried forward for MAIN; blank for SOLAR, and blank on the very first day.
            initialKwh = p.initialKwh.fmt(),
            initialKvah = p.initialKvah.fmt(),
            powerFactor = if (kind.powerFactorEditable) {
                (p.powerFactor ?: W202_DEFAULT_POWER_FACTOR).let { formatUtilityNumber(it, 2) }
            } else "",
            // Finals/remark start blank for the day; a saved day fills them via applyRow next.
            finalKwh = "", finalKvah = "", remark = "",
        )
    }

    private fun applyRow(row: W202MeterReading) = _state.update { s ->
        s.copy(
            initialKwh = row.initialKwh.fmt(), finalKwh = row.finalKwh.fmt(),
            initialKvah = row.initialKvah.fmt(), finalKvah = row.finalKvah.fmt(),
            powerFactor = if (kind.powerFactorEditable) {
                row.powerFactor?.let { formatUtilityNumber(it, 2) } ?: s.powerFactor
            } else "",
            remark = row.remark.orEmpty(),
            isEditing = true,
        )
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit || s.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val reading = W202MeterReading(
                kind = kind,
                readingDate = s.readingDate,
                initialKwh = s.initialKwh.toNum(), finalKwh = s.finalKwh.toNum(),
                initialKvah = s.initialKvah.toNum(), finalKvah = s.finalKvah.toNum(),
                // Blank falls back to the standing value rather than saving no power factor.
                powerFactor = if (kind.powerFactorEditable) {
                    s.powerFactor.toNum() ?: W202_DEFAULT_POWER_FACTOR
                } else null,
                remark = s.remark.trim().ifBlank { null },
            )
            when (val r = repo.submit(reading)) {
                is Result.Success -> {
                    // The stored row wins over the on-screen preview: the consumptions,
                    // solar's power factor, and any opening the trigger carried forward
                    // are all decided in the database, not here.
                    applyRow(r.data)
                    _state.update { it.copy(isSubmitting = false) }
                    _events.emit(W202FormEvent.Saved)
                }
                is Result.Error -> {
                    _state.update { it.copy(isSubmitting = false, error = r.message) }
                    _events.emit(W202FormEvent.Error(r.message))
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun Double?.fmt(): String = this?.let { formatUtilityNumber(it, 3) }.orEmpty()
}
