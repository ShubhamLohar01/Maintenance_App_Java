package com.candorfoods.factoryops.presentation.machine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.candorfoods.factoryops.domain.model.ActiveRun
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.CONDITION_SHUT_DOWN
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.isShutDown
import com.candorfoods.factoryops.domain.model.ProductionRun
import com.candorfoods.factoryops.domain.model.RecessState
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
import com.candorfoods.factoryops.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Text-backed editable fields of an asset for the supervisor edit sheet. */
data class AssetEditForm(
    val building: String = "",
    val assetName: String = "",
    val category: String = "",
    val subLocation: String = "",
    val quantity: String = "",
    val modelNo: String = "",
    val serialNo: String = "",
    val powerLoad: String = "",
    val condition: String = "",
    val remarks: String = "",
)

data class MachineDetailUiState(
    val asset: MtMachine?               = null,
    val activeRun: ProductionRun?       = null,        // run started on THIS device (local Room)
    val backendRun: ActiveRun?          = null,        // live run from /energy/runs/active for this asset
    val todaysRuns: List<ProductionRun> = emptyList(),
    val currentUser: User?              = null,
    val activeBreakdown: BreakdownFlag? = null,
    val recessState: RecessState?       = null,        // operator-side pause/resume session
    val isLoading: Boolean              = false,
    val error: String?                  = null,
    val showStartDialog: Boolean        = false,
    val showStopDialog: Boolean         = false,
    val showShutDownDialog: Boolean     = false,
    val showRestoreDialog: Boolean      = false,
    // Supervisor "edit machine details" sheet.
    val showEditSheet: Boolean          = false,
    val editForm: AssetEditForm         = AssetEditForm(),
    val isSavingEdit: Boolean           = false,
    val editError: String?              = null,
) {
    val isOperator: Boolean    get() = currentUser?.role == UserRole.OPERATOR
    /** Supervisors may edit the asset register (mt_asset_list) from here. */
    val isSupervisor: Boolean  get() = currentUser?.role == UserRole.SUPERVISOR
    /** A run started on THIS device is in progress (the segment we can pause/stop). */
    val isLocalRunning: Boolean get() = activeRun != null
    /** Paused: a session exists, on recess, with no active segment running. */
    val onRecess: Boolean      get() = recessState?.onRecess == true && activeRun == null
    /** A local production session (running or paused) is in progress on this device. */
    val inLocalSession: Boolean get() = isLocalRunning || onRecess
    /** Active time banked from finished segments this session (excludes recess). */
    val accumulatedActiveMs: Long get() = recessState?.accumulatedActiveMs ?: 0L
    /** Start of the segment running right now, or null when on recess / idle. */
    val currentSegmentStartMs: Long? get() = activeRun?.startedAt
    /** When the current recess began (for the "On recess Xm" label). */
    val recessStartedAtMs: Long? get() = recessState?.recessStartedAt
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
    /** This asset has been shut down (soft-decommissioned) — locked for all users. */
    val isShutDown: Boolean get() = asset?.isShutDown() == true
    /** Supervisor / Admin / Head may shut down or restore a machine. */
    val canToggleShutDown: Boolean
        get() = currentUser?.role == UserRole.SUPERVISOR ||
            currentUser?.role == UserRole.ADMIN ||
            currentUser?.role == UserRole.HEAD
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
            energyRepository.observeRecessState(assetId).collect { rs ->
                _uiState.update { it.copy(recessState = rs) }
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
        // Guard: a machine under breakdown or shut down can't start.
        if (_uiState.value.underBreakdown || _uiState.value.isShutDown) return
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
                    // Fresh session — drop any leftover recess row so the timer starts at 0.
                    energyRepository.clearRecessSession(asset.assetId)
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

    /** Recess (pause): stop the current segment and bank its active time. One-tap. */
    fun onRecessClicked() {
        if (!_uiState.value.isLocalRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val message = when (val r = energyRepository.recessProduction(assetId)) {
                is Result.Error -> r.message
                else            -> "Production paused"
            }
            loadBackendRun()
            _uiState.update { it.copy(isLoading = false) }
            _uiEvent.emit(MachineDetailUiEvent.ShowSnackbar(message))
        }
    }

    /** Resume from recess: open a new segment, keeping the banked active time. One-tap. */
    fun onResumeClicked() {
        val asset = _uiState.value.asset ?: return
        if (_uiState.value.isShutDown) return
        if (!_uiState.value.onRecess) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val scheduledEndAt = System.currentTimeMillis() + (DEFAULT_RUN_HOURS * 3_600_000).toLong()
            val message = when (
                val r = energyRepository.resumeProduction(asset.assetId, asset.assetName, asset.ratedKw, scheduledEndAt)
            ) {
                is Result.Error -> r.message
                else            -> "Production resumed"
            }
            loadBackendRun()
            _uiState.update { it.copy(isLoading = false) }
            _uiEvent.emit(MachineDetailUiEvent.ShowSnackbar(message))
        }
    }

    fun onConfirmStop() {
        onDismissDialogs()
        val state = _uiState.value
        // A local session (running or paused) ends via endProductionSession (stops the
        // segment if any + clears the session). Otherwise fall back to a backend-only stop.
        val hasLocalSession = state.inLocalSession
        if (!hasLocalSession && !state.canStop) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val now = System.currentTimeMillis()
            val errorMessage: String? = when {
                hasLocalSession -> when (val r = energyRepository.endProductionSession(assetId)) {
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

    // ─── Supervisor: edit machine details (PUT /mt-machines/{asset_id}) ───────────
    fun onEditClicked() {
        val a = _uiState.value.asset ?: return
        _uiState.update {
            it.copy(
                showEditSheet = true,
                editError = null,
                editForm = AssetEditForm(
                    building = a.building,
                    assetName = a.assetName,
                    category = a.category,
                    subLocation = a.subLocation,
                    quantity = a.quantity?.toString() ?: "",
                    modelNo = a.modelNo ?: "",
                    serialNo = a.serialNo ?: "",
                    powerLoad = a.powerLoadRaw ?: "",
                    condition = a.condition ?: "",
                    remarks = a.remarks ?: "",
                ),
            )
        }
    }

    fun onEditFormChange(transform: (AssetEditForm) -> AssetEditForm) =
        _uiState.update { it.copy(editForm = transform(it.editForm)) }

    fun onEditDismiss() = _uiState.update { it.copy(showEditSheet = false, editError = null) }

    fun onSaveEdit() {
        val current = _uiState.value.asset ?: return
        val f = _uiState.value.editForm
        if (f.assetName.isBlank() || f.building.isBlank()) {
            _uiState.update { it.copy(editError = "Asset name and building are required.") }
            return
        }
        // Carry edits on the existing asset. power_load is sent as text; the backend
        // recomputes rated_kw and returns the fresh row, which we swap in below.
        val edited = current.copy(
            building     = f.building.trim(),
            assetName    = f.assetName.trim(),
            category     = f.category.trim(),
            subLocation  = f.subLocation.trim(),
            quantity     = f.quantity.trim().toIntOrNull(),
            modelNo      = f.modelNo.trim().ifBlank { null },
            serialNo     = f.serialNo.trim().ifBlank { null },
            powerLoadRaw = f.powerLoad.trim().ifBlank { null },
            condition    = f.condition.trim().ifBlank { null },
            remarks      = f.remarks.trim().ifBlank { null },
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingEdit = true, editError = null) }
            when (val r = mtMachineRepository.updateAsset(edited)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSavingEdit = false, showEditSheet = false, asset = r.data) }
                    _uiEvent.emit(MachineDetailUiEvent.ShowSnackbar("Machine details updated"))
                }
                is Result.Error -> _uiState.update { it.copy(isSavingEdit = false, editError = r.message) }
                is Result.Loading -> _uiState.update { it.copy(isSavingEdit = false) }
            }
        }
    }

    // ─── Shut down / restore (SUPERVISOR / ADMIN / HEAD) ──────────────────────────
    fun onShutDownClicked() = _uiState.update { it.copy(showShutDownDialog = true) }
    fun onRestoreClicked()  = _uiState.update { it.copy(showRestoreDialog = true) }
    fun onDismissShutDownDialogs() =
        _uiState.update { it.copy(showShutDownDialog = false, showRestoreDialog = false) }

    fun onConfirmShutDown() = setCondition(CONDITION_SHUT_DOWN, "Machine shut down")
    fun onConfirmRestore()  = setCondition(null, "Machine restored")

    /** Writes condition via PUT /mt-machines/{asset_id} (backend-authoritative). */
    private fun setCondition(condition: String?, successMsg: String) {
        onDismissShutDownDialogs()
        val current = _uiState.value.asset ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val r = mtMachineRepository.updateAsset(current.copy(condition = condition))) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, asset = r.data) }
                    _uiEvent.emit(MachineDetailUiEvent.ShowSnackbar(successMsg))
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(MachineDetailUiEvent.ShowSnackbar(r.message))
                }
                is Result.Loading -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private companion object {
        // Fixed scheduled length for now; supervisor-configurable default is a follow-up.
        const val DEFAULT_RUN_HOURS = 8.0
        const val ACTIVE_RUNS_POLL_MILLIS = 30_000L
    }
}
