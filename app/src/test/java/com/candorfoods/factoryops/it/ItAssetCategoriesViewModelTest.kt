package com.candorfoods.factoryops.it

import com.candorfoods.factoryops.domain.model.ItAssetCategory
import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetMachineMasterUseCase
import com.candorfoods.factoryops.presentation.it.ItAssetCategoriesViewModel
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItAssetCategoriesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: MtMachineRepository
    private lateinit var getMachineMaster: GetMachineMasterUseCase

    private fun asset(id: String, category: String) = MtMachine(
        assetId = id, building = "A-185", assetName = "Sample $id",
        category = category, subLocation = "1st Floor", ratedKw = null,
        powerLoadRaw = null, section = MachineCategory.OTHERS,
    )

    @BeforeEach fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Mock the repository interface and use the real use case — GetMachineMasterUseCase
        // is a final class whose invoke() has default args, which is awkward to stub.
        repository = mockk()
        getMachineMaster = GetMachineMasterUseCase(repository)
    }

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test fun `counts only the assets of each IT category`() = runTest {
        coEvery { repository.list(null, null, null) } returns Result.Success(
            listOf(
                asset("1", "Electric Asset"),
                asset("2", "electric asset"),          // casing variant still counts
                asset("3", "IT/Communication"),
                asset("4", "Production Equipment"),    // not IT — ignored
            )
        )
        val vm = ItAssetCategoriesViewModel(getMachineMaster)
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(2, state.countOf(ItAssetCategory.ELECTRICAL))
        assertEquals(1, state.countOf(ItAssetCategory.IT_COMMUNICATION))
    }

    @Test fun `a load failure surfaces the message and zero counts`() = runTest {
        coEvery { repository.list(null, null, null) } returns Result.Error("offline")
        val vm = ItAssetCategoriesViewModel(getMachineMaster)
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("offline", state.error)
        assertEquals(0, state.countOf(ItAssetCategory.ELECTRICAL))
    }

    @Test fun `retry reloads the register`() = runTest {
        coEvery { repository.list(null, null, null) } returns Result.Error("offline")
        val vm = ItAssetCategoriesViewModel(getMachineMaster)
        coEvery { repository.list(null, null, null) } returns
            Result.Success(listOf(asset("1", "IT/Communication")))
        vm.onRetry()
        assertEquals(1, vm.uiState.value.countOf(ItAssetCategory.IT_COMMUNICATION))
        assertNull(vm.uiState.value.error)
    }
}
