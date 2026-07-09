package com.candorfoods.factoryops.presentation.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.ChecklistRecordSummary
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PreventiveListUiState(
    val isLoading: Boolean = true,
    val records: List<ChecklistRecordSummary> = emptyList(),
    val error: String? = null,
    // PDF export (per-row download): which record is rendering, and the one-shot result.
    val exportingId: Int? = null,
    val pdfToShare: File? = null,
    val exportError: String? = null,
)

@HiltViewModel
class PreventiveListViewModel @Inject constructor(
    private val checklistRepository: ChecklistRepository,
    private val pdfRenderer: ChecklistPdfRenderer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreventiveListUiState())
    val uiState: StateFlow<PreventiveListUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = checklistRepository.list()) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, records = r.data, error = null)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = r.message)
                }
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Fetch the full record, render the controlled-copy PDF (50a/50b format), and expose the
     * file for the screen to hand to the share sheet. One export at a time.
     */
    fun exportPdf(id: Int) {
        if (_uiState.value.exportingId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(exportingId = id, exportError = null) }
            when (val r = checklistRepository.detail(id)) {
                is Result.Success -> {
                    try {
                        val file = pdfRenderer.renderPreventive(r.data)
                        _uiState.update { it.copy(exportingId = null, pdfToShare = file) }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(exportingId = null, exportError = e.message ?: "Couldn't create PDF")
                        }
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(exportingId = null, exportError = r.message)
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun onPdfShared() = _uiState.update { it.copy(pdfToShare = null) }
    fun onExportErrorShown() = _uiState.update { it.copy(exportError = null) }
}
