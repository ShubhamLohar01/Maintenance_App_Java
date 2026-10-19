package com.candorfoods.factoryops.presentation.it

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.presentation.common.components.AssetEditForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItAssetDetailUiState(
    val asset: MtMachine? = null,
    val currentUser: User? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showEditSheet: Boolean = false,
    val editForm: AssetEditForm = AssetEditForm(),
    val isSavingEdit: Boolean = false,
    val editError: String? = null,
) {
    /**
     * Who may edit the asset register. Mirrors the backend's allow-list for these
     * categories, so the pencil never shows to someone who'd only earn a 403.
     */
    val canEdit: Boolean
        get() = currentUser?.role in setOf(
            UserRole.IT_TECHNICIAN, UserRole.SUPERVISOR, UserRole.HEAD, UserRole.ADMIN,
        )
}

sealed interface ItAssetDetailUiEvent {
    data class ShowSnackbar(val message: String) : ItAssetDetailUiEvent
}

/**
 * One IT asset (mt_asset_list row), read-only plus an edit sheet.
 *
 * The asset_id is pushed in by the screen rather than injected via SavedStateHandle,
 * matching [ItAssetListViewModel] and RaisedTicketDetailViewModel — it keeps the
 * ViewModel plainly constructible in tests.
 */
@HiltViewModel
class ItAssetDetailViewModel @Inject constructor(
    private val mtMachineRepository: MtMachineRepository,
    getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItAssetDetailUiState())
    val uiState: StateFlow<ItAssetDetailUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<ItAssetDetailUiEvent>()
    val uiEvent: SharedFlow<ItAssetDetailUiEvent> = _uiEvent.asSharedFlow()

    private var assetId: String = ""

    init {
        viewModelScope.launch {
            getCurrentUser().collect { user -> _uiState.update { it.copy(currentUser = user) } }
        }
    }

    /** Set the asset this screen shows. Re-entering the same id doesn't re-fetch. */
    fun onAssetId(id: String) {
        if (id == assetId && _uiState.value.asset != null) return
        assetId = id
        load()
    }

    fun onRetry() = load()

    // ─── Edit (PUT /mt-machines/{asset_id}) ──────────────────────────────────────
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

    /**
     * Save the sheet. The category is carried over from the loaded asset, never from
     * the form — it's locked in the UI, so an IT edit can't move the asset out of the
     * category list it was opened from.
     */
    fun onSaveEdit() {
        val current = _uiState.value.asset ?: return
        val f = _uiState.value.editForm
        if (f.assetName.isBlank() || f.building.isBlank()) {
            _uiState.update { it.copy(editError = "Asset name and building are required.") }
            return
        }
        val edited = current.copy(
            building = f.building.trim(),
            assetName = f.assetName.trim(),
            subLocation = f.subLocation.trim(),
            quantity = f.quantity.trim().toIntOrNull(),
            modelNo = f.modelNo.trim().ifBlank { null },
            serialNo = f.serialNo.trim().ifBlank { null },
            powerLoadRaw = f.powerLoad.trim().ifBlank { null },
            condition = f.condition.trim().ifBlank { null },
            remarks = f.remarks.trim().ifBlank { null },
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingEdit = true, editError = null) }
            when (val r = mtMachineRepository.updateAsset(edited)) {
                is Result.Success -> {
                    // Swap in the row the backend returned — rated_kw is recomputed there.
                    _uiState.update {
                        it.copy(isSavingEdit = false, showEditSheet = false, asset = r.data)
                    }
                    _uiEvent.emit(ItAssetDetailUiEvent.ShowSnackbar("Asset updated"))
                }
                is Result.Error -> _uiState.update {
                    it.copy(isSavingEdit = false, editError = r.message)
                }
                is Result.Loading -> _uiState.update { it.copy(isSavingEdit = false) }
            }
        }
    }

    private fun load() {
        val id = assetId
        if (id.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = mtMachineRepository.getByAssetId(id)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        asset = r.data,
                        error = if (r.data == null) "Asset $id was not found." else null,
                    )
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                is Result.Loading -> _uiState.update { it.copy(isLoading = true) }
            }
        }
    }
}
