package com.candorfoods.factoryops.data.local.dao

import androidx.room.*
import com.candorfoods.factoryops.data.local.entity.PmPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PmPlanDao {

    @Query("SELECT * FROM pm_plans WHERE isActive = 1 ORDER BY nextDueAt ASC")
    fun observeActivePlans(): Flow<List<PmPlanEntity>>

    @Query("SELECT * FROM pm_plans ORDER BY nextDueAt ASC")
    fun observeAllPlans(): Flow<List<PmPlanEntity>>

    @Query("SELECT * FROM pm_plans WHERE id = :id")
    suspend fun getPlanById(id: String): PmPlanEntity?

    @Query("SELECT * FROM pm_plans WHERE id = :id")
    fun observePlanById(id: String): Flow<PmPlanEntity?>

    @Query("SELECT * FROM pm_plans WHERE machineId = :machineId AND isActive = 1")
    fun observePlansForMachine(machineId: String): Flow<List<PmPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: PmPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(plans: List<PmPlanEntity>)

    @Query("UPDATE pm_plans SET lastCompletedAt = :ts, lastCompletedAtHours = :hours, nextDueAt = :nextDueAt WHERE id = :id")
    suspend fun updateLastCompleted(id: String, ts: Long, hours: Double?, nextDueAt: Long)

    @Query("SELECT COUNT(*) FROM pm_plans")
    suspend fun count(): Int

    /** Drop a provisional local row once the server has assigned its canonical id. */
    @Query("DELETE FROM pm_plans WHERE id = :id")
    suspend fun deleteById(id: String)
}
