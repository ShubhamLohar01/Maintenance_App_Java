package com.candorfoods.factoryops.presentation.spareparts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.spareparts.SparePartLogEntry
import com.candorfoods.factoryops.domain.repository.SparePartsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SparePartHistoryUiState(
    val isLoading: Boolean = true,
    val entries: List<SparePartLogEntry> = emptyList(),
    val error: String? = null,
)

/** Read-only view of one part's mt_spareparts_log rows, newest first. */
@HiltViewModel
class SparePartHistoryViewModel @Inject constructor(
    private val repository: SparePartsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val partId: Int = savedStateHandle.get<Int>("partId") ?: 0

    private val _uiState = MutableStateFlow(SparePartHistoryUiState())
    val uiState: StateFlow<SparePartHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = repository.history(partId)) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, entries = r.data) }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading -> Unit
            }
        }
    }
}
