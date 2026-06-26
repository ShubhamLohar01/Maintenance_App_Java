package com.candorfoods.factoryops.presentation.machine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.candorfoods.factoryops.domain.model.ActiveRun
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.ProductionRun
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.EnergyRepository
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.domain.usecase.energy.GetActiveRunsUseCase
import com.candorfoods.factoryops.domain.usecase.energy.GetTodaysRunsUseCase
import com.candorfoods.factoryops.domain.usecase.energy.StartProductionRunUseCase
import com.candorfoods.factoryops.domain.usecase.energy.StopProductionRunUseCase
import com.candorfoods.factoryops.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MachineDetailUiState(
    val asset: MtMachine?               = null,
    val activeRun: ProductionRun?       = null,        // run started on THIS device (local Room)
    val backendRun: ActiveRun?          = null,        // live run from /energy/runs/active for this asset
    val todaysRuns: List<ProductionRun> = emptyList(),
    val currentUser: User?              = null,
    val activeBreakdown: BreakdownFlag? = null,
    val isLoading: Boolean              = false,
    val error: String?                  = null,
    val showStartDialog: Boolean        = false,
    val showStopDialog: Boolean         = false,
) {
    val isOperator: Boolean    get() = currentUser?.role == UserRole.OPERATOR
    /** Running per EITHER source — local row or the live backend run. */
    val hasActiveRun: Boolean  get() = activeRun != null || backendRun != null
    /** When the run started, whichever source we have. */
    val runStartedAtMs: Long?  get() = activeRun?.startedAt ?: backendRun?.startedAtEpochMs
    /** Who is running it (local run = the signed-in operator). */
    val runOperatorName: String?
        get() = if (activeRun != null) (currentUser?.name ?: "You") else backendRun?.operatorName
    /**
     * Stoppable when there's a local row (existing path), OR the backend reports a
     * run_id for this asset that the SIGNED-IN operator started (own-run policy).
     */
    val canStop: Boolean
        get() = activeRun != null ||
            (backendRun?.runId != null && backendRun.operatorId != null &&
                backendRun.operatorId == currentUser?.id)
    /** Running but not stoppable here (someone else's run, or backend hasn't sent run_id yet). */
    val runningButLocked: Boolean get() = hasActiveRun && !canStop
    /** An unresolved breakdown blocks starting production until QC closes it. */
    val underBreakdown: Boolean get() = activeBreakdown != null
}

sealed interface MachineDetailUiEvent {
    data class ShowSnackbar(val message: String) : MachineDetailUiEvent
}

@HiltViewModel
class MachineDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mtMachineRepository: MtMachineRepository,
    private val energyRepository: EnergyRepository,
    private val breakdownRepository: BreakdownRepository,
    private val startRunUseCase: StartProductionRunUseCase,
    private val stopRunUseCase: StopProductionRunUseCase,
    private val getTodaysRuns: GetTodaysRunsUseCase,
    private val getActiveRuns: GetActiveRunsUseCase,
    getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {

    // Nav arg is the asset_id (e.g. "W202-0005").
    private val assetId: String = checkNotNull(savedStateHandle[Screen.MachineDetail.ARG])

    private val _uiState = MutableStateFlow(MachineDetailUiState())
    val uiState: StateFlow<MachineDetailUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<MachineDetailUiEvent>()
    val uiEvent: SharedFlow<MachineDetailUiEvent> = _uiEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            when (val r = mtMachineRepository.getByAssetId(assetId)) {
                is Result.Success -> _uiState.update { it.copy(asset = r.data) }
                is Result.Error   -> _uiState.update { it.copy(error = r.message) }
                is Result.Loading -> Unit
            }
        }
        viewModelScope.launch {
            energyRepository.getActiveRunForMachine(assetId).collect { run ->
                _uiState.update { it.copy(activeRun = run) }
            }
        }
        viewModelScope.launch {
            getTodaysRuns(assetId).collect { runs ->
                _uiState.update { it.copy(todaysRuns = runs) }
            }
        }
        viewModelScope.launch {
            breakdownRepository.observeActiveFlagForMachine(assetId).collect { flag ->
                _uiState.update { it.copy(activeBreakdown = flag) }
            }
        }
        viewModelScope.launch {
            getCurrentUser().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
        // Reconcile with the live backend run for this asset (so the detail agrees
        // with Home's "Working now", even for runs started on another device).
        viewModelScope.launch {
            while (true) {
                loadBackendRun()
                delay(ACTIVE_RUNS_POLL_MILLIS)
            }
        }
    }

    private suspend fun loadBackendRun() {
        when (val r = getActiveRuns()) {
            is Result.Success -> _uiState.update { state ->
                state.copy(backendRun = r.data.firstOrNull { it.assetId == assetId })
            }
            else -> Unit   // keep last good value on a failed/loading fetch
        }
    }

    fun onStartClicked() {
        // Guard: a machine under breakdown can't start until QC closes it.
        if (_uiState.value.underBreakdown) return
        _uiState.update { it.copy(showStartDialog = true) }
    }
    fun onStopClicked()  = _uiState.update { it.copy(showStopDialog = true) }
    fun onDismissDialogs() = _uiState.update {
        it.copy(showStartDialog = false, showStopDialog = false)
    }

    fun onConfirmStart() {
        onDismissDialogs()
        viewModelScope.launch {
            val asset = _uiState.value.asset ?: return@launch
            _uiState.update { it.copy(isLoading = true) }
            when (val r = startRunUseCase(asset.assetId, asset.assetName, asset.ratedKw, DEFAULT_RUN_HOURS)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(MachineDetailUiEvent.ShowSnackbar("Production started"))
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(MachineDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onConfirmStop() {
        onDismissDialogs()
        val state = _uiState.value
        if (!state.canStop) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val now = System.currentTimeMillis()
            val errorMessage: String? = when {
                // Run started on this device → local stop path (queues sync).
                state.activeRun != null -> when (val r = stopRunUseCase(state.activeRun.id)) {
                    is Result.Error -> r.message
                    else            -> null
                }
                // Backend-only run started by this operator → stop it directly by run_id.
                state.backendRun?.runId != null -> when (
                    val r = energyRepository.stopActiveRunById(state.backendRun.runId, now)
                ) {
                    is Result.Error -> r.message
                    else            -> null
                }
                else -> "Can't stop this run from here."
            }
            loadBackendRun()
            _uiState.update { it.copy(isLoading = false) }
            _uiEvent.emit(MachineDetailUiEvent.ShowSnackbar(errorMessage ?: "Production stopped"))
        }
    }

    private companion object {
        // Fixed scheduled length for now; supervisor-configurable default is a follow-up.
        const val DEFAULT_RUN_HOURS = 8.0
        const val ACTIVE_RUNS_POLL_MILLIS = 30_000L
    }
}
