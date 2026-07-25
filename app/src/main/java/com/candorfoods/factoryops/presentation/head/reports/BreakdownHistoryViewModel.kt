package com.candorfoods.factoryops.presentation.head.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BreakdownHistoryUiState(
    val isLoading: Boolean = true,
    val plant: String? = null,   // hyphenated UI form, e.g. "A-185"; null = Both
    val status: String? = null,  // "OPEN" / "CLOSED"; null = All
    val from: String? = null,    // null = server default
    val to: String? = null,      // null = server default
    val rows: List<BreakdownFlag> = emptyList(),
    val error: String? = null,
)

/** "Breakdown — History" (Head Reports) — every breakdown in a date range regardless
 * of status, filterable by plant and status. */
@HiltViewModel
class BreakdownHistoryViewModel @Inject constructor(
    private val repository: BreakdownRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreakdownHistoryUiState())
    val uiState: StateFlow<BreakdownHistoryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun setPlant(plant: String?) {
        _uiState.update { it.copy(plant = plant) }
        load()
    }

    fun setStatus(status: String?) {
        _uiState.update { it.copy(status = status) }
        load()
    }

    fun setDateRange(from: String?, to: String?) {
        _uiState.update { it.copy(from = from, to = to) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val s = _uiState.value
            val from = s.from ?: LocalDate.now().minusDays(30).toString()
            val to = s.to ?: LocalDate.now().toString()
            // Write the resolved (non-null) dates back into state immediately so the
            // Screen always displays exactly what was queried — a single source of
            // truth instead of the Screen re-deriving its own "default to 30 days" copy.
            _uiState.update { it.copy(isLoading = true, error = null, from = from, to = to) }
            when (val r = repository.breakdownHistory(from, to, s.plant.toBackendPlantId(), s.status)) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, rows = r.data) }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading -> Unit
            }
        }
    }
}
