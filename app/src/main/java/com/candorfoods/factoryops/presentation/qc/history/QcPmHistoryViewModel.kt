package com.candorfoods.factoryops.presentation.qc.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.ChecklistRecordSummary
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QcPmHistoryUiState(
    val isLoading: Boolean = true,
    val records: List<ChecklistRecordSummary> = emptyList(),
    val error: String? = null,
)

/**
 * Read-only list of PM checklists for QC — both SUBMITTED and DRAFT.
 *
 * Passes no status filter so the backend returns every status; draft rows are labelled
 * in the UI. View + PDF download work for any record.
 */
@HiltViewModel
class QcPmHistoryViewModel @Inject constructor(
    private val checklistRepository: ChecklistRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QcPmHistoryUiState())
    val uiState: StateFlow<QcPmHistoryUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = checklistRepository.list()) {   // no status filter → submitted + drafts
                is Result.Success -> _uiState.update { it.copy(isLoading = false, records = r.data) }
                is Result.Error   -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                is Result.Loading -> Unit
            }
        }
    }
}
