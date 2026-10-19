package com.candorfoods.factoryops.presentation.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.ReportsRepository
import com.candorfoods.factoryops.util.pdf.PdfDownloader
import com.candorfoods.factoryops.util.pdf.SummaryPdfRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SummaryReportUiState(
    val from: String = LocalDate.now().minusDays(30).toString(),
    val to: String = LocalDate.now().toString(),
    val isGenerating: Boolean = false,
    /** One-shot message for the snackbar — cleared by [messageShown]. */
    val message: String? = null,
)

/** Backs the "Download Summary Report" card on both Reports hubs. */
@HiltViewModel
class SummaryReportViewModel @Inject constructor(
    private val repository: ReportsRepository,
    private val renderer: SummaryPdfRenderer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryReportUiState())
    val uiState: StateFlow<SummaryReportUiState> = _uiState.asStateFlow()

    fun setFrom(iso: String) = _uiState.update { it.copy(from = iso) }
    fun setTo(iso: String) = _uiState.update { it.copy(to = iso) }
    fun messageShown() = _uiState.update { it.copy(message = null) }

    fun download(context: Context) {
        val s = _uiState.value
        if (s.isGenerating) return
        if (s.from > s.to) {
            _uiState.update { it.copy(message = "\"From\" must be on or before \"To\"") }
            return
        }
        _uiState.update { it.copy(isGenerating = true) }
        viewModelScope.launch {
            val msg = when (val r = repository.summaryReport(s.from, s.to, null)) {
                is Result.Success -> runCatching {
                    PdfDownloader.downloadAndOpen(context, renderer.render(r.data))
                }.getOrElse { "Couldn't build the PDF: ${it.message}" }
                is Result.Error -> r.message
                else -> "Couldn't load the report"
            }
            _uiState.update { it.copy(isGenerating = false, message = msg) }
        }
    }
}
