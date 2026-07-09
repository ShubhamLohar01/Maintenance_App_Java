package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.MtMachineApi
import com.candorfoods.factoryops.data.remote.dto.MtMachineDto
import com.candorfoods.factoryops.data.remote.dto.toAddDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.data.remote.dto.toUpdateDto
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MtMachineRepositoryImpl @Inject constructor(
    private val mtMachineApi: MtMachineApi,
    private val authPreferences: AuthPreferences,
) : MtMachineRepository {

    override suspend fun list(
        building: String?,
        subLocation: String?,
        subCategory: String?,
    ): Result<List<MtMachine>> {
        val token = authPreferences.getToken()
            ?: return Result.Error("Not logged in")
        return try {
            val rows = mtMachineApi.list(
                token       = "Bearer $token",
                building    = building,
                subLocation = subLocation,
                subCategory = subCategory,
            )
            Result.Success(rows.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load asset register", e)
        }
    }

    override suspend fun getByAssetId(assetId: String): Result<MtMachine?> =
        when (val r = list()) {
            is Result.Success -> Result.Success(r.data.firstOrNull { it.assetId == assetId })
            is Result.Error -> r
            is Result.Loading -> r
        }

    override suspend fun addOther(
        name: String,
        building: String,
        subLocation: String,
        subCategory: String,
        powerLoad: String?,
    ): Result<MtMachine> {
        val token = authPreferences.getToken()
            ?: return Result.Error("Not logged in")
        return try {
            val saved = mtMachineApi.add(
                token = "Bearer $token",
                dto = MtMachineDto(
                    assetId     = "",            // backend assigns
                    building    = building,
                    assetName   = name,
                    category    = subCategory,
                    subLocation = subLocation,
                    powerLoad   = powerLoad,
                ),
            )
            Result.Success(saved.toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to add asset", e)
        }
    }

    override suspend fun addAsset(machine: MtMachine): Result<MtMachine> {
        val token = authPreferences.getToken()
            ?: return Result.Error("Not logged in")
        return try {
            val saved = mtMachineApi.add(
                token = "Bearer $token",
                dto = machine.toAddDto(),
            )
            Result.Success(saved.toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to add machine", e)
        }
    }

    override suspend fun updateAsset(machine: MtMachine): Result<MtMachine> {
        val token = authPreferences.getToken()
            ?: return Result.Error("Not logged in")
        return try {
            val saved = mtMachineApi.update(
                token = "Bearer $token",
                assetId = machine.assetId,
                dto = machine.toUpdateDto(),
            )
            Result.Success(saved.toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to update asset", e)
        }
    }
}
