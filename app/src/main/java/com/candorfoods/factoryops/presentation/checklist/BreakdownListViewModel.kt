package com.candorfoods.factoryops.presentation.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.util.pdf.BreakdownDocData
import com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
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
    private val getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreakdownListUiState())
    val uiState: StateFlow<BreakdownListUiState> = _uiState.asStateFlow()

    /** The signed-in technician's plant — they see only their own plant's breakdowns. */
    private var plant: String = ChecklistType.DEFAULT_PLANT

    init {
        viewModelScope.launch {
            plant = ChecklistType.plantOf(getCurrentUser().firstOrNull()?.plantId)
            // Pull only this plant's open flags, so the device stops syncing the other's.
            breakdownRepository.refreshOpenFlags(plant)
            loadSubmitted()
        }
        viewModelScope.launch {
            breakdownRepository.observeAllFlags().collect { flags ->
                // Room still holds whatever earlier "both" syncs stored, so filter on read.
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = flags.filter(::isOwnPlant)
                            .map { f -> BreakdownListItem(f, isApproved(f)) },
                    )
                }
            }
        }
    }

    /**
     * A flag belongs to this technician when its building resolves to their plant.
     * A flag with no building can't be attributed, so it stays visible rather than
     * quietly hiding work someone still has to do.
     */
    private fun isOwnPlant(flag: BreakdownFlag): Boolean {
        val building = flag.building?.trim().orEmpty()
        return building.isEmpty() || ChecklistType.plantOf(building) == plant
    }

    fun loadSubmitted() {
        viewModelScope.launch {
            _uiState.update { it.copy(submittedLoading = true, submittedError = null) }
            when (val r = checklistRepository.listBreakdowns()) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        submittedLoading = false,
                        // The endpoint returns both plants' sheets; doc_no says whose.
                        submitted = r.data
                            .filter { rec -> BreakdownFormSpec.forDocNo(rec.docNo).plant == plant }
                            .sortedByDescending { rec -> rec.id },
                    )
                }
                is Result.Error -> _uiState.update {
                    it.copy(submittedLoading = false, submittedError = r.message)
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            breakdownRepository.refreshOpenFlags(plant)
            loadSubmitted()
        }
    }

    fun exportSubmitted(record: BreakdownRecord) {
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
                _uiState.update { it.copy(isExporting = false, exportError = e.message ?: "Couldn't create PDF") }
            }
        }
    }

    fun onPdfShared() = _uiState.update { it.copy(pdfToShare = null) }
    fun onExportErrorShown() = _uiState.update { it.copy(exportError = null) }
}
