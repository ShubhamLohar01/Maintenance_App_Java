package com.candorfoods.factoryops.presentation.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.NewMachineTransfer
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MachineTransferRepository
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetMachineMasterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Machine Transfer form. Submits to POST /machine-transfers as multipart/form-data —
 * text fields plus an optional proof photo file part (the repository streams the picked
 * content:// uri; no AWS SDK / credentials on device). Option lists below are
 * placeholders the backend will later replace with real warehouses / asset names;
 * manual entry is always allowed for the machine field.
 */
/** One selectable asset in the transfer machine search. Carries the asset_id so the
 *  backend can move the exact register row's building (name alone is ambiguous). */
data class MachineOption(
    val assetId: String,
    val name: String,
    val building: String,
    val subLocation: String,
)

/** Compact, comparable warehouse code: "W-202"/"w202"/" W202 " -> "W202". */
private fun normWarehouse(s: String): String = s.filter { it.isLetterOrDigit() }.uppercase()

data class TransferFormState(
    val date: String = LocalDate.now().toString(),
    val fromWarehouse: String = "A185",
    val toWarehouse: String = "W202",
    val machineName: String = "",
    val selectedAssetId: String? = null,   // set when a register suggestion is picked; null if typed
    val machineCode: String = "",          // model / Sr. No. (optional)
    val proofImageUri: String? = null,     // local content:// uri of the picked photo
    val condition: String = "",
    val reason: String = "",
    val authorisedPerson: String = "",
    val remarks: String = "",
    // All assets fetched from the register (mt_asset_list); the picker filters these.
    val allMachines: List<MachineOption> = emptyList(),
    // Machine names currently mid-transfer (status=PENDING) — hidden from the picker
    // and blocked from a second transfer until the destination acknowledges receipt.
    val pendingMachineNames: Set<String> = emptySet(),
    val machinesError: String? = null,
    // Submission lifecycle for POST /machine-transfers.
    val submitting: Boolean = false,
    val submitError: String? = null,
    val submittedOk: Boolean = false,      // one-shot success — consume after handling
    val resultPhotoUrl: String? = null,    // S3 URL the backend returned (informational)
) {
    /** Only assets physically in the selected FROM warehouse, minus any already
     *  mid-transfer. Drives the machine-name autocomplete. */
    val machineSuggestions: List<MachineOption>
        get() = allMachines
            .filter { normWarehouse(it.building) == normWarehouse(fromWarehouse) }
            .filter { it.name.trim().lowercase() !in pendingMachineNames }

    /** True when the typed machine already has a pending transfer (blocks submit). */
    val machineIsPending: Boolean
        get() = machineName.isNotBlank() && machineName.trim().lowercase() in pendingMachineNames

    /** Light gate — enough to make the record meaningful while UI-only. */
    val canSubmit: Boolean
        get() = fromWarehouse.isNotBlank() &&
            toWarehouse.isNotBlank() &&
            machineName.isNotBlank() &&
            fromWarehouse != toWarehouse

    /** Surfaced inline so the user knows why submit is disabled. */
    val sameWarehouseError: Boolean
        get() = fromWarehouse.isNotBlank() && fromWarehouse == toWarehouse
}

@HiltViewModel
class TransferFormViewModel @Inject constructor(
    private val getMachineMaster: GetMachineMasterUseCase,
    private val transferRepository: MachineTransferRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TransferFormState())
    val state: StateFlow<TransferFormState> = _state.asStateFlow()

    init { loadMachines() }

    fun update(transform: (TransferFormState) -> TransferFormState) = _state.update(transform)

    /** Flip the transfer direction — only two warehouses, so From/To just swap. */
    fun swapWarehouses() = _state.update {
        it.copy(fromWarehouse = it.toWarehouse, toWarehouse = it.fromWarehouse)
    }

    /** Manual typing in the machine field — clears any picked asset_id so we never send
     *  a stale id for a hand-typed name (backend then falls back to a unique-name match). */
    fun onMachineNameChanged(v: String) = _state.update {
        it.copy(machineName = v, selectedAssetId = null)
    }

    /** A register suggestion was picked — capture its exact asset_id for a precise move. */
    fun onMachinePicked(option: MachineOption) = _state.update {
        it.copy(machineName = option.name, selectedAssetId = option.assetId)
    }

    /** Pull asset names from the register for the machine search. Manual entry still
     *  works if this fails, so a load error is surfaced softly, not blocking. */
    fun loadMachines() {
        viewModelScope.launch {
            when (val r = getMachineMaster()) {
                is Result.Success -> _state.update {
                    it.copy(
                        allMachines = r.data
                            .filter { m -> m.assetName.isNotBlank() }
                            .map { m -> MachineOption(m.assetId, m.assetName, m.building, m.subLocation) }
                            .sortedWith(compareBy({ o -> o.name.lowercase() }, { o -> o.building })),
                        machinesError = null,
                    )
                }
                is Result.Error -> _state.update { it.copy(machinesError = r.message) }
                is Result.Loading -> Unit
            }
        }
        loadPendingTransfers()
    }

    /** Names of machines already mid-transfer (status=PENDING) so the picker hides them
     *  and submit is blocked. Best-effort — a load failure just leaves the set empty. */
    private fun loadPendingTransfers() {
        viewModelScope.launch {
            when (val r = transferRepository.listTransfers()) {
                is Result.Success -> _state.update { st ->
                    st.copy(pendingMachineNames = r.data
                        .filter { it.status.equals("PENDING", ignoreCase = true) }
                        .map { it.machineName.trim().lowercase() }
                        .filter { it.isNotBlank() }
                        .toSet())
                }
                else -> Unit
            }
        }
    }

    /** POST the transfer as multipart. The proof photo (if any) is read from its
     *  content:// uri inside the repository and streamed as a file part. */
    fun submit() {
        val s = _state.value
        if (s.submitting || !s.canSubmit) return
        if (s.machineIsPending) {
            _state.update {
                it.copy(submitError = "This machine already has a pending transfer awaiting acknowledgement.")
            }
            return
        }
        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            val input = NewMachineTransfer(
                fromWarehouse = s.fromWarehouse,
                toWarehouse = s.toWarehouse,
                machineName = s.machineName,
                assetId = s.selectedAssetId,
                date = s.date.ifBlank { null },
                machineCode = s.machineCode.ifBlank { null },
                condition = s.condition.ifBlank { null },
                reason = s.reason.ifBlank { null },
                authorisedPerson = s.authorisedPerson.ifBlank { null },
                remarks = s.remarks.ifBlank { null },
                photoUri = s.proofImageUri,
            )
            when (val r = transferRepository.createTransfer(input)) {
                is Result.Success -> _state.update {
                    it.copy(submitting = false, submittedOk = true, resultPhotoUrl = r.data.proofPhotoUrl)
                }
                is Result.Error -> _state.update { it.copy(submitting = false, submitError = r.message) }
                is Result.Loading -> Unit
            }
        }
    }

    /** Clear the one-shot submit result after the screen has shown it. */
    fun consumeSubmitResult() = _state.update {
        it.copy(submittedOk = false, submitError = null, resultPhotoUrl = null)
    }

    companion object {
        // Warehouse codes for transfer source/destination (only these two; user swaps direction).
        val WAREHOUSES = listOf(
            "A185",
            "W202",
        )

        val CONDITIONS = listOf(
            "New",
            "Good",
            "Working",
            "Needs Repair",
            "Under Repair",
            "Damaged",
            "Scrap",
        )
    }
}
