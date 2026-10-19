package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.local.entity.SparePartPickEntity
import com.candorfoods.factoryops.data.remote.api.SparePartsApi
import com.candorfoods.factoryops.data.remote.dto.SparePartActionRequestDto
import com.candorfoods.factoryops.data.remote.dto.SparePartCreateDto
import com.candorfoods.factoryops.data.remote.dto.SparePartUpdateDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.spareparts.SparePart
import com.candorfoods.factoryops.domain.model.spareparts.SparePartLogEntry
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPick
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPickerCatalog
import com.candorfoods.factoryops.domain.model.spareparts.SparePartsMachine
import com.candorfoods.factoryops.domain.model.spareparts.WorkType
import com.candorfoods.factoryops.domain.repository.SparePartsRepository
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SparePartsRepositoryImpl @Inject constructor(
    private val api: SparePartsApi,
    private val authPreferences: AuthPreferences,
    private val db: AppDatabase,
) : SparePartsRepository {

    private val pickDao get() = db.sparePartPickDao()

    private suspend fun bearerToken() = "Bearer ${authPreferences.getToken().orEmpty()}"

    /**
     * The backend returns its reason in a JSON `detail` field ("'V-Belt' already
     * exists for this machine", "not enough stock: 2 on hand"). Surfacing that
     * verbatim is what makes the form and sheet errors useful, so pull it out of
     * the error body rather than showing a bare status code.
     */
    private fun message(e: Exception, fallback: String): String = when (e) {
        is HttpException -> e.response()?.errorBody()?.string()
            ?.substringAfter("\"detail\":\"", "")
            ?.substringBefore("\"")
            ?.takeIf { it.isNotBlank() }
            ?: fallback
        else -> e.message ?: fallback
    }

    override suspend fun list(): Result<List<SparePartsMachine>> = try {
        Result.Success(api.list(bearerToken()).toDomain())
    } catch (e: Exception) {
        Result.Error(message(e, "Failed to load spare parts"), e)
    }

    override suspend fun picker(assetId: String?): SparePartPickerCatalog {
        val now = System.currentTimeMillis()
        return try {
            val fresh = api.picker(bearerToken(), assetId).toDomain()
            pickDao.replaceAll(fresh.items.map { it.toEntity(now) })
            fresh
        } catch (e: Exception) {
            // Offline, or the tunnel is cold. Fall back to the last synced
            // catalogue rather than showing an empty picker — a technician
            // standing at the machine still has to close the job. `assetId` is
            // re-applied here because the flag depends on the question asked, not
            // on what was cached.
            //
            // The failure is REPORTED, not swallowed: an empty cache plus a
            // swallowed error is indistinguishable from "this warehouse has no
            // stock", which is a lie the picker used to tell for every 401, 500
            // and dead tunnel.
            val cached = pickDao.getAll().map { it.toDomain(assetId) }
            SparePartPickerCatalog(
                warehouses = cached.mapNotNull { it.warehouse }.distinct().sorted(),
                items = cached.sortedWith(
                    compareBy({ !it.forThisMachine }, { it.machineName.orEmpty().lowercase() },
                        { it.partName.lowercase() })
                ),
                error = message(
                    e,
                    "Couldn't load spare parts " +
                        ((e as? HttpException)?.let { "(HTTP ${it.code()})" }
                            ?: "— no connection to the server"),
                ),
            )
        }
    }

    private fun SparePartPick.toEntity(cachedAt: Long) = SparePartPickEntity(
        partId = partId,
        partName = partName,
        unit = unit,
        quantity = quantity,
        minQuantity = minQuantity,
        lowStock = lowStock,
        machineName = machineName,
        assetId = assetId,
        warehouse = warehouse,
        cachedAt = cachedAt,
    )

    private fun SparePartPickEntity.toDomain(forAssetId: String?) = SparePartPick(
        partId = partId,
        partName = partName,
        unit = unit,
        quantity = quantity,
        minQuantity = minQuantity,
        lowStock = lowStock,
        machineName = machineName,
        assetId = assetId,
        warehouse = warehouse,
        forThisMachine = forAssetId != null && assetId == forAssetId,
    )

    override suspend fun create(
        assetId: String?, machineName: String?, warehouse: String?,
        partName: String, unit: String?, quantity: Int, minQuantity: Int,
    ): Result<SparePart> = try {
        Result.Success(
            api.create(
                bearerToken(),
                SparePartCreateDto(
                    assetId = assetId, machineName = machineName, warehouse = warehouse,
                    partName = partName, unit = unit,
                    quantity = quantity, minQuantity = minQuantity,
                ),
            ).toDomain()
        )
    } catch (e: Exception) {
        Result.Error(message(e, "Failed to add spare part"), e)
    }

    override suspend fun update(
        partId: Int, assetId: String?, machineName: String?, warehouse: String?,
        partName: String, unit: String?, minQuantity: Int,
    ): Result<SparePart> = try {
        Result.Success(
            api.update(
                bearerToken(), partId,
                SparePartUpdateDto(
                    assetId = assetId, machineName = machineName, warehouse = warehouse,
                    partName = partName, unit = unit, minQuantity = minQuantity,
                ),
            ).toDomain()
        )
    } catch (e: Exception) {
        Result.Error(message(e, "Failed to save spare part"), e)
    }

    override suspend fun delete(partId: Int): Result<Unit> = try {
        val response = api.delete(bearerToken(), partId)
        if (response.isSuccessful) {
            Result.Success(Unit)
        } else if (response.code() == 403) {
            Result.Error("Only a supervisor can delete a spare part")
        } else {
            Result.Error("Failed to delete spare part")
        }
    } catch (e: Exception) {
        Result.Error(message(e, "Failed to delete spare part"), e)
    }

    override suspend fun use(
        partId: Int, quantity: Int, workType: WorkType, note: String?,
    ): Result<SparePart> = try {
        Result.Success(
            api.use(
                bearerToken(), partId,
                SparePartActionRequestDto(quantity, workType.wire, note),
            ).toDomain()
        )
    } catch (e: Exception) {
        Result.Error(message(e, "Failed to log usage"), e)
    }

    override suspend fun restock(partId: Int, quantity: Int, note: String?): Result<SparePart> = try {
        Result.Success(
            api.restock(bearerToken(), partId, SparePartActionRequestDto(quantity, null, note)).toDomain()
        )
    } catch (e: Exception) {
        Result.Error(message(e, "Failed to restock"), e)
    }

    override suspend fun history(partId: Int): Result<List<SparePartLogEntry>> = try {
        Result.Success(api.history(bearerToken(), partId).toDomain())
    } catch (e: Exception) {
        Result.Error(message(e, "Failed to load history"), e)
    }
}
