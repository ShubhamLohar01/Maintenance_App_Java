package com.candorfoods.factoryops.presentation.qc

import com.candorfoods.factoryops.presentation.checklist.ChecklistCatalog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class CheckResult { PASS, FAIL, NA, UNANSWERED }

/** One after-maintenance line in the QC snapshot (wire shape read by the backend sync:
 *  it fills the machine's after-maintenance rows in the 50a/50b record). */
@Serializable
data class QcAfterMaintenanceDto(
    val checkpoint: String,
    val status: String,          // "PASS" | "FAIL"  (backend maps -> OK / NOT_OK)
    val remarks: String? = null,
)

@Serializable
data class QcChecklistSnapshot(
    @SerialName("after_maintenance") val afterMaintenance: List<QcAfterMaintenanceDto>,
    val notes: String? = null,
)

/** One editable after-maintenance check in the QC sheet. */
data class QcAfterMaintenanceItem(
    val checkpoint: String,
    val result: CheckResult = CheckResult.UNANSWERED,
    val remarks: String = "",
)

/**
 * QC "After Maintenance" checklist — the 4 controlled housekeeping checks the QC person
 * confirms per machine (replaces the old generic Visual/Hygiene/Calibration checklist).
 * On approve these flow into the machine's after-maintenance rows in the month's 50a/50b
 * preventive-checklist record.
 */
data class QcChecklistDraft(
    val items: List<QcAfterMaintenanceItem> =
        ChecklistCatalog.afterMaintenanceChecks.map { QcAfterMaintenanceItem(it) },
    val notes: String = "",
    val afterPhotoLocalPath: String? = null,
) {
    /** Approve = every after-maintenance check is PASS. Photo/notes stay optional. */
    val canApprove: Boolean
        get() = items.isNotEmpty() && items.all { it.result == CheckResult.PASS }

    /** Disapprove = at least one check answered (so we never record an empty checklist). */
    val canDisapprove: Boolean
        get() = items.any { it.result != CheckResult.UNANSWERED }

    fun toSnapshot(): QcChecklistSnapshot = QcChecklistSnapshot(
        afterMaintenance = items.map {
            QcAfterMaintenanceDto(
                checkpoint = it.checkpoint,
                status = if (it.result == CheckResult.FAIL) "FAIL" else "PASS",
                remarks = it.remarks.takeIf { r -> r.isNotBlank() },
            )
        },
        notes = notes.takeIf { it.isNotBlank() },
    )
}
