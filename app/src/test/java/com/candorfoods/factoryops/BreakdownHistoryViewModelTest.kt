package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.SyncStatus
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.presentation.head.reports.BreakdownHistoryViewModel
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BreakdownHistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: BreakdownRepository
    private lateinit var viewModel: BreakdownHistoryViewModel

    private fun flag(id: String, status: BreakdownStatus) = BreakdownFlag(
        id = id,
        machineId = "M-1",
        assetName = "Chiller 1",
        building = "A-185",
        operatorId = "op1",
        severity = Severity.MAJOR,
        description = "Leak",
        beforePhotoLocalPath = null,
        raisedAt = 1000L,
        status = status,
        acknowledgedBy = null,
        acknowledgedByName = null,
        acknowledgedAt = null,
        resolvedBy = null,
        resolvedByName = null,
        resolvedAt = null,
        partsUsed = null,
        afterPhotoLocalPath = null,
        qcStatus = null,
        qcAcknowledgedBy = null,
        qcAcknowledgedByName = null,
        qcAcknowledgedAt = null,
        qcDecidedBy = null,
        qcDecidedByName = null,
        qcDecidedAt = null,
        qcDecisionNotes = null,
        qcAfterPhotoLocalPath = null,
        qcChecklistJson = null,
        qcOverrideFromUserId = null,
        qcOverrideFromName = null,
        qcOverrideAt = null,
        syncStatus = SyncStatus.SYNCED,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk()
        coEvery { repo.breakdownHistory(any(), any(), any(), any()) } returns Result.Success(emptyList())
        viewModel = BreakdownHistoryViewModel(repo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load populates rows on success regardless of mixed statuses`() = runTest {
        val rows = listOf(
            flag("1", BreakdownStatus.OPEN),
            flag("2", BreakdownStatus.CLOSED),
            flag("3", BreakdownStatus.PENDING_QC),
        )
        coEvery { repo.breakdownHistory(any(), any(), any(), any()) } returns Result.Success(rows)

        viewModel.load()

        val state = viewModel.uiState.value
        assertEquals(rows, state.rows)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `load surfaces an error and clears loading`() = runTest {
        coEvery { repo.breakdownHistory(any(), any(), any(), any()) } returns Result.Error("offline")

        viewModel.load()

        val state = viewModel.uiState.value
        assertEquals("offline", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `setStatus updates state and sends the status to the repository`() = runTest {
        viewModel.setStatus("OPEN")

        assertEquals("OPEN", viewModel.uiState.value.status)
        coVerify { repo.breakdownHistory(any(), any(), any(), "OPEN") }
    }

    @Test
    fun `load defaults null dates to a 30-day window and writes them back into state`() = runTest {
        val today = java.time.LocalDate.now().toString()
        val defaultFrom = java.time.LocalDate.now().minusDays(30).toString()

        viewModel.load()

        coVerify { repo.breakdownHistory(defaultFrom, today, any(), any()) }
        assertEquals(defaultFrom, viewModel.uiState.value.from)
        assertEquals(today, viewModel.uiState.value.to)
    }

    @Test
    fun `setPlant with hyphenated A-185 sends short form A185 to repository`() = runTest {
        viewModel.setPlant("A-185")

        assertEquals("A-185", viewModel.uiState.value.plant)
        coVerify { repo.breakdownHistory(any(), any(), "A185", any()) }
    }

    @Test
    fun `setPlant with hyphenated W-202 sends short form W202 to repository`() = runTest {
        viewModel.setPlant("W-202")

        assertEquals("W-202", viewModel.uiState.value.plant)
        coVerify { repo.breakdownHistory(any(), any(), "W202", any()) }
    }

    @Test
    fun `setPlant with null sends both to repository`() = runTest {
        viewModel.setPlant(null)

        assertEquals(null, viewModel.uiState.value.plant)
        coVerify { repo.breakdownHistory(any(), any(), "both", any()) }
    }
}
