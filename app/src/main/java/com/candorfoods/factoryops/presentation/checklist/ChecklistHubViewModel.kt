package com.candorfoods.factoryops.presentation.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChecklistHubUiState(
    val preventiveTypes: List<ChecklistType> = listOf(ChecklistType.MONTHLY, ChecklistType.QUARTERLY),
    val breakdownSpec: BreakdownFormSpec = BreakdownFormSpec.W202,
) {
    /**
     * The plant's own document numbers: "Monthly (50a) / Quarterly (50b)" for W-202's two
     * documents, "Quarterly / Monthly (41)" for A-185's single combined one.
     */
    val preventiveSubtitle: String
        get() {
            val short = { t: ChecklistType -> t.docNo.substringAfterLast('.') }
            val shared = preventiveTypes.map(short).distinct().singleOrNull()
            return if (shared != null) {
                preventiveTypes.joinToString(" / ") { it.label } + " ($shared)"
            } else {
                preventiveTypes.joinToString(" / ") { "${it.label} (${short(it)})" }
            }
        }

    val breakdownSubtitle: String
        get() = "Breakdown maintenance record (${breakdownSpec.docNo.substringAfterLast('.')})"
}

/** Names the two documents after the signed-in technician's own plant. */
@HiltViewModel
class ChecklistHubViewModel @Inject constructor(
    getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChecklistHubUiState())
    val uiState: StateFlow<ChecklistHubUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val plantId = getCurrentUser().firstOrNull()?.plantId
            _uiState.update {
                it.copy(
                    preventiveTypes = ChecklistType.forPlant(plantId),
                    breakdownSpec = BreakdownFormSpec.forPlant(plantId),
                )
            }
        }
    }
}
