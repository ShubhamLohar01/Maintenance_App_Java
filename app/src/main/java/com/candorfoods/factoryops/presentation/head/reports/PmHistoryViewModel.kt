package com.candorfoods.factoryops.presentation.head.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class PmHistoryUiState(
    val isLoading: Boolean = true,
    val status: String? = null,  // PmWoStatus name, e.g. "CLOSED"; null = All
    val from: String? = null,    // ISO yyyy-MM-dd; null = not yet resolved
    val to: String? = null,
    val rows: List<PmWorkOrder> = emptyList(),
    val error: String? = null,
)

/** "PM — History" (Head Reports) — every PM work order, live or closed, filterable by
 *  status (server-side) and date range (client-side — the API has no date param). */
@HiltViewModel
class PmHistoryViewModel @Inject constructor(
    private val repository: PmWorkOrderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PmHistoryUiState())
    val uiState: StateFlow<PmHistoryUiState> = _uiState.asStateFlow()

    init { load() }

    fun setStatus(status: String?) { _uiState.update { it.copy(status = status) }; load() }
    fun setDateRange(from: String?, to: String?) { _uiState.update { it.copy(from = from, to = to) }; load() }

    fun load() {
        viewModelScope.launch {
            val s = _uiState.value
            val from = s.from ?: LocalDate.now().minusDays(30).toString()
            val to = s.to ?: LocalDate.now().toString()
            // Write the resolved (non-null) dates back into state immediately so the
            // Screen always displays exactly what was queried — a single source of
            // truth instead of the Screen re-deriving its own "default to 30 days" copy.
            _uiState.update { it.copy(isLoading = true, error = null, from = from, to = to) }
            when (val r = repository.history(s.status)) {
                is Result.Success -> _uiState.update {
                    val window = dayRangeMillis(from, to)
                    it.copy(isLoading = false, rows = r.data.filter { wo -> wo.scheduledDate in window })
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading -> Unit
            }
        }
    }

    private fun dayRangeMillis(from: String, to: String): LongRange {
        val zone = ZoneId.systemDefault()
        val startMillis = LocalDate.parse(from).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = LocalDate.parse(to).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return startMillis..endMillis
    }
}
