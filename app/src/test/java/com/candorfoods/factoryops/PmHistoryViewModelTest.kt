package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.TradeType
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.presentation.head.reports.PmHistoryViewModel
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
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class PmHistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: PmWorkOrderRepository
    private lateinit var viewModel: PmHistoryViewModel

    private fun wo(id: String, status: PmWoStatus, scheduledDate: Long) = PmWorkOrder(
        id = id,
        planId = "plan-1",
        machineId = "M-1",
        templateId = "tpl-1",
        templateName = "Roaster PM",
        machineType = MachineType.ROASTER,
        estimatedDurationMinutes = 30,
        tradeType = TradeType.MECHANICAL,
        scheduledDate = scheduledDate,
        generatedAt = scheduledDate,
        status = status,
        assignedTechnicianId = "tech-1",
        assignedTechnicianName = "Tech One",
        acknowledgedAt = null,
        startedAt = null,
        submittedAt = null,
        supervisorApprovedBy = null,
        supervisorApprovedByName = null,
        supervisorApprovedAt = null,
        supervisorRejectedAt = null,
        supervisorRejectionNotes = null,
        qcAcknowledgedBy = null,
        qcAcknowledgedByName = null,
        qcAcknowledgedAt = null,
        qcDecidedBy = null,
        qcDecidedByName = null,
        qcDecidedAt = null,
        qcDecisionNotes = null,
        qcAfterPhotoLocalPath = null,
        qcChecklistJson = null,
        closedAt = null,
        finalNotes = null,
        overallResult = null,
        escalationTier = 0,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk()
        coEvery { repo.history(any()) } returns Result.Success(emptyList())
        viewModel = PmHistoryViewModel(repo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load populates rows on success`() = runTest {
        val today = LocalDate.now().toString()
        val zone = ZoneId.systemDefault()
        val midWindowMillis = LocalDate.parse(today).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = listOf(
            wo("1", PmWoStatus.CLOSED, midWindowMillis),
            wo("2", PmWoStatus.NOTIFIED, midWindowMillis),
        )
        coEvery { repo.history(any()) } returns Result.Success(rows)

        viewModel.load()

        val state = viewModel.uiState.value
        assertEquals(rows, state.rows)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `load surfaces an error and clears loading`() = runTest {
        coEvery { repo.history(any()) } returns Result.Error("offline")

        viewModel.load()

        val state = viewModel.uiState.value
        assertEquals("offline", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `setStatus CLOSED sends CLOSED to the repository`() = runTest {
        viewModel.setStatus("CLOSED")

        assertEquals("CLOSED", viewModel.uiState.value.status)
        coVerify { repo.history("CLOSED") }
    }

    @Test
    fun `setStatus null sends null to the repository`() = runTest {
        viewModel.setStatus(null)

        assertEquals(null, viewModel.uiState.value.status)
        coVerify { repo.history(null) }
    }

    @Test
    fun `load defaults null dates to a 30-day window and writes them back into state`() = runTest {
        val today = LocalDate.now().toString()
        val defaultFrom = LocalDate.now().minusDays(30).toString()

        viewModel.load()

        assertEquals(defaultFrom, viewModel.uiState.value.from)
        assertEquals(today, viewModel.uiState.value.to)
        coVerify { repo.history(null) }
    }

    @Test
    fun `client-side date filtering keeps only rows within the from-to window, including boundaries`() = runTest {
        val zone = ZoneId.systemDefault()
        val from = "2026-07-01"
        val to = "2026-07-10"

        val startOfFrom = LocalDate.parse(from).atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfTo = LocalDate.parse(to).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val midWindow = LocalDate.parse("2026-07-05").atStartOfDay(zone).toInstant().toEpochMilli()
        val beforeWindow = startOfFrom - 1
        val afterWindow = endOfTo + 1

        val rows = listOf(
            wo("before", PmWoStatus.CLOSED, beforeWindow),
            wo("at-start", PmWoStatus.CLOSED, startOfFrom),
            wo("mid", PmWoStatus.CLOSED, midWindow),
            wo("at-end", PmWoStatus.CLOSED, endOfTo),
            wo("after", PmWoStatus.CLOSED, afterWindow),
        )
        coEvery { repo.history(any()) } returns Result.Success(rows)

        viewModel.setDateRange(from, to)

        val ids = viewModel.uiState.value.rows.map { it.id }
        assertEquals(listOf("at-start", "mid", "at-end"), ids)
    }
}
