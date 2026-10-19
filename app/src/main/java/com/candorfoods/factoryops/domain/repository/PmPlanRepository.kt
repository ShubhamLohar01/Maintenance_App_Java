package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.AssignedPmPlan
import com.candorfoods.factoryops.domain.model.PmPlan
import com.candorfoods.factoryops.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface PmPlanRepository {

    fun observeAllPlans(): Flow<List<PmPlan>>
    fun observeActivePlans(): Flow<List<PmPlan>>
    fun observePlanById(id: String): Flow<PmPlan?>
    fun observePlansForMachine(machineId: String): Flow<List<PmPlan>>
    suspend fun getPlanById(id: String): PmPlan?
    suspend fun getActivePlans(): List<PmPlan>

    /**
     * Fetch the active PM plans assigned to a technician from the backend (mt_pm_plan via
     * GET /pm/plans). Lets a technician SEE an assignment the moment a supervisor makes it,
     * before the work-order generator turns it into a due task. Carries the checklist
     * items + description for the plan-detail sheet.
     */
    suspend fun getAssignedPlans(techId: String): Result<List<AssignedPmPlan>>

    /**
     * Mirror ALL plans from the backend (mt_pm_plan via GET /pm/plans) into Room —
     * plan row + its checklist template. Keeps the supervisor's Plan tab equal to the
     * database instead of only what this device created (so plans survive reinstalls
     * and show on every supervisor device). Best-effort + offline-safe.
     */
    suspend fun refreshAllPlans(): Result<Unit>

    suspend fun savePlan(plan: PmPlan): Result<PmPlan>
    suspend fun updateLastCompleted(planId: String, ts: Long, hours: Double?, nextDueAt: Long)

    suspend fun seedMockIfEmpty(seedPlans: List<PmPlan>)
}
