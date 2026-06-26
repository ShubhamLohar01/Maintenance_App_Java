package com.candorfoods.factoryops.presentation.checklist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.ChecklistRecordDetail
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import com.candorfoods.factoryops.navigation.Screen
import com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PreventiveDetailUiState(
    val isLoading: Boolean = true,
    val record: ChecklistRecordDetail? = null,
    val error: String? = null,
    val isExporting: Boolean = false,
    val pdfToShare: File? = null,
    val exportError: String? = null,
)

@HiltViewModel
class PreventiveDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val checklistRepository: ChecklistRepository,
    private val pdfRenderer: ChecklistPdfRenderer,
) : ViewModel() {

    private val id: Int = savedStateHandle.get<String>(Screen.PreventiveDetail.ARG)?.toIntOrNull() ?: -1

    private val _uiState = MutableStateFlow(PreventiveDetailUiState())
    val uiState: StateFlow<PreventiveDetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = checklistRepository.detail(id)) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, record = r.data) }
                is Result.Error   -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                is Result.Loading -> Unit
            }
        }
    }

    fun exportPdf() {
        val record = _uiState.value.record ?: return
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null) }
            try {
                val file = pdfRenderer.renderPreventive(record)
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
