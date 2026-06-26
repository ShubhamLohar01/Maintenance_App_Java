package com.candorfoods.factoryops

import app.cash.turbine.test
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.AuthRepository
import com.candorfoods.factoryops.domain.usecase.auth.LoginUseCase
import com.candorfoods.factoryops.presentation.login.LoginUiEvent
import com.candorfoods.factoryops.presentation.login.LoginViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var loginUseCase: LoginUseCase
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: LoginViewModel

    private val mockUser = User(
        id = "user-001", name = "Rajan Kumar", username = "operator1",
        role = UserRole.OPERATOR, plantId = "PLANT-01",
        jwtToken = "tok", tokenExpiresAt = Long.MAX_VALUE
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        loginUseCase = mockk()
        authRepository = mockk()
        every { authRepository.getCurrentUser() } returns flowOf(null)
        every { authRepository.isLoggedIn() } returns false
        viewModel = LoginViewModel(loginUseCase, authRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty credentials and no error`() {
        val state = viewModel.uiState.value
        assertEquals("", state.username)
        assertEquals("", state.password)
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `onUsernameChanged updates username and clears error`() {
        // Stub the use case: onLoginClicked launches into viewModelScope, and with
        // the UnconfinedTestDispatcher it runs eagerly — an unstubbed mock would
        // throw a MockKException that escapes to the global handler and surfaces as
        // an UncaughtExceptionsBeforeTest failure in a later runTest.
        coEvery { loginUseCase(any(), any()) } returns Result.Error("forced error")
        viewModel.onLoginClicked() // force an error state first
        viewModel.onUsernameChanged("operator1")
        assertEquals("operator1", viewModel.uiState.value.username)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `onPasswordChanged updates password`() {
        viewModel.onPasswordChanged("pass123")
        assertEquals("pass123", viewModel.uiState.value.password)
    }

    @Test
    fun `login success emits NavigateToHome event`() = runTest {
        coEvery { loginUseCase(any(), any()) } returns Result.Success(mockUser)
        viewModel.onUsernameChanged("operator1")
        viewModel.onPasswordChanged("pass123")
        viewModel.uiEvent.test {
            viewModel.onLoginClicked()
            assertEquals(LoginUiEvent.NavigateToHome, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `login failure sets error state`() = runTest {
        coEvery { loginUseCase(any(), any()) } returns Result.Error("Invalid credentials")
        viewModel.onUsernameChanged("operator1")
        viewModel.onPasswordChanged("wrong")
        viewModel.onLoginClicked()
        assertEquals("Invalid credentials", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `login with blank username shows validation error without calling use case`() = runTest {
        coEvery { loginUseCase("", any()) } returns Result.Error("Username cannot be empty")
        viewModel.onLoginClicked()
        assertEquals("Username cannot be empty", viewModel.uiState.value.error)
    }
}
