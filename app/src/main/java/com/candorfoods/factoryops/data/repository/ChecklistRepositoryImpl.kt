package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.ChecklistApi
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.data.remote.dto.toDto
import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.domain.model.BreakdownSubmission
import com.candorfoods.factoryops.domain.model.ChecklistRecordDetail
import com.candorfoods.factoryops.domain.model.ChecklistRecordSummary
import com.candorfoods.factoryops.domain.model.ChecklistSubmission
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChecklistRepositoryImpl @Inject constructor(
    private val checklistApi: ChecklistApi,
    private val authPreferences: AuthPreferences,
) : ChecklistRepository {

    override suspend fun save(submission: ChecklistSubmission, id: Int?): Result<Int> = call { token ->
        val dto = submission.toDto()
        val responseId = if (id == null) {
            checklistApi.submit(token, dto).id
        } else {
            checklistApi.update(token, id, dto).id
        }
        Result.Success(responseId)
    }

    override suspend fun list(status: String?): Result<List<ChecklistRecordSummary>> = call { token ->
        Result.Success(checklistApi.list(token, status).map { it.toDomain() })
    }

    override suspend fun detail(id: Int): Result<ChecklistRecordDetail> = call { token ->
        Result.Success(checklistApi.detail(token, id).toDomain())
    }

    override suspend fun submitBreakdown(submission: BreakdownSubmission): Result<List<Int>> = call { token ->
        Result.Success(checklistApi.submitBreakdown(token, submission.toDto()).ids)
    }

    override suspend fun listBreakdowns(): Result<List<BreakdownRecord>> = call { token ->
        Result.Success(checklistApi.listBreakdowns(token).map { it.toDomain() })
    }

    private inline fun <T> call(block: (token: String) -> Result<T>): Result<T> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        return try {
            block("Bearer $token")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Request failed", e)
        }
    }
}
