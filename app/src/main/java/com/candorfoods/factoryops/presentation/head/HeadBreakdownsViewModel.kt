package com.candorfoods.factoryops.presentation.head

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HeadBreakdownsUiState(
    val isLoading: Boolean = true,
    val open: List<BreakdownFlag> = emptyList(),
    val resolved: List<BreakdownFlag> = emptyList(),
    val plantId: String = "",
)

@HiltViewModel
class HeadBreakdownsViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
    getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HeadBreakdownsUiState())
    val uiState: StateFlow<HeadBreakdownsUiState> = _uiState.asStateFlow()

    init {
        // Clear any leftover mock sample flags (real-backend builds only).
        viewModelScope.launch { breakdownRepository.purgeMockSeedFlags() }
        // Pull open breakdowns from the backend so asset-register flags (raised on any
        // device) land in Room; the observers below then render them.
        viewModelScope.launch { breakdownRepository.refreshOpenFlags("both") }
        viewModelScope.launch {
            getCurrentUser().collectLatest { user ->
                val plant = user?.plantId.orEmpty()
                if (plant.isBlank()) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@collectLatest
                }
                val since = System.currentTimeMillis() - SEVEN_DAYS
                combine(
                    // No legacy `machines`-table join, so asset-register flags are included.
                    breakdownRepository.observeAllOpenFlags(),
                    breakdownRepository.observeResolvedFlagsByPlant(plant, since),
                ) { open, resolved -> open to resolved }
                    .collectLatest { (open, resolved) ->
                        _uiState.update {
                            it.copy(isLoading = false, open = open, resolved = resolved, plantId = plant)
                        }
                    }
            }
        }
    }

    companion object {
        private const val SEVEN_DAYS = 7L * 24 * 60 * 60 * 1000
    }
}
