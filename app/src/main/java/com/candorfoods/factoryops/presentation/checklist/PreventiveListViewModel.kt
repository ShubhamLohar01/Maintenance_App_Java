package com.candorfoods.factoryops.presentation.checklist

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

data class PreventiveListUiState(
    val isLoading: Boolean = true,
    val records: List<ChecklistRecordSummary> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class PreventiveListViewModel @Inject constructor(
    private val checklistRepository: ChecklistRepository,
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
}
