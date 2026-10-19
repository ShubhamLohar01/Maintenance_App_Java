package com.candorfoods.factoryops.util.pdf

import com.candorfoods.factoryops.domain.model.ChecklistRecordDetail
import com.candorfoods.factoryops.presentation.checklist.BreakdownFormSpec
import com.candorfoods.factoryops.presentation.checklist.ChecklistCatalog

/** The controlled-copy formats we can export, per plant. */
enum class PdfForm {
    PREVENTIVE_MONTHLY, PREVENTIVE_QUARTERLY, BREAKDOWN,     // W-202
    PREVENTIVE_A185, BREAKDOWN_A185,                          // A-185
}

/** Static header metadata reproduced from the controlled-copy PDFs in `public/`. */
data class DocMeta(
    val documentNo: String,
    val formatTitle: String,
    val issueNo: String,
    val revisionNo: String,
    val issueDate: String,
    val revisionDate: String,
    val landscape: Boolean,
)

fun docMetaFor(form: PdfForm): DocMeta = when (form) {
    PdfForm.PREVENTIVE_MONTHLY -> DocMeta(
        documentNo = "CFPLA.C4.F.50a",
        formatTitle = "Preventive Maintenance Checklist- Monthly",
        issueNo = "06", revisionNo = "05",
        issueDate = "01/11/2027", revisionDate = "13/12/2025",
        landscape = false,
    )
    PdfForm.PREVENTIVE_QUARTERLY -> DocMeta(
        documentNo = "CFPLA.C4.F.50b",
        formatTitle = "Preventive Maintenance Checklist-Quarterly",
        issueNo = "05", revisionNo = "04",
        issueDate = "01/11/2027", revisionDate = "01/10/2025",
        landscape = false,
    )
    PdfForm.BREAKDOWN -> DocMeta(
        documentNo = "CFPLA.C4.F.06",
        formatTitle = "Breakdown Maintainance Record",
        issueNo = "04", revisionNo = "03",
        issueDate = "01/11/2017", revisionDate = "01/10/2025",
        landscape = true,
    )
    // A-185's one combined document; the Quarterly and Monthly blocks share its header.
    PdfForm.PREVENTIVE_A185 -> DocMeta(
        documentNo = "CFPLB.C4.F.41",
        formatTitle = "Preventive Maintenance Checklist- Quarterly and monthly",
        issueNo = "03", revisionNo = "02",
        issueDate = "04/08/2021", revisionDate = "15/04/2026",
        landscape = false,
    )
    // A-185's own controlled copy of the same register (Rev 03, 02/02/2026).
    PdfForm.BREAKDOWN_A185 -> DocMeta(
        documentNo = "CFPLB.C4.F.61",
        formatTitle = "Breakdown  Maintenance Record",
        issueNo = "04", revisionNo = "03",
        issueDate = "01/11/2017", revisionDate = "02/02/2026",
        landscape = true,
    )
}

/** Map a stored record's `formType` (the ChecklistType name) to a [PdfForm]. */
fun pdfFormFor(formType: String): PdfForm = when (formType.trim().uppercase()) {
    "QUARTERLY"                        -> PdfForm.PREVENTIVE_QUARTERLY
    "A185_QUARTERLY", "A185_MONTHLY"   -> PdfForm.PREVENTIVE_A185
    else                               -> PdfForm.PREVENTIVE_MONTHLY
}

/** A flattened render row for the preventive body table. */
sealed interface PreventiveRow {
    data class SectionHeader(val title: String) : PreventiveRow
    data class EquipmentHeader(val title: String) : PreventiveRow
    data class SubHeader(val title: String) : PreventiveRow          // e.g. "After maintenance" within a machine
    data class Checkpoint(val name: String, val status: String, val remarks: String) : PreventiveRow
}

/**
 * Flatten a stored record into ordered render rows, mirroring the grouping used by
 * PreventiveDetailScreen: a SectionHeader when the section changes, an EquipmentHeader
 * when section/equipment/srNo changes, then one Checkpoint per item.
 */
fun flattenPreventive(detail: ChecklistRecordDetail): List<PreventiveRow> {
    val out = mutableListOf<PreventiveRow>()
    detail.items.forEachIndexed { index, item ->
        val prev = detail.items.getOrNull(index - 1)
        val newSection = prev == null || prev.section != item.section
        val newEquipment = newSection || prev?.equipment != item.equipment || prev?.srNo != item.srNo
        if (newSection && item.section.isNotBlank()) {
            out.add(PreventiveRow.SectionHeader(item.section.uppercase()))
        }
        if (newEquipment) {
            val name = buildString {
                if (item.srNo != null) append("${item.srNo}. ")
                append(item.equipment)
                if (item.equipmentDate.isNotBlank()) {
                    append("  ·  ${item.equipmentDate}")
                    if (item.equipmentDateTo.isNotBlank() &&
                        item.equipmentDateTo != item.equipmentDate
                    ) append(" to ${item.equipmentDateTo}")
                }
            }
            out.add(PreventiveRow.EquipmentHeader(name))
        }
        // Sub-label the after-maintenance block once per machine (they follow its normal checks).
        val isAfter = item.checkpoint in ChecklistCatalog.afterMaintenanceChecks
        val prevAfter = !newEquipment && (prev?.checkpoint in ChecklistCatalog.afterMaintenanceChecks)
        if (isAfter && !prevAfter) {
            out.add(PreventiveRow.SubHeader("After maintenance"))
        }
        out.add(
            PreventiveRow.Checkpoint(
                name = item.checkpoint.ifBlank { "General check" },
                status = if (item.status.trim().uppercase() == "OK") "OK" else "NOT OK",
                remarks = item.remarks,
            )
        )
    }
    return out
}

/** One breakdown register row, already trimmed/derived from the form entries. */
data class BreakdownRow(
    val srNo: Int,
    val date: String,
    val location: String,
    val machineName: String,
    val equipmentModel: String,
    val problem: String,
    val typeOfMaintenance: String,
    val partOfMachine: String,
    val reason: String,
    val durationStart: String,
    val durationEnd: String,
    val machineOperatorSign: String,
    val maintenancePersonSign: String,
    val qcClearanceSign: String,
)

/** Render input for the breakdown PDF (built by the ViewModel from on-screen entries). */
data class BreakdownDocData(
    val docNo: String,
    val verifiedBy: String,
    val rows: List<BreakdownRow>,
    /** Picks the controlled-copy header block; the columns are the same either way. */
    val spec: BreakdownFormSpec = BreakdownFormSpec.W202,
)

/** Build a filesystem-safe output filename per form. */
fun pdfFileName(form: PdfForm, date: String, recordId: Int?): String {
    val safeDate = date.ifBlank { "record" }.trim().replace(Regex("[^A-Za-z0-9_-]"), "-")
    return when (form) {
        PdfForm.PREVENTIVE_MONTHLY   -> "Preventive_50a_Monthly_${safeDate}_rec${recordId ?: 0}.pdf"
        PdfForm.PREVENTIVE_QUARTERLY -> "Preventive_50b_Quarterly_${safeDate}_rec${recordId ?: 0}.pdf"
        PdfForm.BREAKDOWN            -> "Breakdown_F06_${safeDate}.pdf"
        PdfForm.PREVENTIVE_A185      -> "Preventive_F41_A185_${safeDate}_rec${recordId ?: 0}.pdf"
        PdfForm.BREAKDOWN_A185       -> "Breakdown_F61_A185_${safeDate}.pdf"
    }
}

/** True when a row of [rowHeight] starting at [cursorY] fits above [pageBottom]. */
fun fits(cursorY: Float, rowHeight: Float, pageBottom: Float): Boolean =
    cursorY + rowHeight <= pageBottom
