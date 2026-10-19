package com.candorfoods.factoryops.presentation.a185

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.a185.A185Prefill
import com.candorfoods.factoryops.domain.model.a185.A185Reading
import com.candorfoods.factoryops.domain.repository.A185ReadingsRepository
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

/** One editable section (HT / Cold / UV): the live diff drives the on-screen consumption. */
private fun diff(initial: String, final: String): Double? {
    val i = initial.toNum(); val f = final.toNum()
    return if (i != null && f != null) f - i else null
}
private fun String.toNum(): Double? = trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()

data class A185FormState(
    val readingDate: String = LocalDate.now().toString(),
    val htInitial: String = "", val htFinal: String = "",
    val coldInitial: String = "", val coldFinal: String = "",
    val uvInitial: String = "", val uvFinal: String = "",
    val remark: String = "",
    val htPowerFactor: Double = 0.99,
    val sourceDate: String? = null,      // "carried from <date>" hint
    val isEditing: Boolean = false,      // this date already had a saved row
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    val htConsumption: Double? get() = diff(htInitial, htFinal)
    val coldConsumption: Double? get() = diff(coldInitial, coldFinal)
    val uvConsumption: Double? get() = diff(uvInitial, uvFinal)

    /** A section is invalid only when both sides are present and Final < Initial. */
    private fun sectionInvalid(initial: String, final: String): Boolean {
        val i = initial.toNum(); val f = final.toNum()
        return i != null && f != null && f < i
    }
    val htError: Boolean get() = sectionInvalid(htInitial, htFinal)
    val coldError: Boolean get() = sectionInvalid(coldInitial, coldFinal)
    val uvError: Boolean get() = sectionInvalid(uvInitial, uvFinal)

    private val anyFilled: Boolean
        get() = listOf(htInitial, htFinal, coldInitial, coldFinal, uvInitial, uvFinal)
            .any { it.isNotBlank() }

    /** Submit when nothing violates Final ≥ Initial and there's at least one value to save. */
    val canSubmit: Boolean get() = anyFilled && !htError && !coldError && !uvError
}

sealed interface A185FormEvent {
    data object Saved : A185FormEvent
    data class Error(val message: String) : A185FormEvent
}

@HiltViewModel
class A185ReadingFormViewModel @Inject constructor(
    private val repo: A185ReadingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val argDate: String? = savedStateHandle.get<String>("date")?.ifBlank { null }

    private val _state = MutableStateFlow(A185FormState(readingDate = argDate ?: LocalDate.now().toString()))
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<A185FormEvent>()
    val events = _events.asSharedFlow()

    init { loadDay(_state.value.readingDate) }

    // ── field editing ────────────────────────────────────────────────────────────
    fun onHtInitial(v: String) = _state.update { it.copy(htInitial = v) }
    fun onHtFinal(v: String) = _state.update { it.copy(htFinal = v) }
    fun onColdInitial(v: String) = _state.update { it.copy(coldInitial = v) }
    fun onColdFinal(v: String) = _state.update { it.copy(coldFinal = v) }
    fun onUvInitial(v: String) = _state.update { it.copy(uvInitial = v) }
    fun onUvFinal(v: String) = _state.update { it.copy(uvFinal = v) }
    fun onRemark(v: String) = _state.update { it.copy(remark = v) }

    fun setDate(date: String) {
        _state.update { it.copy(readingDate = date) }
        loadDay(date)
    }

    /**
     * Load a day: prefill gives the fixed PF + Initial openings (and whether the date was
     * already saved); if it was, pull the stored row so the technician resumes exactly
     * what's on the server (Finals + remark).
     */
    private fun loadDay(date: String) {
        viewModelScope.launch {
            when (val r = repo.prefill(date)) {
                is Result.Success -> applyPrefill(r.data)
                is Result.Error -> Unit          // non-fatal: leave blanks, technician types them
                Result.Loading -> Unit
            }
            if (_state.value.isEditing) {
                when (val h = repo.list(date, date)) {
                    is Result.Success -> h.data.firstOrNull()?.let(::applyRow)
                    is Result.Error -> Unit
                    Result.Loading -> Unit
                }
            }
        }
    }

    private fun applyPrefill(p: A185Prefill) = _state.update { s ->
        s.copy(
            htPowerFactor = p.htPowerFactor,
            sourceDate = p.sourceDate,
            isEditing = p.alreadySaved,
            // Pre-fill Initials from the previous day's Finals (blank when there's no earlier day).
            htInitial = p.htInitial.fmt(),
            coldInitial = p.coldInitial.fmt(),
            uvInitial = p.uvInitial.fmt(),
            // Finals/remark start blank for the day; a saved day fills them via applyRow next.
            htFinal = "", coldFinal = "", uvFinal = "", remark = "",
        )
    }

    private fun applyRow(row: A185Reading) = _state.update { s ->
        s.copy(
            htInitial = row.htInitial.fmt(), htFinal = row.htFinal.fmt(),
            coldInitial = row.coldInitial.fmt(), coldFinal = row.coldFinal.fmt(),
            uvInitial = row.uvInitial.fmt(), uvFinal = row.uvFinal.fmt(),
            remark = row.remark.orEmpty(),
            htPowerFactor = row.htPowerFactor ?: s.htPowerFactor,
            isEditing = true,
        )
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit || s.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val reading = A185Reading(
                readingDate = s.readingDate,
                htInitial = s.htInitial.toNum(), htFinal = s.htFinal.toNum(),
                coldInitial = s.coldInitial.toNum(), coldFinal = s.coldFinal.toNum(),
                uvInitial = s.uvInitial.toNum(), uvFinal = s.uvFinal.toNum(),
                remark = s.remark.trim().ifBlank { null },
            )
            when (val r = repo.submit(reading)) {
                is Result.Success -> {
                    _state.update { it.copy(isSubmitting = false, isEditing = true) }
                    _events.emit(A185FormEvent.Saved)
                }
                is Result.Error -> {
                    _state.update { it.copy(isSubmitting = false, error = r.message) }
                    _events.emit(A185FormEvent.Error(r.message))
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun Double?.fmt(): String = this?.let { formatUtilityNumber(it, 3) }.orEmpty()
}
