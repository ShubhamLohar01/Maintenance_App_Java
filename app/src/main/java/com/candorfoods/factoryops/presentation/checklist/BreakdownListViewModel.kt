package com.candorfoods.factoryops.presentation.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.repository.BreakdownF06DraftRepository
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BreakdownListItem(
    val flag: BreakdownFlag,
    val isApproved: Boolean,
    val isCompleted: Boolean,
)

data class BreakdownListUiState(
    val isLoading: Boolean = true,
    val items: List<BreakdownListItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class BreakdownListViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
    private val draftRepository: BreakdownF06DraftRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreakdownListUiState())
    val uiState: StateFlow<BreakdownListUiState> = _uiState.asStateFlow()

    init {
        // Best-effort pull of the open pool so freshly-raised breakdowns appear.
        viewModelScope.launch { breakdownRepository.refreshOpenFlags("both") }
        viewModelScope.launch {
            combine(
                breakdownRepository.observeAllFlags(),
                draftRepository.observeSavedIds(),
            ) { flags, savedIds ->
                flags.map { f -> BreakdownListItem(f, isApproved(f), f.id in savedIds) }
            }.collect { items ->
                _uiState.update { it.copy(isLoading = false, items = items) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { breakdownRepository.refreshOpenFlags("both") }
    }
}
