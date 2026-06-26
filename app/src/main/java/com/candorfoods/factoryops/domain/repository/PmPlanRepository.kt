package com.candorfoods.factoryops.domain.repository

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

    suspend fun savePlan(plan: PmPlan): Result<PmPlan>
    suspend fun updateLastCompleted(planId: String, ts: Long, hours: Double?, nextDueAt: Long)

    suspend fun seedMockIfEmpty(seedPlans: List<PmPlan>)
}
