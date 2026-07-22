package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.BreakdownApi
import com.candorfoods.factoryops.data.remote.api.ChecklistApi
import com.candorfoods.factoryops.data.remote.dto.BreakdownHistoryDto
import com.candorfoods.factoryops.data.repository.BreakdownRepositoryImpl
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.Result
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BreakdownHistoryTest {

    private val api: BreakdownApi = mockk()
    private val checklistApi: ChecklistApi = mockk(relaxed = true)
    private val mtMachineRepository: MtMachineRepository = mockk(relaxed = true)
    private val db: AppDatabase = mockk(relaxed = true)
    private val authPreferences: AuthPreferences = mockk(relaxed = true)
    private val repo = BreakdownRepositoryImpl(api, checklistApi, mtMachineRepository, db, authPreferences)

    @Test
    fun `breakdownHistory maps rows regardless of status`() = runTest {
        coEvery {
            api.breakdownHistory(any(), "2026-07-01", "2026-07-20", "both", null)
        } returns listOf(
            BreakdownHistoryDto(id = "bd-1", assetId = "A185-0007", status = "CLOSED"),
            BreakdownHistoryDto(id = "bd-2", assetId = "A185-0008", status = "OPEN"),
        )
        val result = repo.breakdownHistory("2026-07-01", "2026-07-20")
        assertTrue(result is Result.Success)
        val flags = (result as Result.Success).data
        assertEquals(2, flags.size)
        assertEquals(BreakdownStatus.CLOSED, flags[0].status)
    }

    @Test
    fun `qcActivityHistory maps rows and surfaces errors`() = runTest {
        coEvery { api.qcActivityHistory(any(), any(), any(), any()) } throws RuntimeException("timeout")
        val result = repo.qcActivityHistory("2026-07-01", "2026-07-20")
        assertTrue(result is Result.Error)
        assertEquals("timeout", (result as Result.Error).message)
    }
}
