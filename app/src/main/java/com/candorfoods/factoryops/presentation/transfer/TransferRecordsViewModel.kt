package com.candorfoods.factoryops.presentation.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.MachineTransfer
import com.candorfoods.factoryops.domain.model.NewMachineTransfer
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MachineTransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Machine-transfer records page. Lists GET /machine-transfers and lets an allowed user
 * (destination-warehouse technician or a supervisor — the backend sets `canAcknowledge`
 * per row) acknowledge receipt, flipping status PENDING -> APPROVED.
 */
/** Editable copy of a transfer while the creator edits it in the bottom sheet. */
data class TransferEditDraft(
    val id: Int,
    val fromWarehouse: String,
    val toWarehouse: String,
    val machineName: String,
    val date: String,
    val condition: String,
    val machineCode: String,
    val reason: String,
    val authorisedPerson: String,
    val remarks: String,
) {
    companion object {
        fun from(r: MachineTransfer) = TransferEditDraft(
            id = r.id,
            fromWarehouse = r.fromWarehouse,
            toWarehouse = r.toWarehouse,
            machineName = r.machineName,
            date = r.date.orEmpty(),
            condition = r.condition.orEmpty(),
            machineCode = r.machineCode.orEmpty(),
            reason = r.reason.orEmpty(),
            authorisedPerson = r.authorisedPerson.orEmpty(),
            remarks = r.remarks.orEmpty(),
        )
    }
}

data class TransferRecordsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val records: List<MachineTransfer> = emptyList(),
    val acknowledgingId: Int? = null,
    val deleteConfirmId: Int? = null,   // record whose delete is awaiting confirmation
    val busyId: Int? = null,            // a delete in flight
    val edit: TransferEditDraft? = null,
    val savingEdit: Boolean = false,
)

@HiltViewModel
class TransferRecordsViewModel @Inject constructor(
    private val repository: MachineTransferRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TransferRecordsUiState())
    val state: StateFlow<TransferRecordsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.records.isEmpty(), error = null) }
            when (val r = repository.listTransfers()) {
                is Result.Success -> _state.update { it.copy(isLoading = false, records = r.data, error = null) }
                is Result.Error   -> _state.update { it.copy(isLoading = false, error = r.message) }
                is Result.Loading -> Unit
            }
        }
    }

    fun acknowledge(id: Int) {
        if (_state.value.acknowledgingId != null) return
        _state.update { it.copy(acknowledgingId = id, error = null) }
        viewModelScope.launch {
            when (val r = repository.acknowledgeTransfer(id)) {
                is Result.Success -> {
                    _state.update { it.copy(acknowledgingId = null) }
                    refresh()
                }
                is Result.Error   -> _state.update { it.copy(acknowledgingId = null, error = r.message) }
                is Result.Loading -> Unit
            }
        }
    }

    // ----- delete -----------------------------------------------------------

    fun requestDelete(id: Int) = _state.update { it.copy(deleteConfirmId = id) }
    fun cancelDelete() = _state.update { it.copy(deleteConfirmId = null) }

    fun confirmDelete() {
        val id = _state.value.deleteConfirmId ?: return
        _state.update { it.copy(deleteConfirmId = null, busyId = id, error = null) }
        viewModelScope.launch {
            when (val r = repository.deleteTransfer(id)) {
                is Result.Success -> { _state.update { it.copy(busyId = null) }; refresh() }
                is Result.Error   -> _state.update { it.copy(busyId = null, error = r.message) }
                is Result.Loading -> Unit
            }
        }
    }

    // ----- edit -------------------------------------------------------------

    fun startEdit(record: MachineTransfer) =
        _state.update { it.copy(edit = TransferEditDraft.from(record), error = null) }
    fun cancelEdit() = _state.update { it.copy(edit = null) }
    fun updateEdit(transform: (TransferEditDraft) -> TransferEditDraft) =
        _state.update { st -> st.edit?.let { st.copy(edit = transform(it)) } ?: st }

    fun swapEditWarehouses() = _state.update { st ->
        st.edit?.let { st.copy(edit = it.copy(fromWarehouse = it.toWarehouse, toWarehouse = it.fromWarehouse)) } ?: st
    }

    fun saveEdit() {
        val d = _state.value.edit ?: return
        if (_state.value.savingEdit) return
        if (d.machineName.isBlank() || d.fromWarehouse.isBlank() || d.toWarehouse.isBlank() ||
            d.fromWarehouse.equals(d.toWarehouse, ignoreCase = true)) {
            _state.update { it.copy(error = "From and To must be set and different, and machine name is required.") }
            return
        }
        _state.update { it.copy(savingEdit = true, error = null) }
        viewModelScope.launch {
            val input = NewMachineTransfer(
                fromWarehouse = d.fromWarehouse,
                toWarehouse = d.toWarehouse,
                machineName = d.machineName,
                assetId = null,                       // edit keeps the existing register link
                date = d.date.ifBlank { null },
                machineCode = d.machineCode.ifBlank { null },
                condition = d.condition.ifBlank { null },
                reason = d.reason.ifBlank { null },
                authorisedPerson = d.authorisedPerson.ifBlank { null },
                remarks = d.remarks.ifBlank { null },
                photoUri = null,
            )
            when (val r = repository.updateTransfer(d.id, input)) {
                is Result.Success -> { _state.update { it.copy(savingEdit = false, edit = null) }; refresh() }
                is Result.Error   -> _state.update { it.copy(savingEdit = false, error = r.message) }
                is Result.Loading -> Unit
            }
        }
    }
}
