package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.domain.model.ChecklistRecordDetail
import com.candorfoods.factoryops.domain.model.ChecklistRecordSummary
import com.candorfoods.factoryops.domain.model.BreakdownSubmission
import com.candorfoods.factoryops.domain.model.ChecklistSubmission
import com.candorfoods.factoryops.domain.model.Result

interface ChecklistRepository {
    /**
     * Create (id == null) or update (id != null) a checklist record. Used for both
     * draft saves and the final submit — [ChecklistSubmission.status] carries which.
     * Returns the record id.
     */
    suspend fun save(submission: ChecklistSubmission, id: Int?): Result<Int>

    /** List stored preventive checklists (newest first). */
    suspend fun list(): Result<List<ChecklistRecordSummary>>

    /** Fetch one stored checklist with all its checkpoint rows. */
    suspend fun detail(id: Int): Result<ChecklistRecordDetail>

    /** Submit a Breakdown Maintenance Record sheet (CFPLA.C4.F.06). Returns the new row ids. */
    suspend fun submitBreakdown(submission: BreakdownSubmission): Result<List<Int>>

    /** List submitted Breakdown F.06 records (newest first). */
    suspend fun listBreakdowns(): Result<List<BreakdownRecord>>
}
