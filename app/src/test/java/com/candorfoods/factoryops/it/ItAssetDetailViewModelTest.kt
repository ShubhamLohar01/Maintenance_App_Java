package com.candorfoods.factoryops.it

import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.AuthRepository
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.presentation.it.ItAssetDetailViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItAssetDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: MtMachineRepository
    private lateinit var authRepository: AuthRepository

    private val asset = MtMachine(
        assetId = "A185-0001",
        building = "A-185",
        assetName = "Router",
        category = "IT/Communication",
        subLocation = "1st Floor",
        ratedKw = null,
        powerLoadRaw = "40Watt",
        section = MachineCategory.OTHERS,
        quantity = 2,
        condition = "Working",
        modelNo = "RB-750",
        serialNo = "SN-1",
        remarks = "Rack 3",
    )

    private fun user(role: UserRole) = User(
        id = "7", name = "IT User", username = "it1", role = role,
        plantId = "A-185", jwtToken = "t", tokenExpiresAt = 0L,
    )

    private fun viewModel(
        role: UserRole = UserRole.IT_TECHNICIAN,
        assetId: String = "A185-0001",
    ): ItAssetDetailViewModel {
        every { authRepository.getCurrentUser() } returns flowOf(user(role))
        return ItAssetDetailViewModel(repository, GetCurrentUserUseCase(authRepository))
            .apply { onAssetId(assetId) }
    }

    @BeforeEach fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        authRepository = mockk()
        coEvery { repository.getByAssetId("A185-0001") } returns Result.Success(asset)
    }

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test fun `loads the asset by id`() = runTest {
        val state = viewModel().uiState.value
        assertEquals("Router", state.asset?.assetName)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test fun `an unknown id shows a not-found error`() = runTest {
        coEvery { repository.getByAssetId("A185-9999") } returns Result.Success(null)
        val state = viewModel(assetId = "A185-9999").uiState.value
        assertNull(state.asset)
        assertEquals("Asset A185-9999 was not found.", state.error)
    }

    @Test fun `a load failure surfaces the backend message`() = runTest {
        coEvery { repository.getByAssetId("A185-0001") } returns Result.Error("offline")
        val state = viewModel().uiState.value
        assertNull(state.asset)
        assertEquals("offline", state.error)
    }

    @Test fun `opening the sheet prefills the form from the asset`() = runTest {
        val vm = viewModel()
        vm.onEditClicked()
        val form = vm.uiState.value.editForm
        assertTrue(vm.uiState.value.showEditSheet)
        assertEquals("Router", form.assetName)
        assertEquals("A-185", form.building)
        assertEquals("1st Floor", form.subLocation)
        assertEquals("2", form.quantity)
        assertEquals("40Watt", form.powerLoad)
        assertEquals("RB-750", form.modelNo)
        assertEquals("Rack 3", form.remarks)
    }

    @Test fun `a successful save swaps in the row the backend returned`() = runTest {
        val vm = viewModel()
        val sent = slot<MtMachine>()
        val saved = asset.copy(assetName = "Core Router", ratedKw = 0.04)
        coEvery { repository.updateAsset(capture(sent)) } returns Result.Success(saved)

        vm.onEditClicked()
        vm.onEditFormChange { it.copy(assetName = "Core Router") }
        vm.onSaveEdit()

        assertEquals("Core Router", sent.captured.assetName)
        assertEquals("A185-0001", sent.captured.assetId)
        val state = vm.uiState.value
        assertFalse(state.showEditSheet)
        assertFalse(state.isSavingEdit)
        assertEquals(0.04, state.asset!!.ratedKw!!, 1e-9)
    }

    /** Category is locked in the UI; the save must carry the loaded value, not the form. */
    @Test fun `saving keeps the asset in its own category`() = runTest {
        val vm = viewModel()
        val sent = slot<MtMachine>()
        coEvery { repository.updateAsset(capture(sent)) } returns Result.Success(asset)

        vm.onEditClicked()
        vm.onEditFormChange { it.copy(category = "Production Equipment") }
        vm.onSaveEdit()

        assertEquals("IT/Communication", sent.captured.category)
    }

    @Test fun `a rejected save keeps the sheet open and shows the backend message`() = runTest {
        val vm = viewModel()
        coEvery { repository.updateAsset(any()) } returns Result.Error("Not allowed to edit assets")

        vm.onEditClicked()
        vm.onSaveEdit()

        val state = vm.uiState.value
        assertTrue(state.showEditSheet)
        assertEquals("Not allowed to edit assets", state.editError)
        assertFalse(state.isSavingEdit)
    }

    @Test fun `a blank name or building blocks the call`() = runTest {
        val vm = viewModel()
        vm.onEditClicked()
        vm.onEditFormChange { it.copy(assetName = "  ") }
        vm.onSaveEdit()

        assertEquals("Asset name and building are required.", vm.uiState.value.editError)
        assertTrue(vm.uiState.value.showEditSheet)
        // updateAsset was never stubbed — reaching it would have thrown.
        assertNotNull(vm.uiState.value.asset)
    }

    @Test fun `only write roles may edit`() = runTest {
        assertTrue(viewModel(UserRole.IT_TECHNICIAN).uiState.value.canEdit)
        assertTrue(viewModel(UserRole.SUPERVISOR).uiState.value.canEdit)
        assertFalse(viewModel(UserRole.OPERATOR).uiState.value.canEdit)
        assertFalse(viewModel(UserRole.TECHNICIAN).uiState.value.canEdit)
    }
}
