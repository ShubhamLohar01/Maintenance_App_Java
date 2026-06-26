package com.candorfoods.factoryops.domain.model

/**
 * Per-checkpoint outcome: ticked = OK, unticked-with-remark = NOT_OK, untouched =
 * UNSET. UNSET only occurs in DRAFT records — a finalized checklist has none.
 */
enum class CheckpointStatus { OK, NOT_OK, UNSET }

/** Lifecycle of a stored checklist record. */
enum class ChecklistStatus { DRAFT, SUBMITTED }

data class ChecklistItemSubmission(
    val section: String,
    val equipment: String,
    val srNo: Int?,
    val equipmentDate: String,   // ISO yyyy-MM-dd — the date this equipment was checked
    val checkpoint: String,
    val status: CheckpointStatus,
    val remarks: String,
)

/**
 * A completed Preventive Maintenance checklist, ready to persist to the backend
 * `doc_preventive_maintenance` table. [formType] is "MONTHLY" / "QUARTERLY" and
 * [docNo] is the controlled SOP number (CFPLA.C4.F.50a / 50b).
 */
data class ChecklistSubmission(
    val formType: String,
    val docNo: String,
    val status: ChecklistStatus,  // DRAFT (partial save) | SUBMITTED (final)
    val checklistDate: String,   // ISO yyyy-MM-dd
    val doneBy: String,
    val checkedBy: String,
    val verifiedBy: String,
    val remarks: String,
    val items: List<ChecklistItemSubmission>,
)

/** One breakdown row for the Breakdown Maintenance Record (CFPLA.C4.F.06). */
data class BreakdownEntrySubmission(
    val recordDate: String,
    val location: String,
    val machineName: String,
    val equipmentModelNo: String,
    val problemInBrief: String,
    val typeOfMaintenance: String,
    val partOfMachine: String,
    val temporaryReason: String,
    val durationStart: String,
    val durationEnd: String,
    val machineOperatorSign: String,
    val maintenancePersonSign: String,
    val qcClearanceSign: String,
)

/** A breakdown sheet — N entries plus one sheet-level verified-by. */
data class BreakdownSubmission(
    val docNo: String,
    val verifiedBy: String,
    val entries: List<BreakdownEntrySubmission>,
)
