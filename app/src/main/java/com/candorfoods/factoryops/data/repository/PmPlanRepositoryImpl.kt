package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.local.entity.PmPlanEntity
import com.candorfoods.factoryops.data.remote.api.PmSyncApi
import com.candorfoods.factoryops.data.remote.dto.PmPlanDto
import com.candorfoods.factoryops.data.remote.dto.pmPlanDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.AssignedPmPlan
import com.candorfoods.factoryops.domain.model.PmPlan
import com.candorfoods.factoryops.domain.model.PmTriggerType
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.SyncStatus
import com.candorfoods.factoryops.domain.repository.PmPlanRepository
import com.candorfoods.factoryops.domain.repository.PmTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PmPlanRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val api: PmSyncApi,
    private val templateRepo: PmTemplateRepository,
    private val authPreferences: AuthPreferences,
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

    override suspend fun getAssignedPlans(techId: String): Result<List<AssignedPmPlan>> = try {
        val token = "Bearer ${authPreferences.getToken().orEmpty()}"
        val plans = api.listPlans(token)
            .filter { it.assignedTechnicianId == techId && it.isActive }
            .map { dto ->
                val (plan, template) = dto.toDomain()
                AssignedPmPlan(
                    plan = plan,
                    machineName = dto.machineName,
                    description = template.description,
                    items = template.items,
                )
            }
        Result.Success(plans)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to load PM plans", e)
    }

    override suspend fun refreshAllPlans(): Result<Unit> = try {
        val token = "Bearer ${authPreferences.getToken().orEmpty()}"
        for (dto in api.listPlans(token)) {
            mirrorServerPlan(dto)
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to refresh PM plans", e)
    }

    /**
     * Mirror one server plan into Room: checklist template first, then the plan row,
     * both marked SYNCED (they came FROM the server — don't re-push). If the server
     * echoes a client_ref matching a provisional local row (a create whose response we
     * missed), that row is dropped in favour of the server-id one — no duplicates.
     */
    private suspend fun mirrorServerPlan(dto: PmPlanDto): PmPlan {
        dto.clientRef?.takeIf { it.isNotBlank() && it != dto.id }?.let { dao.deleteById(it) }
        val (plan, template) = dto.toDomain()
        templateRepo.saveTemplate(template, template.items)
        dao.upsert(plan.toEntity().copy(syncStatus = SyncStatus.SYNCED.name))
        return plan
    }

    override suspend fun savePlan(plan: PmPlan): Result<PmPlan> {
        // Create vs edit: a row already SYNCED carries a server-assigned id -> PUT it.
        // Anything else (new, or an offline create that never reached the server) is a
        // CREATE: plan.id is only the app's provisional uuid, sent as client_ref.
        val isOnServer = try {
            dao.getPlanById(plan.id)?.syncStatus == SyncStatus.SYNCED.name
        } catch (e: Exception) {
            return Result.Error(e.message ?: "Save failed", e)
        }

        // Offline-first: persist locally so the plan shows immediately and the retry
        // path has the provisional row to work from. A server-known row KEEPS its
        // SYNCED marker even while this edit is in flight — that marker is what routes
        // a retry back to PUT (flipping it to PENDING would make the retry re-CREATE
        // under client_ref = a server id, duplicating the plan).
        try {
            dao.upsert(
                plan.toEntity().copy(
                    syncStatus = if (isOnServer) SyncStatus.SYNCED.name else SyncStatus.PENDING.name
                )
            )
        } catch (e: Exception) {
            return Result.Error(e.message ?: "Save failed", e)
        }

        // The checklist items live on the template (a plan is stored locally as a
        // template+plan pair), so load it and merge via pmPlanDto().
        val template = templateRepo.getTemplateById(plan.templateId)
            ?: return Result.Success(plan)   // no template (shouldn't happen from the editor) -> local only
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            val response = if (isOnServer) {
                // Server already knows this plan (server id, or legacy plan-<uuid>).
                api.updatePlan(token, plan.id, pmPlanDto(plan, template))
            } else {
                // Server assigns the real id ('PLANAA001'…); our uuid rides along as the
                // idempotency key so a lost-response retry can't create a duplicate.
                api.createPlan(token, pmPlanDto(plan, template, clientRef = plan.id))
            }
            // Adopt the server's canonical row (drops the provisional uuid row on create).
            Result.Success(mirrorServerPlan(response))
        } catch (e: Exception) {
            // Saved on device but the server didn't get it — surface it so the supervisor
            // retries (idempotent via client_ref) rather than silently diverging.
            Result.Error(
                "Saved on device, but couldn't sync to the server: ${e.message ?: "network error"}",
                e,
            )
        }
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
