package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.SyncStatus
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.AuthRepository
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.presentation.checklist.BreakdownListViewModel
import com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer
import io.mockk.coEvery
import io.mockk.coVerify
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
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: BreakdownListViewModel

    private val record = BreakdownRecord(
        id = 1, docNo = "CFPLA.C4.F.06", recordDate = "2026-06-25", location = "A-185",
        machineName = "Mixer", equipmentModelNo = "M1", problemInBrief = "Jam",
        typeOfMaintenance = "Corrective", partOfMachine = "Belt", temporaryReason = "",
        durationStart = "10:00", durationEnd = "10:40", machineOperatorSign = "Op",
        maintenancePersonSign = "Tech", qcClearanceSign = "QC", verifiedBy = "Head", createdAt = "",
    )

    private fun user(plantId: String) = User(
        id = "3", name = "Tech", username = "t1", role = UserRole.TECHNICIAN,
        plantId = plantId, jwtToken = "t", tokenExpiresAt = 0L,
    )

    private fun viewModelFor(plantId: String): BreakdownListViewModel {
        every { authRepository.getCurrentUser() } returns flowOf(user(plantId))
        return BreakdownListViewModel(
            breakdownRepo, checklistRepo, renderer, GetCurrentUserUseCase(authRepository),
        )
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        breakdownRepo = mockk(relaxed = true)
        checklistRepo = mockk()
        renderer = mockk()
        authRepository = mockk()
        every { authRepository.getCurrentUser() } returns flowOf(user("W202"))
        every { breakdownRepo.observeAllFlags() } returns flowOf(emptyList())
        coEvery { checklistRepo.listBreakdowns() } returns Result.Success(emptyList())  // init loadSubmitted()
        viewModel = BreakdownListViewModel(
            breakdownRepo, checklistRepo, renderer, GetCurrentUserUseCase(authRepository),
        )
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

    // ─── Plant scoping ────────────────────────────────────────────────────────────

    private fun flag(id: String, building: String?) = BreakdownFlag(
        id = id, machineId = "M-$id", assetName = "Mixer", building = building,
        operatorId = "op", severity = Severity.MAJOR, description = "Jam",
        beforePhotoLocalPath = null, raisedAt = 0L, status = BreakdownStatus.PENDING_QC,
        acknowledgedBy = null, acknowledgedByName = null, acknowledgedAt = null,
        resolvedBy = null, resolvedByName = null, resolvedAt = null, partsUsed = null,
        afterPhotoLocalPath = null, qcStatus = null, qcAcknowledgedBy = null,
        qcAcknowledgedByName = null, qcAcknowledgedAt = null, qcDecidedBy = null,
        qcDecidedByName = null, qcDecidedAt = null, qcDecisionNotes = null,
        qcAfterPhotoLocalPath = null, qcChecklistJson = null, qcOverrideFromUserId = null,
        qcOverrideFromName = null, qcOverrideAt = null, syncStatus = SyncStatus.SYNCED,
    )

    private val bothPlants = listOf(
        record.copy(id = 1, docNo = "CFPLA.C4.F.06"),
        record.copy(id = 2, docNo = "CFPLB.C4.F.61"),
    )

    @Test
    fun `an A185 technician sees only A185 submitted records`() = runTest {
        coEvery { checklistRepo.listBreakdowns() } returns Result.Success(bothPlants)
        assertEquals(listOf(2), viewModelFor("A-185-Koparkhairne").uiState.value.submitted.map { it.id })
        assertEquals(listOf(1), viewModelFor("W-202-Koparkhairne").uiState.value.submitted.map { it.id })
    }

    @Test
    fun `open flags are scoped to the technician's plant`() = runTest {
        every { breakdownRepo.observeAllFlags() } returns flowOf(
            listOf(flag("w", "W-202"), flag("a", "A-185"), flag("x", null)),
        )
        // A flag with no building can't be attributed, so it stays visible to both rather
        // than hiding work that still needs doing.
        assertEquals(listOf("a", "x"), viewModelFor("A185").uiState.value.items.map { it.flag.id })
        assertEquals(listOf("w", "x"), viewModelFor("W202").uiState.value.items.map { it.flag.id })
    }

    @Test
    fun `only the technician's own plant is synced from the backend`() = runTest {
        viewModelFor("A-185-Koparkhairne")
        coVerify { breakdownRepo.refreshOpenFlags("A185") }
        coVerify(exactly = 0) { breakdownRepo.refreshOpenFlags("both") }
    }
}
