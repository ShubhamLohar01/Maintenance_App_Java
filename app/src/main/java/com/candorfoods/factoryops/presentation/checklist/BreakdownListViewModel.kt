package com.candorfoods.factoryops.presentation.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import com.candorfoods.factoryops.util.pdf.BreakdownDocData
import com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BreakdownListItem(val flag: BreakdownFlag, val isApproved: Boolean)

data class BreakdownListUiState(
    val isLoading: Boolean = true,
    val items: List<BreakdownListItem> = emptyList(),
    val submitted: List<BreakdownRecord> = emptyList(),
    val submittedLoading: Boolean = true,
    val submittedError: String? = null,
    val isExporting: Boolean = false,
    val pdfToShare: File? = null,
    val exportError: String? = null,
)

@HiltViewModel
class BreakdownListViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
    private val checklistRepository: ChecklistRepository,
    private val pdfRenderer: ChecklistPdfRenderer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreakdownListUiState())
    val uiState: StateFlow<BreakdownListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { breakdownRepository.refreshOpenFlags("both") }
        viewModelScope.launch {
            breakdownRepository.observeAllFlags().collect { flags ->
                _uiState.update {
                    it.copy(isLoading = false, items = flags.map { f -> BreakdownListItem(f, isApproved(f)) })
                }
            }
        }
        loadSubmitted()
    }

    fun loadSubmitted() {
        viewModelScope.launch {
            _uiState.update { it.copy(submittedLoading = true, submittedError = null) }
            when (val r = checklistRepository.listBreakdowns()) {
                is Result.Success -> _uiState.update {
                    it.copy(submittedLoading = false, submitted = r.data.sortedByDescending { rec -> rec.id })
                }
                is Result.Error -> _uiState.update {
                    it.copy(submittedLoading = false, submittedError = r.message)
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { breakdownRepository.refreshOpenFlags("both") }
        loadSubmitted()
    }

    fun exportSubmitted(record: BreakdownRecord) {
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null) }
            try {
                val file = pdfRenderer.renderBreakdown(
                    BreakdownDocData("CFPLA.C4.F.06", record.verifiedBy, listOf(record.toF06Row()))
                )
                _uiState.update { it.copy(isExporting = false, pdfToShare = file) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, exportError = e.message ?: "Couldn't create PDF") }
            }
        }
    }

    fun onPdfShared() = _uiState.update { it.copy(pdfToShare = null) }
    fun onExportErrorShown() = _uiState.update { it.copy(exportError = null) }
}
