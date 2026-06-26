package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.local.entity.MachineEntity
import com.candorfoods.factoryops.data.remote.api.MachineApi
import com.candorfoods.factoryops.data.remote.dto.MachineDto
import com.candorfoods.factoryops.domain.model.*
import com.candorfoods.factoryops.domain.repository.MachineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MachineRepositoryImpl @Inject constructor(
    private val machineApi: MachineApi,
    private val db: AppDatabase,
    private val authPreferences: AuthPreferences
) : MachineRepository {

    override fun getAssignedMachines(): Flow<Result<List<Machine>>> =
        db.machineDao().getAllMachines()
            .map<List<MachineEntity>, Result<List<Machine>>> { entities ->
                Result.Success(entities.map { it.toDomain() })
            }
            .onStart {
                emit(Result.Loading)
                // Refresh from the backend every time the list is observed so edits
                // made in the DB show up — not only on the first launch when the
                // cache is empty. syncMachines() returns a Result and never throws,
                // so a failed refresh (e.g. offline) just leaves the cached rows in
                // place and the Room flow still emits them.
                syncMachines()
            }
            .catch { emit(Result.Error(it.message ?: "Failed to load machines")) }

    override fun getMachineById(machineId: String): Flow<Machine?> =
        db.machineDao().getMachineById(machineId).map { it?.toDomain() }

    override fun observeAllMachines(): Flow<List<Machine>> =
        db.machineDao().getAllMachines().map { list -> list.map { it.toDomain() } }

    override suspend fun syncMachines(): Result<Unit> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            val machines = machineApi.getAssignedMachines(token)
            db.machineDao().insertAll(machines.map { it.toEntity() })
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Sync failed", e)
        }
    }

    override suspend fun updateMachineStatus(machineId: String, status: String) {
        db.machineDao().updateStatus(machineId, status, System.currentTimeMillis())
    }

    private fun MachineEntity.toDomain() = Machine(
        id = id, code = code, name = name, location = location, building = building, plantId = plantId,
        machineType = runCatching { MachineType.valueOf(machineType) }.getOrDefault(MachineType.OTHER),
        category = category,
        ratedKw = ratedKw, loadFactor = loadFactor,
        loadFactorSource = when (loadFactorSource) {
            "SPOT_MEASURED" -> LoadFactorSource.SPOT_MEASURED
            "IOT_METERED"   -> LoadFactorSource.IOT_METERED
            else            -> LoadFactorSource.ASSUMED
        },
        criticality = when (criticality) {
            "A" -> Criticality.A; "B" -> Criticality.B; else -> Criticality.C
        },
        expectedRunHours = expectedRunHours,
        currentStatus = when (currentStatus) {
            "RUNNING"    -> MachineStatus.RUNNING
            "STOPPED"    -> MachineStatus.STOPPED
            // Legacy/back-compat: any breakdown state (incl. an old "FLAGGED") maps to PENDING_QC.
            "FLAGGED"    -> MachineStatus.PENDING_QC
            "PENDING_QC" -> MachineStatus.PENDING_QC
            else         -> MachineStatus.IDLE
        },
        updatedAt = updatedAt
    )

    private fun MachineDto.toEntity() = MachineEntity(
        id = id, code = code, name = name, location = location, building = building, plantId = plantId,
        machineType = machineType,
        category = category,
        ratedKw = ratedKw, loadFactor = loadFactor, loadFactorSource = loadFactorSource,
        criticality = criticality, expectedRunHours = expectedRunHours,
        currentStatus = currentStatus, updatedAt = updatedAt
    )
}
