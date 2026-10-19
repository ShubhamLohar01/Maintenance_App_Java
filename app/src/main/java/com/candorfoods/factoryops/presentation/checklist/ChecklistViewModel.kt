package com.candorfoods.factoryops.presentation.checklist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownEntrySubmission
import com.candorfoods.factoryops.domain.model.BreakdownSubmission
import com.candorfoods.factoryops.domain.model.ChecklistItemSubmission
import com.candorfoods.factoryops.domain.model.ChecklistStatus
import com.candorfoods.factoryops.domain.model.ChecklistSubmission
import com.candorfoods.factoryops.domain.model.CheckpointStatus
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One checkbox + remarks row. `checkpoint` is blank for single-row equipment. */
data class ChecklistRow(
    val section: String,
    val equipment: String,
    val srNo: Int?,
    /** The date this equipment was checked. On quarterly forms this is the FROM of
     *  its range; on monthly forms it is the single date and [equipmentDateTo] is
     *  left blank. Kept under the original name so every existing reader — the PDF,
     *  the detail screen, the backend's equipment_date — is unaffected. */
    val equipmentDate: String,
    /** Quarterly only: the TO of this equipment's range. Blank on monthly forms. */
    val equipmentDateTo: String = "",
    val checkpoint: String,
    val checked: Boolean = false,
    val remarks: String = "",
) {
    /** A row is filled once the technician has ticked it OR written a remark. */
    val isFilled: Boolean get() = checked || remarks.isNotBlank()

    /** Tri-state outcome; UNSET only appears on a not-yet-finished (draft) row. */
    val status: CheckpointStatus
        get() = when {
            checked                  -> CheckpointStatus.OK
            remarks.isNotBlank()     -> CheckpointStatus.NOT_OK
            else                     -> CheckpointStatus.UNSET
        }

    /** Stable identity of the equipment this row belongs to (dates are shared per equipment). */
    val equipmentKey: String get() = "$section|$srNo|$equipment"
}

data class ChecklistUiState(
    val type: ChecklistType = ChecklistType.MONTHLY,
    /** The forms this technician's plant uses — the only types they can switch between. */
    val availableTypes: List<ChecklistType> = listOf(ChecklistType.MONTHLY, ChecklistType.QUARTERLY),
    val doneBy: String = "",
    val checkedBy: String = "",
    val verifiedBy: String = "",
    val remarks: String = "",
    val rows: List<ChecklistRow> = emptyList(),
    val draftId: Int? = null,
    val isLoadingDraft: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val submittedId: Int? = null,
    val draftSavedTick: Int = 0,
) {
    val totalRows: Int get() = rows.size
    val filledRows: Int get() = rows.count { it.isFilled }
    val allFilled: Boolean get() = rows.isNotEmpty() && filledRows == totalRows

    /**
     * Quarterly sheets date each machine over a range, so every equipment carries a
     * Date From and a Date To. Monthly sheets keep the single date they always had.
     */
    val needsEquipmentRange: Boolean
        get() = type == ChecklistType.QUARTERLY || type == ChecklistType.A185_QUARTERLY

    /** One entry per equipment: its (from, to) as currently typed. */
    val equipmentRanges: Map<String, Pair<String, String>>
        get() = rows.associate { it.equipmentKey to (it.equipmentDate to it.equipmentDateTo) }

    val totalEquipment: Int get() = equipmentRanges.size

    /**
     * Equipment whose dating is complete and consistent.
     *
     * Monthly: the single date is well-formed. Quarterly: both ends are well-formed
     * and to >= from. Compared as strings — ISO yyyy-MM-dd sorts lexicographically
     * the same way it sorts chronologically, and both sides already match DATE_REGEX.
     */
    val datedEquipment: Int
        get() = equipmentRanges.count { (_, r) -> rangeOk(r.first, r.second) }

    val allEquipmentDated: Boolean get() = totalEquipment > 0 && datedEquipment == totalEquipment

    private fun rangeOk(from: String, to: String): Boolean =
        if (needsEquipmentRange) from.matches(DATE_REGEX) && to.matches(DATE_REGEX) && to >= from
        else from.matches(DATE_REGEX)

    /**
     * Complaint about one equipment's dates, or null while it is merely incomplete.
     *
     * Half-typed is not an error — the user is still working. Only a malformed value
     * or a genuinely inverted range earns red text.
     */
    fun equipmentDateError(from: String, to: String): String? = when {
        from.isNotBlank() && !from.matches(DATE_REGEX) -> "Format: YYYY-MM-DD, e.g. 2026-07-01"
        !needsEquipmentRange -> null
        to.isNotBlank() && !to.matches(DATE_REGEX) -> "Format: YYYY-MM-DD, e.g. 2026-09-30"
        from.isBlank() || to.isBlank() -> null            // incomplete, not wrong
        to < from -> "Date To must be on or after Date From"
        else -> null
    }

    private val signOffComplete: Boolean
        get() = doneBy.isNotBlank() && checkedBy.isNotBlank() && verifiedBy.isNotBlank()

    /** Anything entered yet (so a partial save is worth doing). */
    private val anyEntered: Boolean
        get() = filledRows > 0 || doneBy.isNotBlank() || checkedBy.isNotBlank() ||
            verifiedBy.isNotBlank() || remarks.isNotBlank()

    private val busy: Boolean get() = isSubmitting || isLoadingDraft

    val canSaveDraft: Boolean get() = !busy && anyEntered

    // allEquipmentDated already demands a complete, ordered range per machine on
    // quarterly forms. The server deliberately does not enforce presence (old builds
    // send neither date), so this gate is what actually makes it mandatory.
    val canSubmit: Boolean
        get() = !busy && allFilled && allEquipmentDated && signOffComplete

    companion object {
        val DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")
    }
}

@HiltViewModel
class ChecklistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val checklistRepository: ChecklistRepository,
    getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {

    // Per-type row state, so toggling Monthly/Quarterly never loses entries. Built for
    // every type: a draft of another plant's form still has somewhere to merge into.
    private val rowsByType: MutableMap<ChecklistType, List<ChecklistRow>> =
        ChecklistType.entries.associateWith { buildRows(it) }.toMutableMap()

    private val _uiState = MutableStateFlow(
        ChecklistUiState(rows = rowsByType.getValue(ChecklistType.MONTHLY))
    )
    val uiState: StateFlow<ChecklistUiState> = _uiState.asStateFlow()

    init {
        // Opening a draft first: loadDraft flips isLoadingDraft synchronously, which is the
        // flag the plant resolver below reads to leave the draft's own type alone.
        val draftId = savedStateHandle.get<String>(Screen.PreventiveForm.ARG)?.toIntOrNull()
        if (draftId != null) loadDraft(draftId)

        // The technician's own plant picks the form set — there is no plant switcher.
        viewModelScope.launch {
            val types = ChecklistType.forPlant(getCurrentUser().firstOrNull()?.plantId)
            _uiState.update { state ->
                val openingDraft = state.isLoadingDraft || state.draftId != null
                val type = if (openingDraft) state.type else types.first()
                state.copy(availableTypes = types, type = type, rows = rowsByType.getValue(type))
            }
        }
    }

    fun onTypeChange(type: ChecklistType) =
        _uiState.update { it.copy(type = type, rows = rowsByType.getValue(type), submitError = null) }

    fun onDoneByChange(v: String)     = _uiState.update { it.copy(doneBy = v, submitError = null) }
    fun onCheckedByChange(v: String)  = _uiState.update { it.copy(checkedBy = v, submitError = null) }
    fun onVerifiedByChange(v: String) = _uiState.update { it.copy(verifiedBy = v, submitError = null) }
    fun onRemarksChange(v: String)    = _uiState.update { it.copy(remarks = v, submitError = null) }

    fun onRowCheckedChange(index: Int, checked: Boolean) = mutateRow(index) { it.copy(checked = checked) }
    fun onRowRemarksChange(index: Int, remarks: String)  = mutateRow(index) { it.copy(remarks = remarks) }

    /** Updates the start date on every checkpoint row of one equipment. */
    fun onEquipmentDateChange(equipmentKey: String, date: String) =
        mutateEquipment(equipmentKey) { it.copy(equipmentDate = date) }

    /** Updates the end date on every checkpoint row of one equipment (quarterly only). */
    fun onEquipmentDateToChange(equipmentKey: String, date: String) =
        mutateEquipment(equipmentKey) { it.copy(equipmentDateTo = date) }

    /** Dates are per EQUIPMENT, but stored on each of its checkpoint rows, so a change
     *  has to fan out across every row sharing the key. */
    private fun mutateEquipment(equipmentKey: String, transform: (ChecklistRow) -> ChecklistRow) {
        _uiState.update { state ->
            val updated = state.rows.map { if (it.equipmentKey == equipmentKey) transform(it) else it }
            rowsByType[state.type] = updated
            state.copy(rows = updated, submitError = null)
        }
    }

    private inline fun mutateRow(index: Int, transform: (ChecklistRow) -> ChecklistRow) {
        _uiState.update { state ->
            val updated = state.rows.toMutableList().also { it[index] = transform(it[index]) }
            rowsByType[state.type] = updated
            state.copy(rows = updated, submitError = null)
        }
    }

    /** Partial save — keeps the form open and remembers the record id. */
    fun onSaveDraft() {
        val state = _uiState.value
        if (!state.canSaveDraft) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null) }
            val submission = buildSubmission(state, ChecklistStatus.DRAFT)
            when (val r = checklistRepository.save(submission, state.draftId)) {
                is Result.Success -> _uiState.update {
                    it.copy(isSubmitting = false, draftId = r.data, draftSavedTick = it.draftSavedTick + 1)
                }
                is Result.Error -> _uiState.update { it.copy(isSubmitting = false, submitError = r.message) }
                is Result.Loading -> Unit
            }
        }
    }

    /** Final submit — marks the record SUBMITTED. */
    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null) }
            val submission = buildSubmission(state, ChecklistStatus.SUBMITTED)
            when (val r = checklistRepository.save(submission, state.draftId)) {
                is Result.Success -> _uiState.update { it.copy(isSubmitting = false, submittedId = r.data) }
                is Result.Error   -> _uiState.update { it.copy(isSubmitting = false, submitError = r.message) }
                is Result.Loading -> Unit
            }
        }
    }

    fun onSubmittedAcknowledged() = _uiState.update { it.copy(submittedId = null) }

    private fun buildSubmission(state: ChecklistUiState, status: ChecklistStatus): ChecklistSubmission {
        // Record date = earliest valid equipment date on the sheet.
        val recordDate = state.rows.map { it.equipmentDate }
            .filter { it.matches(ChecklistUiState.DATE_REGEX) }
            .minOrNull() ?: LocalDate.now().toString()
        // No document-level period is sent: the backend derives the sheet's span from
        // the per-machine dates below (see _period in api/preventive_maintenance.py).
        return ChecklistSubmission(
            formType      = state.type.name,
            docNo         = state.type.docNo,
            status        = status,
            checklistDate = recordDate,
            doneBy        = state.doneBy.trim(),
            checkedBy     = state.checkedBy.trim(),
            verifiedBy    = state.verifiedBy.trim(),
            remarks       = state.remarks.trim(),
            items = state.rows.map {
                ChecklistItemSubmission(
                    section       = it.section,
                    equipment     = it.equipment,
                    srNo          = it.srNo,
                    equipmentDate = it.equipmentDate,
                    // A machine serviced within one day carries the same date at both
                    // ends, which is what monthly forms always produce.
                    equipmentDateTo = it.equipmentDateTo.ifBlank { it.equipmentDate },
                    checkpoint    = it.checkpoint,
                    status        = it.status,
                    remarks       = it.remarks.trim(),
                )
            },
        )
    }

    private fun loadDraft(id: Int) {
        _uiState.update { it.copy(isLoadingDraft = true) }
        viewModelScope.launch {
            when (val r = checklistRepository.detail(id)) {
                is Result.Success -> {
                    val d = r.data
                    val type = ChecklistType.entries.find { it.name.equals(d.formType, true) }
                        ?: ChecklistType.MONTHLY
                    val savedByKey = d.items.associateBy { rowKey(it.section, it.srNo, it.equipment, it.checkpoint) }
                    val merged = rowsByType.getValue(type).map { row ->
                        val saved = savedByKey[rowKey(row.section, row.srNo, row.equipment, row.checkpoint)]
                            ?: return@map row
                        row.copy(
                            checked         = saved.status.equals("OK", true),
                            remarks         = saved.remarks,
                            equipmentDate   = saved.equipmentDate.ifBlank { row.equipmentDate },
                            equipmentDateTo = saved.equipmentDateTo.ifBlank { row.equipmentDateTo },
                        )
                    }
                    rowsByType[type] = merged
                    // The record-level range is derived on submit, so nothing to restore
                    // here — the per-equipment dates above are the source of truth.
                    _uiState.update {
                        it.copy(
                            type = type, rows = merged, draftId = id, isLoadingDraft = false,
                            doneBy = d.doneBy, checkedBy = d.checkedBy,
                            verifiedBy = d.verifiedBy, remarks = d.remarks,
                        )
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoadingDraft = false, submitError = "Couldn't load draft — ${r.message}")
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun rowKey(section: String, srNo: Int?, equipment: String, checkpoint: String) =
        "$section|$srNo|$equipment|$checkpoint"

    private fun buildRows(type: ChecklistType): List<ChecklistRow> {
        val today = LocalDate.now().toString()
        return ChecklistCatalog.forType(type).flatMap { section ->
            section.equipment.flatMap { eqp ->
                if (eqp.checkpoints.isEmpty()) {
                    listOf(ChecklistRow(section.name, eqp.name, eqp.srNo, equipmentDate = today, checkpoint = ""))
                } else {
                    // Real machines: maintenance checkpoints + the 4 After-Maintenance checks.
                    (eqp.checkpoints + ChecklistCatalog.afterMaintenanceChecks).map { cp ->
                        ChecklistRow(section.name, eqp.name, eqp.srNo, equipmentDate = today, checkpoint = cp)
                    }
                }
            }
        }
    }
}
