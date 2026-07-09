package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import com.candorfoods.factoryops.presentation.checklist.BreakdownListViewModel
import com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BreakdownListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var breakdownRepo: BreakdownRepository
    private lateinit var checklistRepo: ChecklistRepository
    private lateinit var renderer: ChecklistPdfRenderer
    private lateinit var viewModel: BreakdownListViewModel

    private val record = BreakdownRecord(
        id = 1, docNo = "CFPLA.C4.F.06", recordDate = "2026-06-25", location = "A-185",
        machineName = "Mixer", equipmentModelNo = "M1", problemInBrief = "Jam",
        typeOfMaintenance = "Corrective", partOfMachine = "Belt", temporaryReason = "",
        durationStart = "10:00", durationEnd = "10:40", machineOperatorSign = "Op",
        maintenancePersonSign = "Tech", qcClearanceSign = "QC", verifiedBy = "Head", createdAt = "",
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        breakdownRepo = mockk(relaxed = true)
        checklistRepo = mockk()
        renderer = mockk()
        every { breakdownRepo.observeAllFlags() } returns flowOf(emptyList())
        coEvery { checklistRepo.listBreakdowns() } returns Result.Success(emptyList())  // init loadSubmitted()
        viewModel = BreakdownListViewModel(breakdownRepo, checklistRepo, renderer)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `exportSubmitted renders the F06 record and exposes the file to share`() = runTest {
        val file = File("bd1.pdf")
        coEvery { renderer.renderBreakdown(any()) } returns file
        viewModel.exportSubmitted(record)
        val state = viewModel.uiState.value
        assertEquals(file, state.pdfToShare)
        assertFalse(state.isExporting)
        assertNull(state.exportError)
    }

    @Test
    fun `exportSubmitted surfaces a render failure and clears the spinner`() = runTest {
        coEvery { renderer.renderBreakdown(any()) } throws RuntimeException("disk full")
        viewModel.exportSubmitted(record)
        val state = viewModel.uiState.value
        assertEquals("disk full", state.exportError)
        assertNull(state.pdfToShare)
        assertFalse(state.isExporting)
    }
}
