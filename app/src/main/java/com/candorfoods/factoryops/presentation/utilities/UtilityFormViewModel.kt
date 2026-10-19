package com.candorfoods.factoryops.presentation.utilities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.utilities.FieldKind
import com.candorfoods.factoryops.domain.model.utilities.UtilityPrefill
import com.candorfoods.factoryops.domain.model.utilities.UtilityRecord
import com.candorfoods.factoryops.domain.model.utilities.UtilityType
import com.candorfoods.factoryops.domain.model.utilities.canSubmitUtility
import com.candorfoods.factoryops.domain.repository.UtilitiesRepository
import com.candorfoods.factoryops.domain.usecase.utilities.computeUtility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class UtilityFormState(
    val plant: String = "A185",
    val readingDate: String = LocalDate.now().toString(),
    val inputs: Map<String, String> = emptyMap(),
    val computed: Map<String, Double?> = emptyMap(),
    val canSubmit: Boolean = false,
    val isSubmitting: Boolean = false,
    val isEditing: Boolean = false,
    val error: String? = null,
)

sealed interface UtilityFormEvent {
    data object Saved : UtilityFormEvent
    data class Error(val message: String) : UtilityFormEvent
}

@HiltViewModel
class UtilityFormViewModel @Inject constructor(
    private val repo: UtilitiesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val type: UtilityType = UtilityType.fromSlug(savedStateHandle.get<String>("util")) ?: UtilityType.DIESEL
    private val editPlant: String? = savedStateHandle.get<String>("plant")?.ifBlank { null }
    private val editDate: String? = savedStateHandle.get<String>("date")?.ifBlank { null }

    private val _state = MutableStateFlow(initialState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<UtilityFormEvent>()
    val events = _events.asSharedFlow()

    init {
        if (editPlant != null && editDate != null) loadForEdit(editPlant, editDate)
        // New reading: pull the opening fields (previous closing) + the supervisor
        // rate from the backend so the technician sees them without typing.
        else loadPrefill(_state.value.plant, _state.value.readingDate)
    }

    private fun initialState(): UtilityFormState {
        val inputs = type.inputs.associate { it.key to it.default }
        val plant = editPlant ?: "A185"
        return UtilityFormState(
            plant = plant,
            readingDate = editDate ?: LocalDate.now().toString(),
            inputs = inputs,
            computed = computeUtility(type, numericFrom(inputs)),
            canSubmit = validate(inputs, plant),
        )
    }

    fun onInputChange(key: String, value: String) = _state.update { s ->
        val inputs = s.inputs.toMutableMap().apply { this[key] = value }
        s.copy(
            inputs = inputs,
            computed = computeUtility(type, numericFrom(inputs)),
            canSubmit = validate(inputs, s.plant),
        )
    }

    fun setPlant(plant: String) {
        // Which fields are required depends on the plant, so the Submit gate has to be
        // re-evaluated here — not just when an input changes.
        _state.update { it.copy(plant = plant, canSubmit = validate(it.inputs, plant)) }
        // Opening + rate depend on plant; refetch (never in edit mode — that would
        // clobber the loaded record).
        if (!_state.value.isEditing) loadPrefill(plant, _state.value.readingDate)
    }

    fun setDate(date: String) {
        _state.update { it.copy(readingDate = date) }
        if (!_state.value.isEditing) loadPrefill(_state.value.plant, date)
    }

    /** Fetch the previous closing (→ opening fields) and the current rate, then merge them in. */
    private fun loadPrefill(plant: String, date: String) {
        viewModelScope.launch {
            when (val r = repo.prefill(type, plant, date)) {
                is Result.Success -> applyPrefill(r.data)
                is Result.Error -> Unit          // non-fatal: leave the field defaults in place
                Result.Loading -> Unit
            }
        }
    }

    /** Merge prefill: opening keys ← previous closing, rate key ← supervisor price. */
    private fun applyPrefill(p: UtilityPrefill) = _state.update { s ->
        val inputs = s.inputs.toMutableMap()
        for ((key, value) in p.openings) {
            inputs[key] = value?.let { formatUtilityNumber(it, type.decimalsFor(key)) }.orEmpty()
        }
        inputs[type.rateKey] = p.rate?.let { formatUtilityNumber(it, type.decimalsFor(type.rateKey)) }.orEmpty()
        s.copy(
            inputs = inputs,
            computed = computeUtility(type, numericFrom(inputs)),
            canSubmit = validate(inputs, s.plant),
        )
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit || s.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val numbers = numericFrom(s.inputs) + s.computed
            val texts = type.inputs
                .filter { it.kind == FieldKind.TEXT }
                .associate { it.key to (s.inputs[it.key] ?: "") }
            val record = UtilityRecord(s.plant, s.readingDate, numbers, texts)
            when (val r = repo.submit(type, record)) {
                is Result.Success -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _events.emit(UtilityFormEvent.Saved)
                }
                is Result.Error -> {
                    _state.update { it.copy(isSubmitting = false, error = r.message) }
                    _events.emit(UtilityFormEvent.Error(r.message))
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun loadForEdit(plant: String, date: String) {
        _state.update { it.copy(isEditing = true) }
        viewModelScope.launch {
            when (val r = repo.list(type, plant)) {
                is Result.Success -> r.data.firstOrNull { it.readingDate == date }?.let(::applyRecord)
                is Result.Error -> _state.update { it.copy(error = r.message) }
                Result.Loading -> Unit
            }
        }
    }

    /** Populate the form from a stored record (edit mode). */
    private fun applyRecord(rec: UtilityRecord) = _state.update { s ->
        val inputs = type.inputs.associate { f ->
            f.key to when (f.kind) {
                FieldKind.NUMBER -> formatUtilityNumber(rec.numbers[f.key], f.decimals)
                FieldKind.TEXT -> rec.texts[f.key].orEmpty()
            }
        }
        val plant = rec.plant.ifBlank { s.plant }
        s.copy(
            plant = plant,
            readingDate = rec.readingDate.ifBlank { s.readingDate },
            inputs = inputs,
            computed = computeUtility(type, numericFrom(inputs)),
            canSubmit = validate(inputs, plant),
        )
    }

    /** Parse the numeric input fields; blank or invalid → null. */
    private fun numericFrom(inputs: Map<String, String>): Map<String, Double?> =
        type.inputs
            .filter { it.kind == FieldKind.NUMBER }
            .associate { it.key to inputs[it.key]?.takeIf { v -> v.isNotBlank() }?.toDoubleOrNull() }

    /**
     * Submit gate. Delegates to [canSubmitUtility], which is plant-aware: W-202's DG
     * has no kWh meter, so its kWh readings are optional there (see UtilityValidation).
     * Every caller must therefore pass the plant currently selected in the form.
     */
    private fun validate(inputs: Map<String, String>, plant: String): Boolean =
        canSubmitUtility(type, plant, numericFrom(inputs))
}
