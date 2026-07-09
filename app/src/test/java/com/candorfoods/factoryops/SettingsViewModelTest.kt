package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.local.LanguagePreferences
import com.candorfoods.factoryops.domain.model.AppLanguage
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.domain.usecase.auth.LogoutUseCase
import com.candorfoods.factoryops.presentation.settings.SettingsViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var getCurrentUser: GetCurrentUserUseCase
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var languagePreferences: LanguagePreferences

    private val user = User(
        id = "user-001", name = "Rajan", username = "operator1",
        role = UserRole.OPERATOR, plantId = "PLANT-01",
        jwtToken = "t", tokenExpiresAt = Long.MAX_VALUE,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getCurrentUser = mockk()
        logoutUseCase = mockk(relaxed = true)
        languagePreferences = mockk(relaxed = true)
        every { languagePreferences.currentLanguage } returns MutableStateFlow(AppLanguage.ENGLISH)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test fun `onLanguageSelected persists choice for current user`() {
        every { getCurrentUser() } returns flowOf(user)
        val vm = SettingsViewModel(getCurrentUser, logoutUseCase, languagePreferences)
        vm.onLanguageSelected(AppLanguage.HINDI)
        verify { languagePreferences.setLanguageForUser("user-001", AppLanguage.HINDI) }
    }

    @Test fun `onLanguageSelected no-ops when no current user`() {
        every { getCurrentUser() } returns flowOf(null)
        val vm = SettingsViewModel(getCurrentUser, logoutUseCase, languagePreferences)
        vm.onLanguageSelected(AppLanguage.HINDI)
        verify(exactly = 0) { languagePreferences.setLanguageForUser(any(), any()) }
    }
}
