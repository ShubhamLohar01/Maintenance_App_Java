package com.candorfoods.factoryops

import app.cash.turbine.test
import com.candorfoods.factoryops.domain.model.FloorReadingRow
import com.candorfoods.factoryops.domain.model.FloorReadingsSnapshot
import com.candorfoods.factoryops.domain.model.FloorReadingsSubmitResult
import com.candorfoods.factoryops.domain.model.FloorSystemReading
import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.reports.FloorReadingReportRow
import com.candorfoods.factoryops.domain.repository.FloorReadingRepository
import com.candorfoods.factoryops.domain.repository.ReportsRepository
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetMachineMasterUseCase
import com.candorfoods.factoryops.util.excel.SpreadsheetExporter
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
    private lateinit var reportsRepository: ReportsRepository
    private lateinit var getMachineMaster: GetMachineMasterUseCase
    private lateinit var exporter: SpreadsheetExporter
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
        // Download is exercised in ReadingSheetsTest/XlsxTest; here it just must not be needed.
        reportsRepository = mockk()
        getMachineMaster = mockk()
        exporter = mockk()
        viewModel = DailyReadingViewModel(repository, reportsRepository, getMachineMaster, exporter)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetch loads floors and prefills already-saved meter readings`() = runTest {
        coEvery { repository.fetchSystemReadings(any(), null) } returns Result.Success(snapshot)
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
        coEvery { repository.fetchSystemReadings(any(), null) } returns Result.Success(snapshot)
        viewModel.fetchSystemReading()
        // 1st floor is prefilled, 2nd floor (idle) is blank — already submittable.
        assertTrue(viewModel.uiState.value.canSubmit)
        // Clearing every floor leaves nothing to submit.
        viewModel.onInputChange("1st floor", "")
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `input is sanitised to a parseable number`() = runTest {
        coEvery { repository.fetchSystemReadings(any(), null) } returns Result.Success(snapshot)
        viewModel.fetchSystemReading()
        viewModel.onInputChange("2nd floor", "4a2.5.7")   // letters + extra dot stripped
        assertEquals("42.57", viewModel.uiState.value.rows[1].input)
    }

    @Test
    fun `submit sends only the filled floors and echoes the fetched building`() = runTest {
        coEvery { repository.fetchSystemReadings(any(), null) } returns Result.Success(snapshot)
        val rows = slot<List<FloorReadingRow>>()
        // Must echo the fetched readingDate ("2026-06-25"), not null — otherwise the
        // backend's own default could silently save under a different day than shown.
        coEvery { repository.submitReadings("2026-06-25", "A-185", capture(rows)) } returns
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
        coEvery { repository.fetchSystemReadings(any(), null) } returns Result.Success(snapshot)
        coEvery { repository.submitReadings("2026-06-25", "A-185", any()) } returns Result.Error("HTTP 400")
        viewModel.fetchSystemReading()
        viewModel.uiEvent.test {
            viewModel.submit()
            assertEquals(DailyReadingUiEvent.Error("HTTP 400"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(DailyReadingPhase.Loaded, viewModel.uiState.value.phase)
        assertEquals("HTTP 400", viewModel.uiState.value.error)
    }

    // ─── W-202 AC units on the same form ──────────────────────────────────────

    private val w202Snapshot = FloorReadingsSnapshot(
        building = "W-202",
        readingDate = "2026-06-25",
        floors = listOf(FloorSystemReading("Service Floor", systemReading = 12.0, meterReading = null)),
    )

    private fun asset(id: String, name: String, floor: String = "Service Floor") = MtMachine(
        assetId = id, building = "W-202", assetName = name,
        category = "Electric Asset", subLocation = floor, ratedKw = null,
        powerLoadRaw = null, section = MachineCategory.OTHERS,
    )

    /**
     * Two indoor units (they share a name, so they collapse to the one reading row), plus
     * the register's other AC varieties and a non-AC — so the indoor-only filter is
     * actually exercised. distinctBy keeps the first, so W202-0192 is the surviving row.
     */
    private val w202Assets = listOf(
        asset("W202-0192", "AC Indoor"),
        asset("W202-0170", "AC Indoor", floor = "Lower Basement"),
        asset("W202-0078", "Air Conditioner"),
        asset("W202-0269", "AC Out Door", floor = "TERRACE"),
        asset("W202-0301", "Packaging Machine"),
    )

    private fun loadW202(saved: List<FloorReadingReportRow> = emptyList()) {
        coEvery { repository.fetchSystemReadings(any(), null) } returns Result.Success(w202Snapshot)
        coEvery { getMachineMaster() } returns Result.Success(w202Assets)
        coEvery { reportsRepository.floorReadingsReport("W-202", "2026-06-25", "2026-06-25") } returns
            Result.Success(saved)
        viewModel.fetchSystemReading()
    }

    @Test
    fun `W-202 offers the indoor AC only, and nothing else from the register`() = runTest {
        loadW202()
        val state = viewModel.uiState.value
        assertEquals(listOf("AC Indoor"), state.acRows.map { it.point.name })
        assertEquals("W202-0192", state.acRows.single().point.assetId)
        assertTrue(state.isAcExpanded)    // a single row opens rather than hiding behind a tap
    }

    @Test
    fun `another building gets no AC section`() = runTest {
        coEvery { repository.fetchSystemReadings(any(), null) } returns Result.Success(snapshot)
        viewModel.fetchSystemReading()
        assertEquals("A-185", viewModel.uiState.value.building)
        assertTrue(viewModel.uiState.value.acRows.isEmpty())
    }

    @Test
    fun `an AC reading already stored for the date is prefilled`() = runTest {
        loadW202(
            saved = listOf(
                FloorReadingReportRow("2026-06-25", "W-202", "AC · AC Indoor", 412.0, null),
            )
        )
        val indoor = viewModel.uiState.value.acRows.single()
        assertEquals("412", indoor.input)       // 412.0 reopens as "412", not "412.0"
        assertEquals(412.0, indoor.saved)
    }

    @Test
    fun `submit sends floors and ACs in one round, ACs under their prefixed key`() = runTest {
        loadW202()
        val rows = slot<List<FloorReadingRow>>()
        coEvery { repository.submitReadings("2026-06-25", "W-202", capture(rows)) } returns
            Result.Success(FloorReadingsSubmitResult("W-202", "2026-06-25", 2))

        viewModel.onInputChange("Service Floor", "300")
        viewModel.onAcInputChange("W202-0192", "412")
        assertEquals(2, viewModel.uiState.value.filledCount)

        viewModel.uiEvent.test {
            viewModel.submit()
            assertEquals(DailyReadingUiEvent.Saved(2), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(
            listOf("Service Floor" to 300.0, "AC · AC Indoor" to 412.0),
            rows.captured.map { it.floor to it.meterReading },
        )
    }

    @Test
    fun `an AC alone is enough to submit`() = runTest {
        loadW202()
        assertFalse(viewModel.uiState.value.canSubmit)   // nothing filled yet
        viewModel.onAcInputChange("W202-0192", "88")
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `AC input is sanitised like a floor's`() = runTest {
        loadW202()
        viewModel.onAcInputChange("W202-0192", "4a1.2.5")
        assertEquals("41.25", viewModel.uiState.value.acRows.first().input)
    }

    @Test
    fun `a failed AC lookup leaves the floor form usable`() = runTest {
        coEvery { repository.fetchSystemReadings(any(), null) } returns Result.Success(w202Snapshot)
        coEvery { getMachineMaster() } returns Result.Error("register down")
        viewModel.fetchSystemReading()
        val state = viewModel.uiState.value
        assertEquals(DailyReadingPhase.Loaded, state.phase)
        assertTrue(state.acRows.isEmpty())
        assertEquals(1, state.rows.size)
    }

    @Test
    fun `the AC group toggles open and shut`() = runTest {
        loadW202()
        assertTrue(viewModel.uiState.value.isAcExpanded)
        viewModel.toggleAcSection()
        assertFalse(viewModel.uiState.value.isAcExpanded)
        viewModel.toggleAcSection()
        assertTrue(viewModel.uiState.value.isAcExpanded)
    }
}
