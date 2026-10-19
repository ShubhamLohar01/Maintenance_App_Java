package com.candorfoods.factoryops.domain.model

/** Summary row for the preventive records list (GET /preventive-maintenance/checklists). */
data class ChecklistRecordSummary(
    val id: Int,
    val formType: String,      // "MONTHLY" | "QUARTERLY"
    val status: String,        // "DRAFT" | "SUBMITTED"
    val docNo: String,
    val checklistDate: String,
    val checklistDateFrom: String = "",
    val checklistDateTo: String = "",
    val doneBy: String,
    val createdAt: String,
    // How much of the sheet is done, counted server-side. Default 0 so a build
    // talking to an older backend that omits them still parses, and simply hides
    // the line (see hasCounts).
    val filledCount: Int = 0,
    val totalCount: Int = 0,
) {
    val isDraft: Boolean get() = status.equals("DRAFT", ignoreCase = true)

    /** True when this record covers a span rather than a single day. */
    val hasDateRange: Boolean
        get() = checklistDateFrom.isNotBlank() &&
                checklistDateTo.isNotBlank() &&
                checklistDateFrom != checklistDateTo

    /** Floored at zero: a malformed document reporting more filled than total
     *  would otherwise render a negative "remaining", which reads as a bug. */
    val remainingCount: Int get() = (totalCount - filledCount).coerceAtLeast(0)

    /** False for an empty document, so the card never shows "0 / 0 filled". */
    val hasCounts: Boolean get() = totalCount > 0
}

/** One checkpoint row inside a stored record. */
data class ChecklistRecordItem(
    val section: String,
    val equipment: String,
    val srNo: Int?,
    val equipmentDate: String,
    val equipmentDateTo: String = "",
    val checkpoint: String,
    val status: String,        // "OK" | "NOT_OK"
    val remarks: String,
)

/** Full stored record (GET /preventive-maintenance/checklists/{id}). */
data class ChecklistRecordDetail(
    val id: Int,
    val formType: String,
    val status: String,        // "DRAFT" | "SUBMITTED"
    val docNo: String,
    val checklistDate: String,
    val checklistDateFrom: String = "",
    val checklistDateTo: String = "",
    val doneBy: String,
    val checkedBy: String,
    val verifiedBy: String,
    val remarks: String,
    val createdAt: String,
    val items: List<ChecklistRecordItem>,
) {
    /** True when this record covers a span rather than a single day. */
    val hasDateRange: Boolean
        get() = checklistDateFrom.isNotBlank() &&
                checklistDateTo.isNotBlank() &&
                checklistDateFrom != checklistDateTo
}
