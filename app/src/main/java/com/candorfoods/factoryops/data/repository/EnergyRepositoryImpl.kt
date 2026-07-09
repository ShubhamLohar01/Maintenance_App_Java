package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.local.entity.ProductionRunEntity
import com.candorfoods.factoryops.data.local.entity.RecessStateEntity
import com.candorfoods.factoryops.data.remote.api.EnergyApi
import com.candorfoods.factoryops.data.remote.dto.RunStartRequestDto
import com.candorfoods.factoryops.data.remote.dto.RunStopRequestDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.*
import com.candorfoods.factoryops.domain.repository.EnergyRepository
import com.candorfoods.factoryops.util.Constants
import com.candorfoods.factoryops.util.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the backend: kWh = ratedKw × hours × power factor (0.99). Uses the
 * rating snapshotted onto the run at Start, so Stop works offline. A null rating
 * yields null kWh (the server computes the authoritative value on sync).
 */
fun computeRunKwh(ratedKw: Double?, startedAt: Long, endedAt: Long): Double? {
    if (ratedKw == null) return null
    val hours = (endedAt - startedAt) / 3_600_000.0
    return ratedKw * hours * Constants.POWER_FACTOR
}

@Singleton
class EnergyRepositoryImpl @Inject constructor(
    private val energyApi: EnergyApi,
    private val db: AppDatabase,
    private val authPreferences: AuthPreferences,
) : EnergyRepository {

    override suspend fun getActiveRuns(): Result<List<ActiveRun>> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            Result.Success(energyApi.getActiveRuns(token).map { it.toDomain() })
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load active runs", e)
        }
    }

    override suspend fun startProductionRun(
        assetId: String, assetName: String, ratedKw: Double?, scheduledEndAt: Long
    ): Result<ProductionRun> {
        return try {
            val now = System.currentTimeMillis()
            val localId = UUID.randomUUID().toString()          // = clientRunId
            val entity = ProductionRunEntity(
                id = localId, machineId = assetId,
                operatorId = authPreferences.getUserId().orEmpty(),
                startedAt = now, scheduledEndAt = scheduledEndAt,
                endedAt = null, computedKwh = null,
                syncStatus = "PENDING", flagStatus = null,
                createdAt = now, updatedAt = now,
                assetName = assetName, ratedKw = ratedKw, serverRunId = null,
            )
            db.productionRunDao().insert(entity)
            // Push now (best-effort) so the run lands in mt_machine_daily_kwh
            // immediately. Offline -> stays PENDING and SyncWorker retries it.
            runCatching { syncPendingRuns() }
            Result.Success(entity.toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to start run", e)
        }
    }

    override suspend fun stopProductionRun(runId: String, endedAt: Long): Result<ProductionRun> {
        return try {
            val run = db.productionRunDao().getRunById(runId)
                ?: return Result.Error("Run not found")
            val computedKwh = computeRunKwh(run.ratedKw, run.startedAt, endedAt)
            val now = System.currentTimeMillis()
            db.productionRunDao().stopRun(
                runId = runId, endedAt = endedAt, computedKwh = computedKwh,
                syncStatus = "PENDING", updatedAt = now,
            )
            // Per-day kWh is now recorded entirely by the backend on Stop (each run is a
            // row in mt_machine_daily_kwh); there is no separate daily-kwh upsert anymore.
            // Push now (best-effort) so the Stop reaches the backend immediately;
            // offline -> stays PENDING and SyncWorker retries it.
            runCatching { syncPendingRuns() }
            Result.Success(
                run.copy(
                    endedAt = endedAt, computedKwh = computedKwh,
                    syncStatus = "PENDING", updatedAt = now,
                ).toDomain()
            )
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to stop run", e)
        }
    }

    override suspend fun stopActiveRunById(serverRunId: String, endedAt: Long): Result<Unit> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            energyApi.stopRun(token, serverRunId, RunStopRequestDto(endedAt))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to stop run", e)
        }
    }

    // ─── Recess (pause/resume) ───────────────────────────────────────────────────
    // A production "session" is one or more run segments (rows in production_runs).
    // Recess stops the current segment and banks its active time; Resume opens a new
    // segment; End stops the segment and clears the session. The recess gap belongs to
    // no segment, so it is automatically excluded from kWh (each segment's kWh is real).

    override fun observeRecessState(machineId: String): Flow<RecessState?> =
        db.recessStateDao().observe(machineId).map { it?.toDomain() }

    override suspend fun recessProduction(machineId: String): Result<Unit> {
        return try {
            val run = db.productionRunDao().getActiveRunForMachineOnce(machineId)
                ?: return Result.Error("No active run to pause")
            val now = System.currentTimeMillis()
            // Stop the current segment (reuses the offline-safe stop + sync path).
            val stop = stopProductionRun(run.id, now)
            if (stop is Result.Error) return Result.Error(stop.message)
            val segmentMs = (now - run.startedAt).coerceAtLeast(0L)
            val existing = db.recessStateDao().getOnce(machineId)
            db.recessStateDao().upsert(
                RecessStateEntity(
                    machineId = machineId,
                    operatorId = authPreferences.getUserId().orEmpty(),
                    onRecess = true,
                    accumulatedActiveMs = (existing?.accumulatedActiveMs ?: 0L) + segmentMs,
                    recessStartedAt = now,
                    updatedAt = now,
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to pause production", e)
        }
    }

    override suspend fun resumeProduction(
        assetId: String, assetName: String, ratedKw: Double?, scheduledEndAt: Long
    ): Result<Unit> {
        return try {
            // Open a new segment first; only flip the session out of recess if it starts.
            val start = startProductionRun(assetId, assetName, ratedKw, scheduledEndAt)
            if (start is Result.Error) return Result.Error(start.message)
            val now = System.currentTimeMillis()
            val existing = db.recessStateDao().getOnce(assetId)
            db.recessStateDao().upsert(
                RecessStateEntity(
                    machineId = assetId,
                    operatorId = authPreferences.getUserId().orEmpty(),
                    onRecess = false,
                    accumulatedActiveMs = existing?.accumulatedActiveMs ?: 0L,
                    recessStartedAt = null,
                    updatedAt = now,
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to resume production", e)
        }
    }

    override suspend fun endProductionSession(machineId: String): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val run = db.productionRunDao().getActiveRunForMachineOnce(machineId)
            if (run != null) {
                val stop = stopProductionRun(run.id, now)
                if (stop is Result.Error) return Result.Error(stop.message)
            }
            db.recessStateDao().deleteForMachine(machineId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to stop production", e)
        }
    }

    override suspend fun clearRecessSession(machineId: String) {
        db.recessStateDao().deleteForMachine(machineId)
    }

    private fun RecessStateEntity.toDomain() = RecessState(
        machineId = machineId,
        operatorId = operatorId,
        onRecess = onRecess,
        accumulatedActiveMs = accumulatedActiveMs,
        recessStartedAt = recessStartedAt,
    )

    override fun getTodaysRuns(machineId: String): Flow<List<ProductionRun>> {
        val startOfDay = DateTimeFormatter.startOfDayMillis(System.currentTimeMillis())
        return db.productionRunDao().getTodaysRuns(machineId, startOfDay)
            .map { list -> list.map { it.toDomain() } }
    }

    override fun getActiveRunForMachine(machineId: String): Flow<ProductionRun?> =
        db.productionRunDao().getActiveRunForMachine(machineId).map { it?.toDomain() }

    override fun getMachineHistory(machineId: String, fromDate: Long, toDate: Long): Flow<Result<List<ProductionRun>>> =
        db.productionRunDao().getRunsInRange(machineId, fromDate, toDate)
            .map<List<ProductionRunEntity>, Result<List<ProductionRun>>> { list ->
                Result.Success(list.map { it.toDomain() })
            }
            .catch { emit(Result.Error(it.message ?: "Failed to load history")) }

    override fun getPendingSyncCount(): Flow<Int> = db.productionRunDao().getPendingSyncCount()

    override suspend fun syncPendingRuns(): Result<Unit> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            val pending = db.productionRunDao().getPendingRunsOnce()
            for (run in pending) {
                db.productionRunDao().updateSyncStatus(run.id, "SYNCING", System.currentTimeMillis())
                try {
                    if (run.endedAt == null) {
                        val resp = energyApi.startRun(
                            token, RunStartRequestDto(run.machineId, run.id, run.startedAt, run.scheduledEndAt)
                        )
                        db.productionRunDao().setServerRunId(run.id, resp.runId, System.currentTimeMillis())
                    } else {
                        // Stop must target the SERVER run_id. If start never synced,
                        // start now (idempotent on client_run_id) to obtain it.
                        val existing = run.serverRunId
                        val serverRunId = if (existing != null) existing else {
                            val resp = energyApi.startRun(
                                token, RunStartRequestDto(run.machineId, run.id, run.startedAt, run.scheduledEndAt)
                            )
                            db.productionRunDao().setServerRunId(run.id, resp.runId, System.currentTimeMillis())
                            resp.runId
                        }
                        energyApi.stopRun(token, serverRunId, RunStopRequestDto(run.endedAt))
                    }
                    db.productionRunDao().updateSyncStatus(run.id, "SYNCED", System.currentTimeMillis())
                } catch (e: Exception) {
                    db.productionRunDao().updateSyncStatus(run.id, "FAILED", System.currentTimeMillis())
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Sync failed", e)
        }
    }

    private fun ProductionRunEntity.toDomain() = ProductionRun(
        id = id, machineId = machineId, operatorId = operatorId,
        startedAt = startedAt, scheduledEndAt = scheduledEndAt,
        endedAt = endedAt, computedKwh = computedKwh,
        syncStatus = when (syncStatus) {
            "SYNCING" -> SyncStatus.SYNCING; "SYNCED" -> SyncStatus.SYNCED
            "FAILED"  -> SyncStatus.FAILED;  else     -> SyncStatus.PENDING
        },
        flagStatus = when (flagStatus) {
            "MISSED_START" -> FlagStatus.MISSED_START; "MISSED_STOP" -> FlagStatus.MISSED_STOP
            "RESOLVED"     -> FlagStatus.RESOLVED;      else          -> null
        },
        createdAt = createdAt, updatedAt = updatedAt
    )
}
