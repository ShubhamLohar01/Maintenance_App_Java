package com.candorfoods.factoryops.presentation.utilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.utilities.UTILITY_RATE_KEYS
import com.candorfoods.factoryops.domain.repository.UtilitiesRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UtilityRatesState(
    val plant: String = "A185",
    /** rate key ("diesel_rate", …) → the editable string in its field. */
    val inputs: Map<String, String> = UTILITY_RATE_KEYS.associateWith { "" },
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    /** Only SUPERVISOR/HEAD/ADMIN may edit; anyone else sees the values read-only. */
    val canEdit: Boolean = false,
    val setBy: String? = null,
    val setAt: String? = null,
    val error: String? = null,
)

sealed interface UtilityRatesEvent {
    data object Saved : UtilityRatesEvent
    data class Error(val message: String) : UtilityRatesEvent
}

@HiltViewModel
class UtilityRatesViewModel @Inject constructor(
    private val repo: UtilitiesRepository,
    getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(UtilityRatesState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<UtilityRatesEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            getCurrentUser().collect { user ->
                _state.update { it.copy(canEdit = user?.role?.let { r -> r in EDITOR_ROLES } ?: false) }
            }
        }
        load(_state.value.plant)
    }

    fun setPlant(plant: String) {
        _state.update { it.copy(plant = plant) }
        load(plant)
    }

    private fun load(plant: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.rates(plant)) {
                is Result.Success -> _state.update { s ->
                    s.copy(
                        isLoading = false,
                        inputs = UTILITY_RATE_KEYS.associateWith { key ->
                            r.data.rates[key]?.let { formatUtilityNumber(it, 2) }.orEmpty()
                        },
                        setBy = r.data.setBy,
                        setAt = r.data.setAt,
                    )
                }
                is Result.Error -> _state.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun onRateChange(key: String, value: String) = _state.update { s ->
        s.copy(inputs = s.inputs.toMutableMap().apply { this[key] = value })
    }

    fun save() {
        val s = _state.value
        if (!s.canEdit || s.isSubmitting) return
        val rates = UTILITY_RATE_KEYS.associateWith { key ->
            s.inputs[key]?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.setRates(s.plant, rates)) {
                is Result.Success -> {
                    _state.update { it.copy(isSubmitting = false, setBy = r.data.setBy, setAt = r.data.setAt) }
                    _events.emit(UtilityRatesEvent.Saved)
                }
                is Result.Error -> {
                    _state.update { it.copy(isSubmitting = false, error = r.message) }
                    _events.emit(UtilityRatesEvent.Error(r.message))
                }
                Result.Loading -> Unit
            }
        }
    }

    private companion object {
        val EDITOR_ROLES = setOf(UserRole.SUPERVISOR, UserRole.HEAD, UserRole.ADMIN)
    }
}
