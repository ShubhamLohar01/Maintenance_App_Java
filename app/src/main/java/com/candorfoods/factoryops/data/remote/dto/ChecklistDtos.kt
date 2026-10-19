package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.domain.model.ChecklistRecordDetail
import com.candorfoods.factoryops.domain.model.ChecklistRecordItem
import com.candorfoods.factoryops.domain.model.ChecklistRecordSummary
import com.candorfoods.factoryops.domain.model.BreakdownSubmission
import com.candorfoods.factoryops.domain.model.ChecklistSubmission
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for POST /preventive-maintenance/checklists. Maps to the backend
 * `doc_preventive_maintenance` document (header fields) plus its checkpoint rows.
 */
@Serializable
data class ChecklistSubmissionDto(
    @SerialName("form_type")      val formType: String,        // "MONTHLY" | "QUARTERLY"
    @SerialName("doc_no")         val docNo: String,           // "CFPLA.C4.F.50a" | "...50b"
    @SerialName("status")         val status: String,          // "DRAFT" | "SUBMITTED"
    @SerialName("checklist_date") val checklistDate: String,   // "yyyy-MM-dd" - signed on
    @SerialName("done_by")        val doneBy: String,
    @SerialName("checked_by")     val checkedBy: String,
    @SerialName("verified_by")    val verifiedBy: String,
    @SerialName("remarks")        val remarks: String,
    @SerialName("items")          val items: List<ChecklistItemDto>,
)

@Serializable
data class ChecklistItemDto(
    @SerialName("section")        val section: String,
    @SerialName("equipment")      val equipment: String,
    @SerialName("sr_no")          val srNo: Int? = null,
    // This machine's maintenance period, ISO yyyy-MM-dd - the exact keys stored in the
    // backend's `rows` JSONB item. A machine serviced within one day carries the same
    // date at both ends. Defaulted so records written before the range existed still
    // deserialise; the backend fills both from the old single `equipment_date` on read.
    @SerialName("from_date") val equipmentDateFrom: String = "",
    @SerialName("to_date")   val equipmentDateTo: String = "",
    @SerialName("checkpoint")     val checkpoint: String,
    @SerialName("status")         val status: String,   // "OK" | "NOT_OK"
    @SerialName("remarks")        val remarks: String,
)

@Serializable
data class ChecklistSubmitResponseDto(
    @SerialName("id") val id: Int,
)

// ─── GET: list + detail ──────────────────────────────────────────────────────

@Serializable
data class ChecklistRecordSummaryDto(
    @SerialName("id")             val id: Int,
    @SerialName("form_type")      val formType: String,
    @SerialName("status")         val status: String = "SUBMITTED",
    @SerialName("doc_no")         val docNo: String = "",
    @SerialName("checklist_date") val checklistDate: String = "",
    // Defaulted: rows written before the range existed omit these, and an older
    // backend does not send them at all.
    @SerialName("checklist_date_from") val checklistDateFrom: String = "",
    @SerialName("checklist_date_to")   val checklistDateTo: String = "",
    @SerialName("done_by")        val doneBy: String = "",
    @SerialName("created_at")     val createdAt: String = "",
    // Defaulted: an older backend does not send these, and the card then hides
    // the progress line rather than claiming 0 / 0.
    @SerialName("filled_count")   val filledCount: Int = 0,
    @SerialName("total_count")    val totalCount: Int = 0,
)

@Serializable
data class ChecklistRecordDetailDto(
    @SerialName("id")             val id: Int,
    @SerialName("form_type")      val formType: String,
    @SerialName("status")         val status: String = "SUBMITTED",
    @SerialName("doc_no")         val docNo: String = "",
    @SerialName("checklist_date") val checklistDate: String = "",
    @SerialName("checklist_date_from") val checklistDateFrom: String = "",
    @SerialName("checklist_date_to")   val checklistDateTo: String = "",
    @SerialName("done_by")        val doneBy: String = "",
    @SerialName("checked_by")     val checkedBy: String = "",
    @SerialName("verified_by")    val verifiedBy: String = "",
    @SerialName("remarks")        val remarks: String = "",
    @SerialName("created_at")     val createdAt: String = "",
    @SerialName("items")          val items: List<ChecklistItemDto> = emptyList(),
)

// ifBlank fallbacks, not bare assignment: a record written before the range existed  // (or served by an older backend) carries only checklistDate, and a blank range would
// render as an empty "Period: —" on the detail screen and the PDF.



fun ChecklistRecordSummaryDto.toDomain() = ChecklistRecordSummary(
    id = id, formType = formType, status = status, docNo = docNo,
    checklistDate = checklistDate,
    checklistDateFrom = checklistDateFrom.ifBlank { checklistDate },
    checklistDateTo = checklistDateTo.ifBlank { checklistDateFrom.ifBlank { checklistDate } },
    doneBy = doneBy, createdAt = createdAt,
    filledCount = filledCount, totalCount = totalCount,
)

fun ChecklistRecordDetailDto.toDomain() = ChecklistRecordDetail(
    id = id, formType = formType, status = status, docNo = docNo, checklistDate = checklistDate,
    checklistDateFrom = checklistDateFrom.ifBlank { checklistDate },
    checklistDateTo = checklistDateTo.ifBlank { checklistDateFrom.ifBlank { checklistDate } },
    doneBy = doneBy, checkedBy = checkedBy, verifiedBy = verifiedBy,
    remarks = remarks, createdAt = createdAt,
    items = items.map {
        ChecklistRecordItem(
            section = it.section, equipment = it.equipment, srNo = it.srNo,
            equipmentDate = it.equipmentDateFrom,
            equipmentDateTo = it.equipmentDateTo,
            checkpoint = it.checkpoint, status = it.status, remarks = it.remarks,
        )
    },
)

fun ChecklistSubmission.toDto() = ChecklistSubmissionDto(
    formType      = formType,
    docNo         = docNo,
    status        = status.name,
    checklistDate = checklistDate,
    doneBy        = doneBy,
    checkedBy     = checkedBy,
    verifiedBy    = verifiedBy,
    remarks       = remarks,
    items = items.map {
        ChecklistItemDto(
            section       = it.section,
            equipment     = it.equipment,
            srNo          = it.srNo,
            // domain ChecklistItemSubmission.equipmentDate is this machine's START;
            // it serialises as the wire key `from_date`.
            equipmentDateFrom = it.equipmentDate,
            equipmentDateTo   = it.equipmentDateTo,
            checkpoint    = it.checkpoint,
            status        = it.status.name,
            remarks       = it.remarks,
        )
    },
)

// ─── Breakdown Maintenance Record (CFPLA.C4.F.06) ─────────────────────────────

@Serializable
data class BreakdownEntryDto(
    @SerialName("record_date")             val recordDate: String? = null,
    @SerialName("location")                val location: String = "",
    @SerialName("machine_name")            val machineName: String = "",
    @SerialName("equipment_model_no")      val equipmentModelNo: String = "",
    @SerialName("problem_in_brief")        val problemInBrief: String = "",
    @SerialName("type_of_maintenance")     val typeOfMaintenance: String = "",
    @SerialName("part_of_machine")         val partOfMachine: String = "",
    @SerialName("temporary_reason")        val temporaryReason: String = "",
    @SerialName("duration_start")          val durationStart: String = "",
    @SerialName("duration_end")            val durationEnd: String = "",
    @SerialName("machine_operator_sign")   val machineOperatorSign: String = "",
    @SerialName("maintenance_person_sign") val maintenancePersonSign: String = "",
    @SerialName("qc_clearance_sign")       val qcClearanceSign: String = "",
)

@Serializable
data class BreakdownSheetDto(
    @SerialName("doc_no")      val docNo: String = "CFPLA.C4.F.06",
    @SerialName("verified_by") val verifiedBy: String = "",
    @SerialName("entries")     val entries: List<BreakdownEntryDto>,
)

@Serializable
data class BreakdownSubmitResponseDto(
    @SerialName("ids") val ids: List<Int> = emptyList(),
)

fun BreakdownSubmission.toDto() = BreakdownSheetDto(
    docNo      = docNo,
    verifiedBy = verifiedBy,
    entries = entries.map {
        BreakdownEntryDto(
            recordDate            = it.recordDate.ifBlank { null },
            location              = it.location,
            machineName           = it.machineName,
            equipmentModelNo      = it.equipmentModelNo,
            problemInBrief        = it.problemInBrief,
            typeOfMaintenance     = it.typeOfMaintenance,
            partOfMachine         = it.partOfMachine,
            temporaryReason       = it.temporaryReason,
            durationStart         = it.durationStart,
            durationEnd           = it.durationEnd,
            machineOperatorSign   = it.machineOperatorSign,
            maintenancePersonSign = it.maintenancePersonSign,
            qcClearanceSign       = it.qcClearanceSign,
        )
    },
)

// ─── GET: Breakdown F.06 submitted records ────────────────────────────────────

@Serializable
data class BreakdownRecordDto(
    @SerialName("id")                      val id: Int,
    @SerialName("doc_no")                  val docNo: String = "CFPLA.C4.F.06",
    @SerialName("record_date")             val recordDate: String = "",
    @SerialName("location")                val location: String = "",
    @SerialName("machine_name")            val machineName: String = "",
    @SerialName("equipment_model_no")      val equipmentModelNo: String = "",
    @SerialName("problem_in_brief")        val problemInBrief: String = "",
    @SerialName("type_of_maintenance")     val typeOfMaintenance: String = "",
    @SerialName("part_of_machine")         val partOfMachine: String = "",
    @SerialName("temporary_reason")        val temporaryReason: String = "",
    @SerialName("duration_start")          val durationStart: String = "",
    @SerialName("duration_end")            val durationEnd: String = "",
    @SerialName("machine_operator_sign")   val machineOperatorSign: String = "",
    @SerialName("maintenance_person_sign") val maintenancePersonSign: String = "",
    @SerialName("qc_clearance_sign")       val qcClearanceSign: String = "",
    @SerialName("verified_by")             val verifiedBy: String = "",
    @SerialName("created_at")              val createdAt: String = "",
)

fun BreakdownRecordDto.toDomain() = BreakdownRecord(
    id = id, docNo = docNo, recordDate = recordDate, location = location,
    machineName = machineName, equipmentModelNo = equipmentModelNo,
    problemInBrief = problemInBrief, typeOfMaintenance = typeOfMaintenance,
    partOfMachine = partOfMachine, temporaryReason = temporaryReason,
    durationStart = durationStart, durationEnd = durationEnd,
    machineOperatorSign = machineOperatorSign, maintenancePersonSign = maintenancePersonSign,
    qcClearanceSign = qcClearanceSign, verifiedBy = verifiedBy, createdAt = createdAt,
)
