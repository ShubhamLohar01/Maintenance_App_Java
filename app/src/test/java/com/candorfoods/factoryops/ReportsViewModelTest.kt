package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.reports.FloorReadingReportRow
import com.candorfoods.factoryops.domain.model.reports.MachineReadingRow
import com.candorfoods.factoryops.domain.repository.ReportsRepository
import com.candorfoods.factoryops.presentation.reports.FloorReadingsReportViewModel
import com.candorfoods.factoryops.presentation.reports.MachinesReadingViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MachinesReadingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ReportsRepository

    private val rows = listOf(
        MachineReadingRow(
            readingDate = "2026-07-20", machineId = "A185-1", assetName = "Sealer",
            building = "A-185", floor = "1st floor", operatorName = "Karan",
            startedAt = 1000L, endedAt = 2000L, status = "COMPLETE", source = "RUN", dailyKwh = 5.0,
        ),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load populates rows on success`() = runTest {
        coEvery { repository.machinesReading(null, null, null) } returns Result.Success(rows)
        val viewModel = MachinesReadingViewModel(repository)
        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(1, state.rows.size)
        assertNull(state.error)
    }

    @Test
    fun `load surfaces an error on failure`() = runTest {
        coEvery { repository.machinesReading(null, null, null) } returns Result.Error("HTTP 500")
        val viewModel = MachinesReadingViewModel(repository)
        val state = viewModel.uiState.value
        assertEquals("HTTP 500", state.error)
        assertEquals(0, state.rows.size)
    }

    @Test
    fun `setPlant reloads with the new plant filter`() = runTest {
        coEvery { repository.machinesReading(null, null, null) } returns Result.Success(rows)
        coEvery { repository.machinesReading("A-185", null, null) } returns Result.Success(emptyList())
        val viewModel = MachinesReadingViewModel(repository)

        viewModel.setPlant("A-185")

        assertEquals("A-185", viewModel.uiState.value.plant)
        coVerify { repository.machinesReading("A-185", null, null) }
    }

    @Test
    fun `setDateRange reloads with the new range`() = runTest {
        coEvery { repository.machinesReading(null, null, null) } returns Result.Success(rows)
        coEvery { repository.machinesReading(null, "2026-07-01", "2026-07-20") } returns Result.Success(rows)
        val viewModel = MachinesReadingViewModel(repository)

        viewModel.setDateRange("2026-07-01", "2026-07-20")

        assertEquals("2026-07-01", viewModel.uiState.value.from)
        assertEquals("2026-07-20", viewModel.uiState.value.to)
        coVerify { repository.machinesReading(null, "2026-07-01", "2026-07-20") }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FloorReadingsReportViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ReportsRepository

    private val rows = listOf(
        FloorReadingReportRow(
            readingDate = "2026-07-20", building = "A-185", floor = "Ground floor",
            meterReading = 100.5, dailyKwh = 12.0,
        ),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load populates rows on success`() = runTest {
        coEvery { repository.floorReadingsReport(null, null, null) } returns Result.Success(rows)
        val viewModel = FloorReadingsReportViewModel(repository)
        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(1, state.rows.size)
        assertNull(state.error)
    }

    @Test
    fun `load surfaces an error on failure`() = runTest {
        coEvery { repository.floorReadingsReport(null, null, null) } returns Result.Error("HTTP 500")
        val viewModel = FloorReadingsReportViewModel(repository)
        assertEquals("HTTP 500", viewModel.uiState.value.error)
    }

    @Test
    fun `setPlant reloads with the new plant filter`() = runTest {
        coEvery { repository.floorReadingsReport(null, null, null) } returns Result.Success(rows)
        coEvery { repository.floorReadingsReport("W-202", null, null) } returns Result.Success(emptyList())
        val viewModel = FloorReadingsReportViewModel(repository)

        viewModel.setPlant("W-202")

        assertEquals("W-202", viewModel.uiState.value.plant)
        coVerify { repository.floorReadingsReport("W-202", null, null) }
    }
}
