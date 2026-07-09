package com.candorfoods.factoryops.machine

import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetRunnableAssetsUseCase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetRunnableAssetsShutDownTest {

    private fun machine(id: String, condition: String?) = MtMachine(
        assetId = id, building = "W-202", assetName = "Asset $id",
        category = "Production Equipment", subLocation = "Floor 1", ratedKw = 1.0,
        powerLoadRaw = "120Watt", section = MachineCategory.PRODUCTION, condition = condition,
    )

    private class FakeRepo(private val data: List<MtMachine>) : MtMachineRepository {
        override suspend fun list(building: String?, subLocation: String?, subCategory: String?) =
            Result.Success(data)
        override suspend fun getByAssetId(assetId: String) =
            Result.Success(data.firstOrNull { it.assetId == assetId })
        override suspend fun addOther(
            name: String, building: String, subLocation: String,
            subCategory: String, powerLoad: String?,
        ): Result<MtMachine> = error("unused")
        override suspend fun addAsset(machine: MtMachine): Result<MtMachine> = error("unused")
        override suspend fun updateAsset(machine: MtMachine): Result<MtMachine> = error("unused")
    }

    @Test fun `shut-down machine is excluded even when otherwise runnable`() = runBlocking {
        val repo = FakeRepo(listOf(machine("A", null), machine("B", "Shut Down")))
        val result = GetRunnableAssetsUseCase(repo)() as Result.Success
        assertEquals(listOf("A"), result.data.map { it.assetId })
    }
}
