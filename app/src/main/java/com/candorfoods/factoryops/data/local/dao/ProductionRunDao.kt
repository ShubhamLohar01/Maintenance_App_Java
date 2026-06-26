package com.candorfoods.factoryops.data.local.dao

import androidx.room.*
import com.candorfoods.factoryops.data.local.entity.ProductionRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductionRunDao {
    @Query("SELECT * FROM production_runs WHERE machineId = :machineId AND startedAt >= :startOfDay ORDER BY startedAt DESC")
    fun getTodaysRuns(machineId: String, startOfDay: Long): Flow<List<ProductionRunEntity>>

    @Query("SELECT * FROM production_runs WHERE machineId = :machineId AND startedAt >= :fromDate AND startedAt <= :toDate ORDER BY startedAt DESC")
    fun getRunsInRange(machineId: String, fromDate: Long, toDate: Long): Flow<List<ProductionRunEntity>>

    @Query("SELECT * FROM production_runs WHERE machineId = :machineId AND endedAt IS NULL LIMIT 1")
    fun getActiveRunForMachine(machineId: String): Flow<ProductionRunEntity?>

    @Query("SELECT * FROM production_runs WHERE machineId = :machineId AND endedAt IS NULL LIMIT 1")
    suspend fun getActiveRunForMachineOnce(machineId: String): ProductionRunEntity?

    @Query("SELECT * FROM production_runs WHERE id = :runId LIMIT 1")
    suspend fun getRunById(runId: String): ProductionRunEntity?

    @Query("SELECT * FROM production_runs WHERE endedAt IS NULL")
    suspend fun getAllActiveRuns(): List<ProductionRunEntity>

    /**
     * Sum of computed kWh for finished runs of `machineId` whose started_at falls
     * within a single calendar day `[dayStart, nextDayStart)`. Runs are attributed
     * to the day they STARTED (matches getTodaysRuns / the retry loop). Used to
     * (re)compute a per-machine daily total then upserted to the backend.
     */
    @Query("""
        SELECT COALESCE(SUM(computedKwh), 0.0)
        FROM production_runs
        WHERE machineId = :machineId
          AND startedAt >= :dayStart AND startedAt < :nextDayStart
          AND computedKwh IS NOT NULL
    """)
    suspend fun sumComputedKwhInRange(machineId: String, dayStart: Long, nextDayStart: Long): Double

    /**
     * Distinct machine IDs with at least one run started within `[dayStart, nextDayStart)`.
     * Drives the daily-kWh retry loop, which walks a few trailing days so a stop
     * whose POST failed offline (including across midnight) gets re-sent.
     */
    @Query("""
        SELECT DISTINCT machineId
        FROM production_runs
        WHERE startedAt >= :dayStart AND startedAt < :nextDayStart
    """)
    suspend fun getMachineIdsWithRunsInRange(dayStart: Long, nextDayStart: Long): List<String>

    @Query("SELECT COUNT(*) FROM production_runs WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    fun getPendingSyncCount(): Flow<Int>

    @Query("SELECT * FROM production_runs WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    suspend fun getPendingRunsOnce(): List<ProductionRunEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: ProductionRunEntity)

    @Update
    suspend fun update(run: ProductionRunEntity)

    @Query("UPDATE production_runs SET endedAt = :endedAt, computedKwh = :computedKwh, syncStatus = :syncStatus, updatedAt = :updatedAt WHERE id = :runId")
    suspend fun stopRun(runId: String, endedAt: Long, computedKwh: Double?, syncStatus: String, updatedAt: Long)

    @Query("UPDATE production_runs SET serverRunId = :serverRunId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setServerRunId(id: String, serverRunId: String, updatedAt: Long)

    @Query("UPDATE production_runs SET syncStatus = :syncStatus, updatedAt = :updatedAt WHERE id = :runId")
    suspend fun updateSyncStatus(runId: String, syncStatus: String, updatedAt: Long)

    @Query("UPDATE production_runs SET flagStatus = :flagStatus, updatedAt = :updatedAt WHERE id = :runId")
    suspend fun updateFlagStatus(runId: String, flagStatus: String, updatedAt: Long)
}
