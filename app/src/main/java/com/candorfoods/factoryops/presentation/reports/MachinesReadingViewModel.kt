package com.candorfoods.factoryops.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.reports.MachineReadingRow
import com.candorfoods.factoryops.domain.repository.ReportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MachinesReadingUiState(
    val isLoading: Boolean = true,
    val plant: String? = null,   // null = Both
    val from: String? = null,    // null = server default (30 days ago)
    val to: String? = null,      // null = server default (today)
    val rows: List<MachineReadingRow> = emptyList(),
    val error: String? = null,
)

/** "Machines Reading" (Supervisor Reports) — mt_machine_daily_kwh, filterable by
 * plant and date range, newest first. */
@HiltViewModel
class MachinesReadingViewModel @Inject constructor(
    private val repository: ReportsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MachinesReadingUiState())
    val uiState: StateFlow<MachinesReadingUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun setPlant(plant: String?) {
        _uiState.update { it.copy(plant = plant) }
        load()
    }

    fun setDateRange(from: String?, to: String?) {
        _uiState.update { it.copy(from = from, to = to) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = repository.machinesReading(s.plant, s.from, s.to)) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, rows = r.data) }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading -> Unit
            }
        }
    }
}
