package com.candorfoods.factoryops

import app.cash.turbine.test
import com.candorfoods.factoryops.domain.model.FloorReadingRow
import com.candorfoods.factoryops.domain.model.FloorReadingsSnapshot
import com.candorfoods.factoryops.domain.model.FloorReadingsSubmitResult
import com.candorfoods.factoryops.domain.model.FloorSystemReading
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.FloorReadingRepository
import com.candorfoods.factoryops.presentation.technician.DailyReadingPhase
import com.candorfoods.factoryops.presentation.technician.DailyReadingUiEvent
import com.candorfoods.factoryops.presentation.technician.DailyReadingViewModel
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyReadingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FloorReadingRepository
    private lateinit var viewModel: DailyReadingViewModel

    // 1st floor already has a saved reading (prefilled); 2nd floor is idle (system 0.0, blank).
    private val snapshot = FloorReadingsSnapshot(
        building = "A-185",
        readingDate = "2026-06-25",
        floors = listOf(
            FloorSystemReading(floor = "1st floor", systemReading = 88.3, meterReading = 90.0),
            FloorSystemReading(floor = "2nd floor", systemReading = 0.0, meterReading = null),
        ),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        viewModel = DailyReadingViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetch loads floors and prefills already-saved meter readings`() = runTest {
        coEvery { repository.fetchSystemReadings(null, null) } returns Result.Success(snapshot)
        viewModel.fetchSystemReading()
        val state = viewModel.uiState.value
        assertEquals(DailyReadingPhase.Loaded, state.phase)
        assertEquals("A-185", state.building)
        assertEquals(2, state.rows.size)
        assertEquals("90", state.rows[0].input)   // 90.0 → "90", prefilled
        assertEquals("", state.rows[1].input)       // null → blank (idle floor)
    }

    @Test
    fun `canSubmit is true once at least one floor is filled, and idle floors may stay blank`() = runTest {
        coEvery { repository.fetchSystemReadings(null, null) } returns Result.Success(snapshot)
        viewModel.fetchSystemReading()
        // 1st floor is prefilled, 2nd floor (idle) is blank — already submittable.
        assertTrue(viewModel.uiState.value.canSubmit)
        // Clearing every floor leaves nothing to submit.
        viewModel.onInputChange("1st floor", "")
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `input is sanitised to a parseable number`() = runTest {
        coEvery { repository.fetchSystemReadings(null, null) } returns Result.Success(snapshot)
        viewModel.fetchSystemReading()
        viewModel.onInputChange("2nd floor", "4a2.5.7")   // letters + extra dot stripped
        assertEquals("42.57", viewModel.uiState.value.rows[1].input)
    }

    @Test
    fun `submit sends only the filled floors and echoes the fetched building`() = runTest {
        coEvery { repository.fetchSystemReadings(null, null) } returns Result.Success(snapshot)
        val rows = slot<List<FloorReadingRow>>()
        coEvery { repository.submitReadings(null, "A-185", capture(rows)) } returns
            Result.Success(FloorReadingsSubmitResult("A-185", "2026-06-25", 1))
        viewModel.fetchSystemReading()
        // Leave the idle 2nd floor blank; only the prefilled 1st floor should be sent.
        viewModel.uiEvent.test {
            viewModel.submit()
            assertEquals(DailyReadingUiEvent.Saved(1), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, rows.captured.size)
        assertEquals("1st floor", rows.captured[0].floor)
        assertEquals(90.0, rows.captured[0].meterReading)
        assertEquals(DailyReadingPhase.Loaded, viewModel.uiState.value.phase)
    }

    @Test
    fun `submit surfaces an error event and keeps the form loaded`() = runTest {
        coEvery { repository.fetchSystemReadings(null, null) } returns Result.Success(snapshot)
        coEvery { repository.submitReadings(null, "A-185", any()) } returns Result.Error("HTTP 400")
        viewModel.fetchSystemReading()
        viewModel.uiEvent.test {
            viewModel.submit()
            assertEquals(DailyReadingUiEvent.Error("HTTP 400"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(DailyReadingPhase.Loaded, viewModel.uiState.value.phase)
        assertEquals("HTTP 400", viewModel.uiState.value.error)
    }
}
