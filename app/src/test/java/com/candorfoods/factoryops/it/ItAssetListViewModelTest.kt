package com.candorfoods.factoryops.it

import com.candorfoods.factoryops.domain.model.ItAssetCategory
import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetMachineMasterUseCase
import com.candorfoods.factoryops.presentation.it.ItAssetListViewModel
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItAssetListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var getMachineMaster: GetMachineMasterUseCase
    private lateinit var repository: MtMachineRepository

    private fun asset(
        id: String,
        category: String = "IT/Communication",
        building: String = "A-185",
        floor: String = "1st Floor",
        name: String = "Sample $id",
    ) = MtMachine(
        assetId = id, building = building, assetName = name,
        category = category, subLocation = floor, ratedKw = null,
        powerLoadRaw = null, section = MachineCategory.OTHERS,
    )

    private val register = listOf(
        asset("A185-0001", name = "Router"),
        asset("A185-0002", name = "Switch", floor = "2nd Floor"),
        asset("W202-0003", name = "Desk Phone", building = "W-202"),
        asset("A185-0004", category = "Electric Asset", name = "Ceiling Fan"),
        asset("A185-0005", category = "Production Equipment", name = "Roaster"),
    )

    /** The screen pushes its category in via onCategory(); nav reads the route arg. */
    private fun viewModel(category: ItAssetCategory = ItAssetCategory.IT_COMMUNICATION) =
        ItAssetListViewModel(getMachineMaster, repository).apply { onCategory(category) }

    @BeforeEach fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Mock the repository interface and use the real use case — GetMachineMasterUseCase
        // is a final class whose invoke() has default args, which is awkward to stub.
        repository = mockk()
        getMachineMaster = GetMachineMasterUseCase(repository)
        coEvery { repository.list(null, null, null) } returns Result.Success(register)
    }

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test fun `only the requested category is listed`() = runTest {
        val vm = viewModel()
        val ids = vm.uiState.value.items.map { it.assetId }
        assertEquals(listOf("A185-0001", "A185-0002", "W202-0003"), ids)
    }

    @Test fun `the electrical category lists its own assets`() = runTest {
        val vm = viewModel(ItAssetCategory.ELECTRICAL)
        assertEquals(listOf("A185-0004"), vm.uiState.value.items.map { it.assetId })
    }

    @Test fun `building filter narrows the list and resets the floor filter`() = runTest {
        val vm = viewModel()
        vm.onFloorSelected("2nd Floor")
        vm.onBuildingSelected("W-202")
        assertNull(vm.uiState.value.selectedFloor)
        assertEquals(listOf("W202-0003"), vm.uiState.value.items.map { it.assetId })
    }

    @Test fun `floor filter narrows the list`() = runTest {
        val vm = viewModel()
        vm.onFloorSelected("2nd Floor")
        assertEquals(listOf("A185-0002"), vm.uiState.value.items.map { it.assetId })
    }

    @Test fun `search matches name id and floor`() = runTest {
        val vm = viewModel()
        vm.onSearchChange("router")
        assertEquals(listOf("A185-0001"), vm.uiState.value.items.map { it.assetId })
        vm.onSearchChange("W202")
        assertEquals(listOf("W202-0003"), vm.uiState.value.items.map { it.assetId })
        vm.onSearchChange("2nd")
        assertEquals(listOf("A185-0002"), vm.uiState.value.items.map { it.assetId })
    }

    @Test fun `floor options are scoped to the selected building`() = runTest {
        val vm = viewModel()
        assertEquals(listOf("1st Floor", "2nd Floor"), vm.uiState.value.floors)
        vm.onBuildingSelected("W-202")
        assertEquals(listOf("1st Floor"), vm.uiState.value.floors)
    }

    @Test fun `submitting an asset posts it with the locked category and refreshes`() = runTest {
        val vm = viewModel()
        val posted = slot<MtMachine>()
        coEvery { repository.addAsset(capture(posted)) } returns
            Result.Success(asset("A185-0006", name = "New AP"))
        vm.onAddClicked()
        assertTrue(vm.uiState.value.showAddSheet)

        vm.onSubmitNewAsset(asset("", name = "New AP"))

        assertEquals("IT/Communication", posted.captured.category)
        assertFalse(vm.uiState.value.showAddSheet)
        assertNull(vm.uiState.value.addError)
        // Once in init + once after a successful add.
        coVerify(exactly = 2) { repository.list(null, null, null) }
    }

    @Test fun `a rejected add keeps the sheet open and shows the backend message`() = runTest {
        val vm = viewModel()
        coEvery { repository.addAsset(any()) } returns Result.Error("Not allowed to add assets")
        vm.onAddClicked()
        vm.onSubmitNewAsset(asset("", name = "New AP"))
        assertTrue(vm.uiState.value.showAddSheet)
        assertEquals("Not allowed to add assets", vm.uiState.value.addError)
        assertFalse(vm.uiState.value.isAddingAsset)
    }

    @Test fun `a load failure surfaces the message`() = runTest {
        coEvery { repository.list(null, null, null) } returns Result.Error("offline")
        val vm = viewModel()
        assertEquals("offline", vm.uiState.value.error)
        assertTrue(vm.uiState.value.items.isEmpty())
    }

    @Test fun `switching category clears the filters`() = runTest {
        val vm = viewModel()
        vm.onSearchChange("router")
        vm.onFloorSelected("1st Floor")
        vm.onCategory(ItAssetCategory.ELECTRICAL)
        val state = vm.uiState.value
        assertEquals(ItAssetCategory.ELECTRICAL, state.category)
        assertEquals("", state.searchQuery)
        assertNull(state.selectedFloor)
        assertEquals(listOf("A185-0004"), state.items.map { it.assetId })
    }
}
