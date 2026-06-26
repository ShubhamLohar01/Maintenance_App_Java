package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.entity.PmPlanEntity
import com.candorfoods.factoryops.domain.model.PmPlan
import com.candorfoods.factoryops.domain.model.PmTriggerType
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.SyncStatus
import com.candorfoods.factoryops.domain.repository.PmPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PmPlanRepositoryImpl @Inject constructor(
    private val db: AppDatabase
) : PmPlanRepository {

    private val dao get() = db.pmPlanDao()

    override fun observeAllPlans(): Flow<List<PmPlan>> =
        dao.observeAllPlans().map { list -> list.map { it.toDomain() } }

    override fun observeActivePlans(): Flow<List<PmPlan>> =
        dao.observeActivePlans().map { list -> list.map { it.toDomain() } }

    override fun observePlanById(id: String): Flow<PmPlan?> =
        dao.observePlanById(id).map { it?.toDomain() }

    override fun observePlansForMachine(machineId: String): Flow<List<PmPlan>> =
        dao.observePlansForMachine(machineId).map { list -> list.map { it.toDomain() } }

    override suspend fun getPlanById(id: String): PmPlan? = dao.getPlanById(id)?.toDomain()

    override suspend fun getActivePlans(): List<PmPlan> =
        dao.observeActivePlans().first().map { it.toDomain() }

    override suspend fun savePlan(plan: PmPlan): Result<PmPlan> = try {
        dao.upsert(plan.toEntity())
        Result.Success(plan)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Save failed", e)
    }

    override suspend fun updateLastCompleted(planId: String, ts: Long, hours: Double?, nextDueAt: Long) {
        dao.updateLastCompleted(planId, ts, hours, nextDueAt)
    }

    override suspend fun seedMockIfEmpty(seedPlans: List<PmPlan>) {
        if (dao.count() > 0) return
        dao.upsertAll(seedPlans.map { it.toEntity() })
    }

    private fun PmPlanEntity.toDomain() = PmPlan(
        id = id,
        machineId = machineId,
        templateId = templateId,
        triggerType = runCatching { PmTriggerType.valueOf(triggerType) }.getOrDefault(PmTriggerType.TIME),
        triggerInterval = triggerInterval,
        lastCompletedAt = lastCompletedAt,
        lastCompletedAtHours = lastCompletedAtHours,
        nextDueAt = nextDueAt,
        assignedTechnicianId = assignedTechnicianId,
        isActive = isActive,
        createdAt = createdAt
    )

    private fun PmPlan.toEntity() = PmPlanEntity(
        id = id,
        machineId = machineId,
        templateId = templateId,
        triggerType = triggerType.name,
        triggerInterval = triggerInterval,
        lastCompletedAt = lastCompletedAt,
        lastCompletedAtHours = lastCompletedAtHours,
        nextDueAt = nextDueAt,
        assignedTechnicianId = assignedTechnicianId,
        isActive = isActive,
        createdAt = createdAt,
        syncStatus = SyncStatus.PENDING.name
    )
}
