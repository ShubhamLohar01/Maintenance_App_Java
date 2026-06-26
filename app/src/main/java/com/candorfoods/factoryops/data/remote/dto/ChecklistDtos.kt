package com.candorfoods.factoryops.data.remote.dto

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
    @SerialName("checklist_date") val checklistDate: String,   // "yyyy-MM-dd"
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
    @SerialName("equipment_date") val equipmentDate: String = "",  // ISO yyyy-MM-dd
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
    @SerialName("done_by")        val doneBy: String = "",
    @SerialName("created_at")     val createdAt: String = "",
)

@Serializable
data class ChecklistRecordDetailDto(
    @SerialName("id")             val id: Int,
    @SerialName("form_type")      val formType: String,
    @SerialName("status")         val status: String = "SUBMITTED",
    @SerialName("doc_no")         val docNo: String = "",
    @SerialName("checklist_date") val checklistDate: String = "",
    @SerialName("done_by")        val doneBy: String = "",
    @SerialName("checked_by")     val checkedBy: String = "",
    @SerialName("verified_by")    val verifiedBy: String = "",
    @SerialName("remarks")        val remarks: String = "",
    @SerialName("created_at")     val createdAt: String = "",
    @SerialName("items")          val items: List<ChecklistItemDto> = emptyList(),
)

fun ChecklistRecordSummaryDto.toDomain() = ChecklistRecordSummary(
    id = id, formType = formType, status = status, docNo = docNo,
    checklistDate = checklistDate, doneBy = doneBy, createdAt = createdAt,
)

fun ChecklistRecordDetailDto.toDomain() = ChecklistRecordDetail(
    id = id, formType = formType, status = status, docNo = docNo, checklistDate = checklistDate,
    doneBy = doneBy, checkedBy = checkedBy, verifiedBy = verifiedBy,
    remarks = remarks, createdAt = createdAt,
    items = items.map {
        ChecklistRecordItem(
            section = it.section, equipment = it.equipment, srNo = it.srNo,
            equipmentDate = it.equipmentDate,
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
            equipmentDate = it.equipmentDate,
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
