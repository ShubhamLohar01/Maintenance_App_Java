package com.candorfoods.factoryops.data.local.dao

import androidx.room.*
import com.candorfoods.factoryops.data.local.entity.PmWoTaskLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PmWoTaskLogDao {

    @Query("SELECT * FROM pm_wo_task_logs WHERE woId = :woId ORDER BY orderIndex ASC")
    fun observeForWo(woId: String): Flow<List<PmWoTaskLogEntity>>

    @Query("SELECT * FROM pm_wo_task_logs WHERE woId = :woId ORDER BY orderIndex ASC")
    suspend fun getForWo(woId: String): List<PmWoTaskLogEntity>

    @Query("SELECT * FROM pm_wo_task_logs WHERE id = :id")
    suspend fun getById(id: String): PmWoTaskLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: PmWoTaskLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<PmWoTaskLogEntity>)

    @Query("DELETE FROM pm_wo_task_logs WHERE woId = :woId")
    suspend fun deleteForWo(woId: String)

    @Query(
        """
        UPDATE pm_wo_task_logs
        SET status = :status,
            measurementValue = :measurementValue,
            photoLocalPath = :photoLocalPath,
            notes = :notes,
            completedAt = :ts,
            completedBy = :userId,
            syncStatus = 'PENDING'
        WHERE id = :id
        """
    )
    suspend fun updateLogResult(
        id: String,
        status: String,
        measurementValue: Double?,
        photoLocalPath: String?,
        notes: String?,
        ts: Long,
        userId: String
    )
}

