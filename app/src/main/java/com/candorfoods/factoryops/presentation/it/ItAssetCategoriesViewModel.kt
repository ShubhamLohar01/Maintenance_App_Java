package com.candorfoods.factoryops.presentation.it

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.ItAssetCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.matches
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetMachineMasterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItAssetCategoriesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val rows: List<MtMachine> = emptyList(),
) {
    /** How many register rows belong to [category] — the count on its card. */
    fun countOf(category: ItAssetCategory): Int = rows.count { category.matches(it) }
}

/**
 * IT Assets chooser: loads the asset register once and counts each IT category.
 * The full register is fetched (not one call per category) because GET /mt-machines
 * filters on an exact category string, and the counts must survive casing variants.
 */
@HiltViewModel
class ItAssetCategoriesViewModel @Inject constructor(
    private val getMachineMaster: GetMachineMasterUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItAssetCategoriesUiState())
    val uiState: StateFlow<ItAssetCategoriesUiState> = _uiState.asStateFlow()

    init { load() }

    fun onRetry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = getMachineMaster()) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, rows = r.data, error = null)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = r.message)
                }
                is Result.Loading -> _uiState.update { it.copy(isLoading = true) }
            }
        }
    }
}
