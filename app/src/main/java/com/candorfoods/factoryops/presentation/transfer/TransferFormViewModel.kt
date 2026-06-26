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
data class TransferFormState(
    val date: String = LocalDate.now().toString(),
    val fromWarehouse: String = "",
    val toWarehouse: String = "",
    val machineName: String = "",
    val machineCode: String = "",          // model / Sr. No. (optional)
    val proofImageUri: String? = null,     // local content:// uri of the picked photo
    val condition: String = "",
    val reason: String = "",
    val authorisedPerson: String = "",
    val remarks: String = "",
    // Asset names fetched from the register (mt_asset_list) for the search/autocomplete.
    val machineSuggestions: List<String> = emptyList(),
    val machinesError: String? = null,
    // Submission lifecycle for POST /machine-transfers.
    val submitting: Boolean = false,
    val submitError: String? = null,
    val submittedOk: Boolean = false,      // one-shot success — consume after handling
    val resultPhotoUrl: String? = null,    // S3 URL the backend returned (informational)
) {
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

    /** Pull asset names from the register for the machine search. Manual entry still
     *  works if this fails, so a load error is surfaced softly, not blocking. */
    fun loadMachines() {
        viewModelScope.launch {
            when (val r = getMachineMaster()) {
                is Result.Success -> _state.update {
                    it.copy(
                        machineSuggestions = r.data
                            .map { m -> m.assetName }
                            .filter { n -> n.isNotBlank() }
                            .distinct()
                            .sorted(),
                        machinesError = null,
                    )
                }
                is Result.Error -> _state.update { it.copy(machinesError = r.message) }
                is Result.Loading -> Unit
            }
        }
    }

    /** POST the transfer as multipart. The proof photo (if any) is read from its
     *  content:// uri inside the repository and streamed as a file part. */
    fun submit() {
        val s = _state.value
        if (s.submitting || !s.canSubmit) return
        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            val input = NewMachineTransfer(
                fromWarehouse = s.fromWarehouse,
                toWarehouse = s.toWarehouse,
                machineName = s.machineName,
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
        // Warehouse codes for transfer source/destination.
        val WAREHOUSES = listOf(
            "A185",
            "W202",
            "JTC",
            "A68",
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
