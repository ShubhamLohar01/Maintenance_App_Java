package com.candorfoods.factoryops.presentation.checklist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.repository.BreakdownF06DraftRepository
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.navigation.Screen
import com.candorfoods.factoryops.util.pdf.BreakdownDocData
import com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BreakdownCompleteUiState(
    val isLoading: Boolean = true,
    val flag: BreakdownFlag? = null,
    val fields: F06Fields = F06Fields(),
    val isApproved: Boolean = false,
    val isSaved: Boolean = false,
    val isExporting: Boolean = false,
    val pdfToShare: File? = null,
    val exportError: String? = null,
    val error: String? = null,
)

@HiltViewModel
class BreakdownCompleteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val breakdownRepository: BreakdownRepository,
    private val draftRepository: BreakdownF06DraftRepository,
    private val pdfRenderer: ChecklistPdfRenderer,
) : ViewModel() {

    private val id: String = savedStateHandle.get<String>(Screen.BreakdownComplete.ARG).orEmpty()

    private val _uiState = MutableStateFlow(BreakdownCompleteUiState())
    val uiState: StateFlow<BreakdownCompleteUiState> = _uiState.asStateFlow()

    private var seeded = false

    init {
        viewModelScope.launch {
            combine(
                breakdownRepository.observeFlagById(id),
                draftRepository.observeDraft(id),
            ) { flag, draft -> flag to draft }
                .collect { (flag, draft) ->
                    if (flag == null) {
                        _uiState.update { it.copy(isLoading = false, error = "Breakdown not found") }
                        return@collect
                    }
                    if (!seeded) {
                        seeded = true
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                flag = flag,
                                fields = draft?.toFields() ?: defaultF06Fields(flag),
                                isApproved = isApproved(flag),
                                isSaved = draft != null,
                            )
                        }
                    } else {
                        // Preserve in-progress edits; only refresh flag-derived bits.
                        _uiState.update { it.copy(flag = flag, isApproved = isApproved(flag)) }
                    }
                }
        }
    }

    fun updateFields(fields: F06Fields) = _uiState.update { it.copy(fields = fields) }

    fun save() {
        val s = _uiState.value
        val flag = s.flag ?: return
        if (!s.isApproved) return
        viewModelScope.launch {
            draftRepository.saveDraft(s.fields.toDraft(flag.id, System.currentTimeMillis()))
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun exportPdf() {
        val s = _uiState.value
        val flag = s.flag ?: return
        if (s.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null) }
            try {
                val row = buildF06Row(flag, s.fields)
                val file = pdfRenderer.renderBreakdown(
                    BreakdownDocData(
                        docNo = "CFPLA.C4.F.06",
                        verifiedBy = s.fields.verifiedBy.trim(),
                        rows = listOf(row),
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
