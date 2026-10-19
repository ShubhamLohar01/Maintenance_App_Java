package com.candorfoods.factoryops

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.spareparts.SparePart
import com.candorfoods.factoryops.domain.model.spareparts.SparePartLogEntry
import com.candorfoods.factoryops.domain.model.spareparts.SparePartsMachine
import com.candorfoods.factoryops.domain.model.spareparts.WorkType
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.repository.SparePartsRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.presentation.spareparts.SparePartFormViewModel
import com.candorfoods.factoryops.presentation.spareparts.SparePartHistoryViewModel
import com.candorfoods.factoryops.presentation.spareparts.SparePartsDetailEvent
import com.candorfoods.factoryops.presentation.spareparts.SparePartsDetailPhase
import com.candorfoods.factoryops.presentation.spareparts.SparePartsDetailViewModel
import com.candorfoods.factoryops.presentation.spareparts.SparePartsMachineListViewModel
import com.candorfoods.factoryops.presentation.spareparts.components.MachineOption
import com.candorfoods.factoryops.util.excel.Sheet
import com.candorfoods.factoryops.util.excel.SpreadsheetExporter
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Shared fixture: two machines, one W-202 (with a low-stock part) and one A-185. */
private val testMachines = listOf(
    SparePartsMachine(
        machineName = "Sealing Machine 2",
        assetId = "W202-0014",
        assetName = "Sealing Machine 2",
        warehouse = "W202",
        parts = listOf(
            SparePart(1, "V-Belt A42", "nos", 2, minQuantity = 3, lowStock = true),
            SparePart(2, "Gasket", "nos", 9),
        ),
    ),
    SparePartsMachine(
        machineName = "Chiller Unit 1",
        assetId = "A185-0003",
        assetName = "Chiller Unit 1",
        warehouse = "A185",
        parts = listOf(SparePart(3, "Drive Belt", "nos", 5)),
    ),
)

@OptIn(ExperimentalCoroutinesApi::class)
class SparePartsMachineListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: SparePartsRepository
    private lateinit var exporter: SpreadsheetExporter

    private val machines = testMachines

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        exporter = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load populates machines on success`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val viewModel = SparePartsMachineListViewModel(repository, exporter)
        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(2, state.visibleMachines.size)
        assertNull(state.error)
    }

    @Test
    fun `load surfaces an error on failure`() = runTest {
        coEvery { repository.list() } returns Result.Error("HTTP 500")
        val viewModel = SparePartsMachineListViewModel(repository, exporter)
        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("HTTP 500", state.error)
        assertEquals(0, state.visibleMachines.size)
    }

    @Test
    fun `empty search shows machine cards and no part results`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val vm = SparePartsMachineListViewModel(repository, exporter)

        assertEquals(2, vm.uiState.value.visibleMachines.size)
        assertEquals(emptyList<Any>(), vm.uiState.value.partResults)
    }

    @Test
    fun `search flips to flat part results across every machine`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val vm = SparePartsMachineListViewModel(repository, exporter)

        vm.onSearchChange("belt")

        val results = vm.uiState.value.partResults
        assertEquals(listOf("V-Belt A42", "Drive Belt"), results.map { it.part.partName })
        assertEquals("Sealing Machine 2", results.first().machineName)
    }

    @Test
    fun `search also matches the machine name`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val vm = SparePartsMachineListViewModel(repository, exporter)

        vm.onSearchChange("CHILLER")

        assertEquals(listOf("Drive Belt"), vm.uiState.value.partResults.map { it.part.partName })
    }

    @Test
    fun `warehouse filter narrows the list and All restores it`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val vm = SparePartsMachineListViewModel(repository, exporter)

        vm.onWarehouseChange("A185")
        assertEquals(listOf("Chiller Unit 1"), vm.uiState.value.visibleMachines.map { it.displayName })

        vm.onWarehouseChange(null)
        assertEquals(2, vm.uiState.value.visibleMachines.size)
    }

    @Test
    fun `low stock banner counts parts and filters when toggled`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val vm = SparePartsMachineListViewModel(repository, exporter)

        assertEquals(1, vm.uiState.value.lowStockCount)

        vm.onLowStockOnlyChange(true)
        assertEquals(listOf("Sealing Machine 2"), vm.uiState.value.visibleMachines.map { it.displayName })
    }

    // ─── Excel export ────────────────────────────────────────────────────────────
    // exportRows is what Download writes. It must track the filters exactly, or the
    // spreadsheet quietly disagrees with the screen the user was looking at.

    @Test
    fun `export rows are flat and cover every part when nothing is filtered`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val vm = SparePartsMachineListViewModel(repository, exporter)

        assertEquals(
            listOf("V-Belt A42", "Gasket", "Drive Belt"),
            vm.uiState.value.exportRows.map { it.part.partName },
        )
    }

    @Test
    fun `export rows carry the asset id and warehouse for each part`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val vm = SparePartsMachineListViewModel(repository, exporter)

        val first = vm.uiState.value.exportRows.first()
        assertEquals("W202-0014", first.assetId)
        assertEquals("W202", first.warehouse)
        assertEquals("Sealing Machine 2", first.machineName)
    }

    @Test
    fun `export follows the warehouse filter`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val vm = SparePartsMachineListViewModel(repository, exporter)

        vm.onWarehouseChange("A185")

        assertEquals(listOf("Drive Belt"), vm.uiState.value.exportRows.map { it.part.partName })
    }

    @Test
    fun `export follows the low stock filter`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val vm = SparePartsMachineListViewModel(repository, exporter)

        vm.onLowStockOnlyChange(true)

        assertEquals(listOf("V-Belt A42"), vm.uiState.value.exportRows.map { it.part.partName })
    }

    @Test
    fun `export matches the search results while searching`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val vm = SparePartsMachineListViewModel(repository, exporter)

        vm.onSearchChange("belt")

        val state = vm.uiState.value
        assertEquals(state.partResults, state.exportRows)
    }

    @Test
    fun `download writes the sheet and surfaces the result message`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        coEvery { exporter.export(any(), any(), any()) } returns "Saved to Downloads"
        val vm = SparePartsMachineListViewModel(repository, exporter)

        vm.onDownload()

        assertEquals("Saved to Downloads", vm.uiState.value.downloadMessage)
        assertEquals(false, vm.uiState.value.isDownloading)
        coVerify { exporter.export(any<Sheet>(), "Spare parts", any()) }
    }

    @Test
    fun `download message clears once shown`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        coEvery { exporter.export(any(), any(), any()) } returns "Saved to Downloads"
        val vm = SparePartsMachineListViewModel(repository, exporter)
        vm.onDownload()

        vm.onDownloadMessageShown()

        assertNull(vm.uiState.value.downloadMessage)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SparePartsDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: SparePartsRepository

    private val machines = testMachines

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The role arrives as a Flow<User?> from GetCurrentUserUseCase, so the fake
     * emits one user. User carries auth fields the ViewModel never reads. */
    private fun vmFor(
        role: UserRole,
        machineName: String = "Sealing Machine 2",
    ): SparePartsDetailViewModel {
        val getCurrentUser = mockk<GetCurrentUserUseCase>()
        every { getCurrentUser() } returns flowOf(
            User(
                id = "1", name = "Test", username = "u1", role = role,
                plantId = "W202", jwtToken = "t", tokenExpiresAt = 0L,
            )
        )
        return SparePartsDetailViewModel(
            repository, getCurrentUser, SavedStateHandle(mapOf("machine" to machineName)),
        )
    }

    @Test
    fun `load filters to the matching machine's parts`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val vm = vmFor(UserRole.TECHNICIAN)

        val state = vm.uiState.value
        assertEquals(SparePartsDetailPhase.Loaded, state.phase)
        assertEquals(2, state.parts.size)
        assertEquals("W202-0014", state.assetId)
        assertEquals("W202", state.warehouse)
    }

    @Test
    fun `load with an unmatched machine name yields no parts`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val vm = vmFor(UserRole.TECHNICIAN, machineName = "shrink machine")

        assertEquals(SparePartsDetailPhase.Loaded, vm.uiState.value.phase)
        assertEquals(0, vm.uiState.value.parts.size)
    }

    @Test
    fun `use passes the chosen work type through to the repository`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        coEvery { repository.use(1, 2, WorkType.BREAKDOWN, "line 2") } returns
            Result.Success(SparePart(1, "V-Belt A42", "nos", 0, minQuantity = 3, lowStock = true))
        val vm = vmFor(UserRole.TECHNICIAN)

        vm.use(partId = 1, quantity = 2, workType = WorkType.BREAKDOWN, note = "line 2")

        coVerify { repository.use(1, 2, WorkType.BREAKDOWN, "line 2") }
    }

    @Test
    fun `low stock parts sort above the rest`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        val vm = vmFor(UserRole.TECHNICIAN)

        assertEquals(listOf("V-Belt A42", "Gasket"), vm.uiState.value.parts.map { it.partName })
    }

    @Test
    fun `use surfaces the backend refusal without changing stock`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        coEvery { repository.use(1, 5, WorkType.BREAKDOWN, null) } returns
            Result.Error("not enough stock: 2 on hand")
        val vm = vmFor(UserRole.TECHNICIAN)

        vm.events.test {
            vm.use(partId = 1, quantity = 5, workType = WorkType.BREAKDOWN, note = null)
            assertEquals(SparePartsDetailEvent.Error("not enough stock: 2 on hand"), awaitItem())
        }
        assertEquals(2, vm.uiState.value.parts[0].quantity)   // unchanged
        assertNull(vm.uiState.value.busyPartId)
    }

    @Test
    fun `delete surfaces the backend refusal for a technician`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)
        coEvery { repository.delete(1) } returns
            Result.Error("Only a supervisor can delete a spare part")
        val vm = vmFor(UserRole.TECHNICIAN)

        vm.events.test {
            vm.delete(1)
            val event = awaitItem()
            assertEquals(
                "Only a supervisor can delete a spare part",
                (event as SparePartsDetailEvent.Error).message,
            )
        }
    }

    @Test
    fun `canDelete is false for a technician and true for a supervisor and admin`() = runTest {
        coEvery { repository.list() } returns Result.Success(machines)

        assertEquals(false, vmFor(UserRole.TECHNICIAN).uiState.value.canDelete)
        assertEquals(true, vmFor(UserRole.SUPERVISOR).uiState.value.canDelete)
        assertEquals(true, vmFor(UserRole.ADMIN).uiState.value.canDelete)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SparePartFormViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var spares: SparePartsRepository
    private lateinit var machines: MtMachineRepository

    private val assets = listOf(
        MachineOption("W202-0014", "Sealing Machine 2", "W202"),
        MachineOption("A185-0003", "Chiller Unit 1", "A185"),
    )

    /** MtMachineRepository.list() returns the full register; the ViewModel maps it
     * down to MachineOption, so the fake returns MtMachine rows. */
    private fun asset(id: String, name: String, building: String) = MtMachine(
        assetId = id, building = building, assetName = name,
        category = "Packaging", subLocation = "Floor 1",
        ratedKw = null, powerLoadRaw = null, section = MachineCategory.PRODUCTION,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        spares = mockk()
        machines = mockk()
        coEvery { machines.list(any(), any(), any()) } returns Result.Success(listOf(
            asset("W202-0014", "Sealing Machine 2", "W202"),
            asset("A185-0003", "Chiller Unit 1", "A185"),
        ))
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `selecting an asset fills warehouse and locks it`() = runTest {
        val vm = SparePartFormViewModel(spares, machines)

        vm.onAssetSelected(assets[0])

        assertEquals("W202", vm.uiState.value.warehouse)
        assertEquals(true, vm.uiState.value.warehouseLocked)
        assertEquals("W202-0014", vm.uiState.value.assetId)
    }

    @Test
    fun `other path requires both a machine name and a warehouse`() = runTest {
        val vm = SparePartFormViewModel(spares, machines)
        vm.onChooseOther()
        vm.onPartNameChange("V-Belt A42")

        assertEquals(false, vm.uiState.value.canSave)

        vm.onMachineNameChange("Hand Trolley")
        assertEquals(false, vm.uiState.value.canSave)

        vm.onWarehouseChange("A185")
        assertEquals(true, vm.uiState.value.canSave)
    }

    @Test
    fun `save sends the asset path with a null machine name`() = runTest {
        coEvery {
            spares.create("W202-0014", null, null, "V-Belt A42", "nos", 5, 2)
        } returns Result.Success(SparePart(1, "V-Belt A42", "nos", 5, 2, false))
        val vm = SparePartFormViewModel(spares, machines)
        vm.onAssetSelected(assets[0])
        vm.onPartNameChange("V-Belt A42")
        vm.onUnitChange("nos")
        vm.onQuantityChange("5")
        vm.onMinQuantityChange("2")

        vm.save()

        coVerify { spares.create("W202-0014", null, null, "V-Belt A42", "nos", 5, 2) }
    }

    @Test
    fun `a duplicate name surfaces the backend message inline`() = runTest {
        coEvery { spares.create(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Error("'V-Belt A42' already exists for this machine")
        val vm = SparePartFormViewModel(spares, machines)
        vm.onAssetSelected(assets[0])
        vm.onPartNameChange("V-Belt A42")

        vm.save()

        assertEquals("'V-Belt A42' already exists for this machine", vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.saved)
    }

    @Test
    fun `a failed asset fetch still allows the other path`() = runTest {
        coEvery { machines.list(any(), any(), any()) } returns Result.Error("offline")
        val vm = SparePartFormViewModel(spares, machines)

        assertEquals(emptyList<MachineOption>(), vm.uiState.value.assetOptions)
        assertEquals(
            "Asset list unavailable — enter the machine manually",
            vm.uiState.value.assetLoadError,
        )

        vm.onChooseOther()
        vm.onMachineNameChange("Hand Trolley")
        vm.onWarehouseChange("W202")
        vm.onPartNameChange("Bolt")
        assertEquals(true, vm.uiState.value.canSave)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SparePartHistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: SparePartsRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `history loads entries for the part in the saved state handle`() = runTest {
        coEvery { repository.history(7) } returns Result.Success(
            listOf(
                SparePartLogEntry(2, "Restock", "IN", 5, null, "Sup", "2026-09-07T10:00:00"),
                SparePartLogEntry(1, "Breakdown", "OUT", 1, "line 2", "Tech", "2026-09-06T09:00:00"),
            )
        )
        val vm = SparePartHistoryViewModel(repository, SavedStateHandle(mapOf("partId" to 7)))

        val entries = vm.uiState.value.entries
        assertEquals(listOf("Restock", "Breakdown"), entries.map { it.action })
        assertEquals("+5", entries[0].signedQuantity)
        assertEquals("-1", entries[1].signedQuantity)
    }

    @Test
    fun `history surfaces a load error`() = runTest {
        coEvery { repository.history(7) } returns Result.Error("offline")
        val vm = SparePartHistoryViewModel(repository, SavedStateHandle(mapOf("partId" to 7)))

        assertEquals("offline", vm.uiState.value.error)
    }
}
