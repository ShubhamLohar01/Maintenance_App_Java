package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.ProductionRun
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.SyncStatus
import com.candorfoods.factoryops.domain.repository.EnergyRepository
import com.candorfoods.factoryops.domain.usecase.energy.StartProductionRunUseCase
import com.candorfoods.factoryops.domain.usecase.energy.StopProductionRunUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StartProductionRunUseCaseTest {

    private lateinit var energyRepository: EnergyRepository
    private lateinit var startRunUseCase: StartProductionRunUseCase
    private lateinit var stopRunUseCase: StopProductionRunUseCase

    private val assetId = "W202-0005"

    private fun mockRun(id: String = "run-001", endedAt: Long? = null) = ProductionRun(
        id = id, machineId = assetId, operatorId = "user-001",
        startedAt = System.currentTimeMillis(),
        scheduledEndAt = System.currentTimeMillis() + 8 * 3_600_000L,
        endedAt = endedAt, computedKwh = if (endedAt != null) 87.5 else null,
        syncStatus = SyncStatus.PENDING, flagStatus = null,
        createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
    )

    @BeforeEach
    fun setUp() {
        energyRepository = mockk()
        startRunUseCase = StartProductionRunUseCase(energyRepository)
        stopRunUseCase = StopProductionRunUseCase(energyRepository)
    }

    @Test
    fun `start run sets scheduledEndAt to now + expectedRunHours`() = runTest {
        val scheduledEndSlot = slot<Long>()
        coEvery {
            energyRepository.startProductionRun(assetId, any(), any(), capture(scheduledEndSlot))
        } returns Result.Success(mockRun())
        val before = System.currentTimeMillis()
        startRunUseCase(assetId, "Shrink Wrap", 3.0, expectedRunHours = 8.0)
        val after = System.currentTimeMillis()
        val captured = scheduledEndSlot.captured
        assertTrue(captured >= before + 8 * 3_600_000L)
        assertTrue(captured <= after + 8 * 3_600_000L)
    }

    @Test
    fun `start run success returns Success with production run`() = runTest {
        val run = mockRun()
        coEvery { energyRepository.startProductionRun(any(), any(), any(), any()) } returns Result.Success(run)
        val result = startRunUseCase(assetId, "Shrink Wrap", 3.0, 8.0)
        assertTrue(result is Result.Success)
        assertEquals(run, (result as Result.Success).data)
    }

    @Test
    fun `start run propagates Error from repository`() = runTest {
        coEvery { energyRepository.startProductionRun(any(), any(), any(), any()) } returns Result.Error("DB error")
        val result = startRunUseCase(assetId, "Shrink Wrap", 3.0, 8.0)
        assertTrue(result is Result.Error)
    }

    @Test
    fun `stop run calls repository with current time`() = runTest {
        val runId = "run-001"
        val endedAtSlot = slot<Long>()
        coEvery { energyRepository.stopProductionRun(runId, capture(endedAtSlot)) } returns
                Result.Success(mockRun(id = runId, endedAt = System.currentTimeMillis()))
        val before = System.currentTimeMillis()
        stopRunUseCase(runId)
        val after = System.currentTimeMillis()
        assertTrue(endedAtSlot.captured in before..after)
    }
}
