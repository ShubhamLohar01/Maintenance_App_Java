package com.candorfoods.factoryops.presentation.checklist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownEntrySubmission
import com.candorfoods.factoryops.domain.model.BreakdownSubmission
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetMachineMasterUseCase
import com.candorfoods.factoryops.navigation.Screen
import com.candorfoods.factoryops.util.pdf.BreakdownDocData
import com.candorfoods.factoryops.util.pdf.BreakdownRow
import com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** One breakdown row of the F.06 register. */
data class BreakdownEntryState(
    val key: String,
    val date: String = LocalDate.now().toString(),
    val location: String = "",
    val machineName: String = "",
    val equipmentModel: String = "",
    val problem: String = "",
    val typeOfMaintenance: String = "",
    val partOfMachine: String = "",
    val reason: String = "",
    val durationStart: String = "",
    val durationEnd: String = "",
    val machineOperatorSign: String = "",
    val maintenancePersonSign: String = "",
    val qcClearanceSign: String = "",
) {
    /** Minimum viable row: a date, the machine, and the problem. */
    val isValid: Boolean get() = date.isNotBlank() && machineName.isNotBlank() && problem.isNotBlank()
}

/** A pickable machine from the asset register: its name + its asset/Sr. No. */
data class MachineOption(val name: String, val srNo: String)

data class BreakdownFormState(
    val entries: List<BreakdownEntryState> = emptyList(),
    val verifiedBy: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
    // Machine register for the Machine Name search/dropdown.
    val machineOptions: List<MachineOption> = emptyList(),
    val machinesError: String? = null,
    // PDF export.
    val isExporting: Boolean = false,
    val pdfToShare: File? = null,
    val exportError: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && entries.isNotEmpty() && entries.all { it.isValid }

    /** At least one complete row is needed to produce a non-empty sheet. */
    val canDownload: Boolean
        get() = !isExporting && entries.any { it.isValid }
}

@HiltViewModel
class BreakdownFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val checklistRepository: ChecklistRepository,
    private val breakdownRepository: BreakdownRepository,
    private val getMachineMaster: GetMachineMasterUseCase,
    private val getCurrentUser: GetCurrentUserUseCase,
    private val pdfRenderer: ChecklistPdfRenderer,
) : ViewModel() {

    private val breakdownId: String? = savedStateHandle.get<String>(Screen.BreakdownForm.ARG)

    private var seq = 0
    /** Technician's warehouse, used to pre-fill the Location of new rows (editable). */
    private var defaultLocation: String = ""
    private fun newEntry() = BreakdownEntryState(
        key = "bd-${seq++}",
        location = defaultLocation,
        typeOfMaintenance = "Breakdown",
    )

    private val _state = MutableStateFlow(BreakdownFormState(entries = listOf(newEntry())))
    val state: StateFlow<BreakdownFormState> = _state.asStateFlow()

    init {
        loadMachines()
        loadDefaultLocation()
        if (!breakdownId.isNullOrBlank()) prefillFromBreakdown(breakdownId)
    }

    /** Load the technician's warehouse and back-fill the Location of any blank rows. */
    private fun loadDefaultLocation() {
        viewModelScope.launch {
            val user = getCurrentUser().firstOrNull() ?: return@launch
            defaultLocation = warehouseName(user.plantId)
            _state.update { st ->
                st.copy(entries = st.entries.map {
                    if (it.location.isBlank()) it.copy(location = defaultLocation) else it
                })
            }
        }
    }

    private fun warehouseName(plantId: String?): String = when (plantId?.trim()?.uppercase()) {
        "W202" -> "W-202"
        "A185" -> "A-185"
        else -> plantId.orEmpty()
    }

    /** Seed the single form row from a QC-approved breakdown (technician then reviews/edits). */
    private fun prefillFromBreakdown(id: String) {
        viewModelScope.launch {
            val flag = breakdownRepository.observeFlagById(id).firstOrNull() ?: return@launch
            val entry = breakdownFlagToEntry(flag, "bd-${seq++}")
            _state.update {
                it.copy(entries = listOf(
                    entry.copy(typeOfMaintenance = entry.typeOfMaintenance.ifBlank { "Breakdown" })
                ))
            }
        }
    }

    /** Asset names + their asset_id (Sr. No) for the Machine Name picker. Manual
     *  typing still works if this fails, so an error is surfaced softly. */
    fun loadMachines() {
        viewModelScope.launch {
            when (val r = getMachineMaster()) {
                is Result.Success -> _state.update { st ->
                    st.copy(
                        machineOptions = r.data
                            .filter { it.assetName.isNotBlank() }
                            .map { MachineOption(it.assetName, it.assetId) }
                            .distinctBy { it.name }
                            .sortedBy { it.name },
                        machinesError = null,
                    )
                }
                is Result.Error -> _state.update { it.copy(machinesError = r.message) }
                is Result.Loading -> Unit
            }
        }
    }

    /** Machine chosen from the dropdown: fill the name AND auto-fill its Sr./Model No. */
    fun onMachineSelected(entryKey: String, option: MachineOption) = _state.update { st ->
        st.copy(entries = st.entries.map {
            if (it.key == entryKey) it.copy(machineName = option.name, equipmentModel = option.srNo) else it
        }, error = null)
    }

    fun addEntry() = _state.update { it.copy(entries = it.entries + newEntry(), error = null) }

    fun removeEntry(key: String) = _state.update {
        val left = it.entries.filterNot { e -> e.key == key }
        it.copy(entries = left.ifEmpty { listOf(newEntry()) }, error = null)
    }

    fun updateEntry(updated: BreakdownEntryState) = _state.update { st ->
        st.copy(entries = st.entries.map { if (it.key == updated.key) updated else it }, error = null)
    }

    fun onVerifiedByChange(v: String) = _state.update { it.copy(verifiedBy = v, error = null) }

    /** POST /preventive-maintenance/breakdowns — the sheet carries all entries (one row each). */
    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            val submission = BreakdownSubmission(
                docNo = "CFPLA.C4.F.06",
                verifiedBy = s.verifiedBy.trim(),
                entries = s.entries.map {
                    BreakdownEntrySubmission(
                        recordDate            = it.date.trim(),
                        location              = it.location.trim(),
                        machineName           = it.machineName.trim(),
                        equipmentModelNo      = it.equipmentModel.trim(),
                        problemInBrief        = it.problem.trim(),
                        typeOfMaintenance     = it.typeOfMaintenance.trim(),
                        partOfMachine         = it.partOfMachine.trim(),
                        temporaryReason       = it.reason.trim(),
                        durationStart         = it.durationStart.trim(),
                        durationEnd           = it.durationEnd.trim(),
                        machineOperatorSign   = it.machineOperatorSign.trim(),
                        maintenancePersonSign = it.maintenancePersonSign.trim(),
                        qcClearanceSign       = it.qcClearanceSign.trim(),
                    )
                },
            )
            when (val r = checklistRepository.submitBreakdown(submission)) {
                is Result.Success -> _state.update { it.copy(isSubmitting = false, submitted = true) }
                is Result.Error   -> _state.update { it.copy(isSubmitting = false, error = r.message) }
                is Result.Loading -> Unit
            }
        }
    }

    fun onSubmittedHandled() = _state.update { it.copy(submitted = false) }

    /** Build the F.06 PDF from the complete on-screen entries and emit it for sharing. */
    fun exportPdf() {
        val s = _state.value
        if (s.isExporting) return
        val rows = s.entries.filter { it.isValid }.mapIndexed { i, e ->
            BreakdownRow(
                srNo = i + 1,
                date = e.date.trim(),
                location = e.location.trim(),
                machineName = e.machineName.trim(),
                equipmentModel = e.equipmentModel.trim(),
                problem = e.problem.trim(),
                typeOfMaintenance = e.typeOfMaintenance.trim(),
                partOfMachine = e.partOfMachine.trim(),
                reason = e.reason.trim(),
                durationStart = e.durationStart.trim(),
                durationEnd = e.durationEnd.trim(),
                machineOperatorSign = e.machineOperatorSign.trim(),
                maintenancePersonSign = e.maintenancePersonSign.trim(),
                qcClearanceSign = e.qcClearanceSign.trim(),
            )
        }
        if (rows.isEmpty()) {
            _state.update { it.copy(exportError = "Add at least one entry (Date, Machine, Problem).") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, exportError = null) }
            try {
                val file = pdfRenderer.renderBreakdown(
                    BreakdownDocData(
                        docNo = "CFPLA.C4.F.06",
                        verifiedBy = s.verifiedBy.trim(),
                        rows = rows,
                    )
                )
                _state.update { it.copy(isExporting = false, pdfToShare = file) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isExporting = false, exportError = e.message ?: "Couldn't create PDF")
                }
            }
        }
    }

    fun onPdfShared() = _state.update { it.copy(pdfToShare = null) }

    fun onExportErrorShown() = _state.update { it.copy(exportError = null) }
}
