package com.candorfoods.factoryops.domain.model

/** Summary row for the preventive records list (GET /preventive-maintenance/checklists). */
data class ChecklistRecordSummary(
    val id: Int,
    val formType: String,      // "MONTHLY" | "QUARTERLY"
    val status: String,        // "DRAFT" | "SUBMITTED"
    val docNo: String,
    val checklistDate: String,
    val doneBy: String,
    val createdAt: String,
) {
    val isDraft: Boolean get() = status.equals("DRAFT", ignoreCase = true)
}

/** One checkpoint row inside a stored record. */
data class ChecklistRecordItem(
    val section: String,
    val equipment: String,
    val srNo: Int?,
    val equipmentDate: String,
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
    val doneBy: String,
    val checkedBy: String,
    val verifiedBy: String,
    val remarks: String,
    val createdAt: String,
    val items: List<ChecklistRecordItem>,
)
