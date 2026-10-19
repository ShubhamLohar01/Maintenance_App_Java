package com.candorfoods.factoryops.presentation.qc.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import com.candorfoods.factoryops.presentation.checklist.toF06Row
import com.candorfoods.factoryops.presentation.checklist.BreakdownFormSpec
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

data class QcBreakdownHistoryUiState(
    val isLoading: Boolean = true,
    val records: List<BreakdownRecord> = emptyList(),
    val error: String? = null,
    val isExporting: Boolean = false,
    val pdfToShare: File? = null,
    val exportError: String? = null,
)

/**
 * Read-only list of submitted F.06 breakdown records for QC, with per-record PDF export.
 *
 * The PDF is rendered on-device from the fetched record (same renderer the technician
 * screens use) — there is no stored file to download from the server.
 */
@HiltViewModel
class QcBreakdownHistoryViewModel @Inject constructor(
    private val checklistRepository: ChecklistRepository,
    private val pdfRenderer: ChecklistPdfRenderer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QcBreakdownHistoryUiState())
    val uiState: StateFlow<QcBreakdownHistoryUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = checklistRepository.listBreakdowns()) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, records = r.data.sortedByDescending { rec -> rec.id })
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                is Result.Loading -> Unit
            }
        }
    }

    fun exportPdf(record: BreakdownRecord) {
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null) }
            try {
                val file = pdfRenderer.renderBreakdown(
                    BreakdownDocData(
                        docNo = record.docNo,
                        verifiedBy = record.verifiedBy,
                        rows = listOf(record.toF06Row()),
                        // A-185 rows render F.61's narrower column set.
                        spec = BreakdownFormSpec.forDocNo(record.docNo),
                    )
                )
                _uiState.update { it.copy(isExporting = false, pdfToShare = file) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isExporting = false, exportError = e.message ?: "Couldn't create PDF")
                }
            }
        }
    }

    fun onPdfShared() = _uiState.update { it.copy(pdfToShare = null) }
    fun onExportErrorShown() = _uiState.update { it.copy(exportError = null) }
}
