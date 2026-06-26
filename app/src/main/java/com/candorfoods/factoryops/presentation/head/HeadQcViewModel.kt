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

data class HeadQcUiState(
    val isLoading: Boolean = true,
    val awaiting: List<BreakdownFlag> = emptyList(),
    val decided: List<BreakdownFlag> = emptyList(),
    val plantId: String = "",
)

@HiltViewModel
class HeadQcViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
    getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HeadQcUiState())
    val uiState: StateFlow<HeadQcUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { breakdownRepository.purgeMockSeedFlags() }
        viewModelScope.launch {
            getCurrentUser().collectLatest { user ->
                val plant = user?.plantId.orEmpty()
                if (plant.isBlank()) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@collectLatest
                }
                val since = System.currentTimeMillis() - SEVEN_DAYS
                combine(
                    breakdownRepository.observePendingQcByPlant(plant),
                    breakdownRepository.observeResolvedFlagsByPlant(plant, since),
                ) { awaiting, resolved ->
                    awaiting to resolved.filter { it.qcStatus != null && it.qcDecidedAt != null }
                }.collectLatest { (awaiting, decided) ->
                    _uiState.update {
                        it.copy(isLoading = false, awaiting = awaiting, decided = decided, plantId = plant)
                    }
                }
            }
        }
    }

    companion object {
        private const val SEVEN_DAYS = 7L * 24 * 60 * 60 * 1000
    }
}
