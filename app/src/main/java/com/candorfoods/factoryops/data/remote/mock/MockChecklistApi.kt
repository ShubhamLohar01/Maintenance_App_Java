package com.candorfoods.factoryops.data.remote.mock

import com.candorfoods.factoryops.data.remote.api.ChecklistApi
import com.candorfoods.factoryops.data.remote.dto.BreakdownRecordDto
import com.candorfoods.factoryops.data.remote.dto.BreakdownSheetDto
import com.candorfoods.factoryops.data.remote.dto.BreakdownSubmitResponseDto
import com.candorfoods.factoryops.data.remote.dto.ChecklistRecordDetailDto
import com.candorfoods.factoryops.data.remote.dto.ChecklistRecordSummaryDto
import com.candorfoods.factoryops.data.remote.dto.ChecklistSubmissionDto
import com.candorfoods.factoryops.data.remote.dto.ChecklistSubmitResponseDto
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/** Stand-in until the backend `doc_preventive_maintenance` endpoints are live. */
class MockChecklistApi : ChecklistApi {
    private val counter = AtomicInteger(1000)

    override suspend fun submit(
        token: String,
        body: ChecklistSubmissionDto,
    ): ChecklistSubmitResponseDto {
        delay(400)
        return ChecklistSubmitResponseDto(id = counter.incrementAndGet())
    }

    override suspend fun update(
        token: String,
        id: Int,
        body: ChecklistSubmissionDto,
    ): ChecklistSubmitResponseDto {
        delay(400)
        return ChecklistSubmitResponseDto(id = id)
    }

    override suspend fun list(token: String): List<ChecklistRecordSummaryDto> {
        delay(300)
        return emptyList()
    }

    override suspend fun detail(token: String, id: Int): ChecklistRecordDetailDto {
        delay(300)
        return ChecklistRecordDetailDto(id = id, formType = "MONTHLY")
    }

    override suspend fun submitBreakdown(
        token: String,
        body: BreakdownSheetDto,
    ): BreakdownSubmitResponseDto {
        delay(400)
        return BreakdownSubmitResponseDto(ids = body.entries.map { counter.incrementAndGet() })
    }

    override suspend fun listBreakdowns(token: String): List<BreakdownRecordDto> {
        delay(300)
        return emptyList()
    }
}
