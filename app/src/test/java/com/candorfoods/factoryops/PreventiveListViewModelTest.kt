package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.ChecklistRecordDetail
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import com.candorfoods.factoryops.presentation.checklist.PreventiveListViewModel
import com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer
import io.mockk.coEvery
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PreventiveListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: ChecklistRepository
    private lateinit var renderer: ChecklistPdfRenderer
    private lateinit var viewModel: PreventiveListViewModel

    private val detail = ChecklistRecordDetail(
        id = 7, formType = "MONTHLY", status = "SUBMITTED", docNo = "CFPLA.C4.F.50a",
        checklistDate = "2026-06-25", doneBy = "Tech", checkedBy = "", verifiedBy = "",
        remarks = "", createdAt = "", items = emptyList(),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk()
        renderer = mockk()
        coEvery { repo.list() } returns Result.Success(emptyList())   // init refresh()
        viewModel = PreventiveListViewModel(repo, renderer)
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
}
