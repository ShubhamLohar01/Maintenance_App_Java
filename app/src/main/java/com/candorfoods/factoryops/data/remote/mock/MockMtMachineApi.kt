package com.candorfoods.factoryops.data.remote.mock

import com.candorfoods.factoryops.data.remote.api.MtMachineApi
import com.candorfoods.factoryops.data.remote.dto.MtMachineDto
import com.candorfoods.factoryops.data.remote.dto.MtMachineUpdateDto
import kotlinx.coroutines.delay

class MockMtMachineApi : MtMachineApi {

    override suspend fun list(
        token: String,
        building: String?,
        subLocation: String?,
        subCategory: String?,
    ): List<MtMachineDto> {
        delay(400)
        // Full asset register = static Production rows + the mutable OTHERS seed
        // (Admin can append to the latter). Production/Others is derived from
        // sub_category downstream, not stored, so we just return the union here.
        val pool = productionRows + OthersCatalogSeed.entries
        return pool.filter { dto ->
            (building == null || dto.building.equals(building, ignoreCase = true)) &&
                (subLocation == null || dto.subLocation.equals(subLocation, ignoreCase = true)) &&
                (subCategory == null || dto.category.equals(subCategory, ignoreCase = true))
        }
    }

    override suspend fun add(token: String, dto: MtMachineDto): MtMachineDto {
        delay(300)
        // Backend assigns the asset_id (prefixed by building). Admin-added rows
        // live in the OTHERS seed.
        val withId = dto.copy(assetId = OthersCatalogSeed.nextAdminId(dto.building))
        OthersCatalogSeed.entries.add(withId)
        return withId
    }

    override suspend fun update(token: String, assetId: String, dto: MtMachineUpdateDto): MtMachineDto {
        delay(300)
        val updated = MtMachineDto(
            assetId = assetId,
            building = dto.building,
            assetName = dto.assetName,
            category = dto.category,
            subLocation = dto.subLocation,
            powerLoad = dto.powerLoad,
            quantity = dto.quantity,
            condition = dto.condition,
            modelNo = dto.modelNo,
            serialNo = dto.serialNo,
            remarks = dto.remarks,
        )
        // Reflect the edit in the mutable OTHERS seed if the row lives there.
        val idx = OthersCatalogSeed.entries.indexOfFirst { it.assetId == assetId }
        if (idx >= 0) OthersCatalogSeed.entries[idx] = updated
        return updated
    }

    // PRODUCTION sample rows — sub_category "Production Equipment" so they land
    // in the Production section. A-185 rows carry raw power_load text; W-202 don't.
    private val productionRows = listOf(
        MtMachineDto("W202-0001", "W-202", "Shrink wrap packaging machine", "Production Equipment", "Lower Basement"),
        MtMachineDto("W202-0002", "W-202", "Screw compressor", "Production Equipment", "Service Floor"),
        MtMachineDto("W202-0003", "W-202", "Belt conveyor", "Production Equipment", "1st Floor"),
        MtMachineDto("A185-0001", "A-185", "Coffee roaster", "Production Equipment", "Ground Floor", powerLoad = "22 kW"),
        MtMachineDto("A185-0002", "A-185", "Dough mixer", "Production Equipment", "1st Floor", powerLoad = "120Watt"),
    )
}
