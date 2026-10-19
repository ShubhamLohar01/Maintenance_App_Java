package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.ChecklistRecordDetail
import com.candorfoods.factoryops.domain.model.ChecklistRecordSummary
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.AuthRepository
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.presentation.checklist.PreventiveListViewModel
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PreventiveListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: ChecklistRepository
    private lateinit var renderer: ChecklistPdfRenderer
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: PreventiveListViewModel

    private val detail = ChecklistRecordDetail(
        id = 7, formType = "MONTHLY", status = "SUBMITTED", docNo = "CFPLA.C4.F.50a",
        checklistDate = "2026-06-25", doneBy = "Tech", checkedBy = "", verifiedBy = "",
        remarks = "", createdAt = "", items = emptyList(),
    )

    private fun summary(id: Int, formType: String, docNo: String) = ChecklistRecordSummary(
        id = id, formType = formType, status = "SUBMITTED", docNo = docNo,
        checklistDate = "2026-08-08", doneBy = "Tech", createdAt = "",
    )

    private fun user(plantId: String) = User(
        id = "3", name = "Tech", username = "t1", role = UserRole.TECHNICIAN,
        plantId = plantId, jwtToken = "t", tokenExpiresAt = 0L,
    )

    private fun viewModelFor(plantId: String): PreventiveListViewModel {
        every { authRepository.getCurrentUser() } returns flowOf(user(plantId))
        return PreventiveListViewModel(repo, renderer, GetCurrentUserUseCase(authRepository))
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk()
        renderer = mockk()
        authRepository = mockk()
        every { authRepository.getCurrentUser() } returns flowOf(user("W202"))
        coEvery { repo.list() } returns Result.Success(emptyList())   // init refresh()
        viewModel = PreventiveListViewModel(repo, renderer, GetCurrentUserUseCase(authRepository))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `exportPdf fetches the record, renders it, and exposes the file to share`() = runTest {
        val file = File("rec7.pdf")
        coEvery { repo.detail(7) } returns Result.Success(detail)
        coEvery { renderer.renderPreventive(detail) } returns file
        viewModel.exportPdf(7)
        val state = viewModel.uiState.value
        assertEquals(file, state.pdfToShare)
        assertNull(state.exportingId)
        assertNull(state.exportError)
    }

    @Test
    fun `exportPdf surfaces a detail-load error and clears the spinner`() = runTest {
        coEvery { repo.detail(7) } returns Result.Error("offline")
        viewModel.exportPdf(7)
        val state = viewModel.uiState.value
        assertEquals("offline", state.exportError)
        assertNull(state.pdfToShare)
        assertNull(state.exportingId)
    }

    @Test
    fun `exportPdf surfaces a render failure`() = runTest {
        coEvery { repo.detail(7) } returns Result.Success(detail)
        coEvery { renderer.renderPreventive(detail) } throws RuntimeException("no cache dir")
        viewModel.exportPdf(7)
        assertEquals("no cache dir", viewModel.uiState.value.exportError)
        assertNull(viewModel.uiState.value.pdfToShare)
    }

    // ─── Plant scoping: the endpoint returns every plant's records ────────────────

    private val bothPlants = listOf(
        summary(1, "MONTHLY", "CFPLA.C4.F.50a"),
        summary(2, "QUARTERLY", "CFPLA.C4.F.50b"),
        summary(3, "A185_QUARTERLY", "CFPLB.C4.F.41"),
        summary(4, "A185_MONTHLY", "CFPLB.C4.F.41"),
    )

    @Test
    fun `an A185 technician sees only A185 records`() = runTest {
        coEvery { repo.list() } returns Result.Success(bothPlants)
        val vm = viewModelFor("A-185-Koparkhairne")
        assertEquals(listOf(3, 4), vm.uiState.value.records.map { it.id })
    }

    @Test
    fun `a W202 technician sees only W202 records`() = runTest {
        coEvery { repo.list() } returns Result.Success(bothPlants)
        val vm = viewModelFor("W-202-Koparkhairne")
        assertEquals(listOf(1, 2), vm.uiState.value.records.map { it.id })
    }

    /** Records predating the A-185 forms — including the PM sync's auto-generated ones,
     *  which carry no recognisable form type — are W-202's and stay with W-202. */
    @Test
    fun `unattributable records stay with W202`() = runTest {
        coEvery { repo.list() } returns Result.Success(listOf(summary(9, "", "")))
        assertEquals(listOf(9), viewModelFor("W202").uiState.value.records.map { it.id })
        assertEquals(emptyList<Int>(), viewModelFor("A185").uiState.value.records.map { it.id })
    }
}
