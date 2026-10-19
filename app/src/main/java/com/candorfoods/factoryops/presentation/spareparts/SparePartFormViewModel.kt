package com.candorfoods.factoryops.presentation.spareparts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.repository.SparePartsRepository
import com.candorfoods.factoryops.presentation.spareparts.components.MachineOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SparePartFormUiState(
    val editingPartId: Int? = null,
    val assetOptions: List<MachineOption> = emptyList(),
    val assetLoadError: String? = null,
    /** Text in the machine search box; also the free-text name on the Other path. */
    val machineQuery: String = "",
    val assetId: String? = null,
    val machineName: String? = null,
    val warehouse: String? = null,
    val warehouseLocked: Boolean = false,
    val otherMode: Boolean = false,
    val partName: String = "",
    val unit: String = "",
    val quantity: String = "0",
    val minQuantity: String = "0",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    /** Exactly one machine source, plus a part name. Mirrors the backend's rules
     * so the user is told before a round trip, not after a 400. */
    val canSave: Boolean
        get() = partName.isNotBlank() &&
            !isSaving &&
            if (otherMode) !machineName.isNullOrBlank() && warehouse != null
            else assetId != null
}

/**
 * Add/edit form for one spare part. Assets come from the register on init; if that
 * fetch fails the picker collapses to the "Other" free-text path rather than
 * blocking the user, since adding a part must never depend on a second endpoint.
 */
@HiltViewModel
class SparePartFormViewModel @Inject constructor(
    private val spares: SparePartsRepository,
    private val machines: MtMachineRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SparePartFormUiState())
    val uiState: StateFlow<SparePartFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // The whole register, both buildings — "any machine which exists in the
            // assets DB". MtMachineRepository.list() already fetches it unfiltered.
            when (val r = machines.list()) {
                is Result.Success -> _uiState.update {
                    it.copy(assetOptions = r.data
                        .map { m -> MachineOption(m.assetId, m.assetName, m.building) }
                        .sortedBy { o -> o.assetName })
                }
                is Result.Error -> _uiState.update {
                    it.copy(
                        assetOptions = emptyList(),
                        assetLoadError = "Asset list unavailable — enter the machine manually",
                    )
                }
                Result.Loading -> Unit
            }
        }
    }

    /** Seed the form from an existing part when the sheet opens in edit mode. */
    fun startEditing(
        partId: Int, machineName: String, assetId: String?, warehouse: String?,
        partName: String, unit: String?, minQuantity: Int,
    ) {
        _uiState.update {
            it.copy(
                editingPartId = partId,
                assetId = assetId,
                machineName = if (assetId == null) machineName else null,
                machineQuery = machineName,
                warehouse = warehouse,
                warehouseLocked = assetId != null,
                otherMode = assetId == null,
                partName = partName,
                unit = unit.orEmpty(),
                minQuantity = minQuantity.toString(),
            )
        }
    }

    fun onMachineQueryChange(value: String) = _uiState.update {
        // Typing after a selection clears it — the field and the chosen asset must
        // never disagree about which machine is selected.
        it.copy(machineQuery = value, assetId = null, warehouseLocked = false)
    }

    fun onAssetSelected(option: MachineOption) = _uiState.update {
        it.copy(
            assetId = option.assetId,
            machineName = null,
            machineQuery = option.assetName,
            warehouse = option.building,
            warehouseLocked = true,
            otherMode = false,
            error = null,
        )
    }

    fun onChooseOther() = _uiState.update {
        it.copy(otherMode = true, assetId = null, warehouseLocked = false, warehouse = null)
    }

    fun onMachineNameChange(value: String) = _uiState.update { it.copy(machineName = value) }
    fun onWarehouseChange(value: String) = _uiState.update { it.copy(warehouse = value) }
    fun onPartNameChange(value: String) = _uiState.update { it.copy(partName = value, error = null) }
    fun onUnitChange(value: String) = _uiState.update { it.copy(unit = value) }
    fun onQuantityChange(value: String) = _uiState.update { it.copy(quantity = value.filter(Char::isDigit)) }
    fun onMinQuantityChange(value: String) = _uiState.update { it.copy(minQuantity = value.filter(Char::isDigit)) }

    fun save() {
        val s = _uiState.value
        if (!s.canSave) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = if (s.editingPartId == null) {
                spares.create(
                    assetId = s.assetId,
                    machineName = if (s.assetId == null) s.machineName else null,
                    warehouse = if (s.assetId == null) s.warehouse else null,
                    partName = s.partName.trim(),
                    unit = s.unit.trim().ifBlank { null },
                    quantity = s.quantity.toIntOrNull() ?: 0,
                    minQuantity = s.minQuantity.toIntOrNull() ?: 0,
                )
            } else {
                spares.update(
                    partId = s.editingPartId,
                    assetId = s.assetId,
                    machineName = if (s.assetId == null) s.machineName else null,
                    warehouse = if (s.assetId == null) s.warehouse else null,
                    partName = s.partName.trim(),
                    unit = s.unit.trim().ifBlank { null },
                    minQuantity = s.minQuantity.toIntOrNull() ?: 0,
                )
            }

            when (result) {
                is Result.Success -> _uiState.update { it.copy(isSaving = false, saved = true) }
                is Result.Error -> _uiState.update { it.copy(isSaving = false, error = result.message) }
                Result.Loading -> Unit
            }
        }
    }
}
