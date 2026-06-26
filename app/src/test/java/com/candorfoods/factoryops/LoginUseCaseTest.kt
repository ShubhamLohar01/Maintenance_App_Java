package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.AuthRepository
import com.candorfoods.factoryops.domain.usecase.auth.LoginUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LoginUseCaseTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var loginUseCase: LoginUseCase

    private val mockUser = User(
        id = "user-001", name = "Rajan Kumar", username = "operator1",
        role = UserRole.OPERATOR, plantId = "PLANT-01",
        jwtToken = "mock_token", tokenExpiresAt = Long.MAX_VALUE
    )

    @BeforeEach
    fun setUp() {
        authRepository = mockk()
        loginUseCase = LoginUseCase(authRepository)
    }

    @Test
    fun `returns error when username is blank`() = runTest {
        val result = loginUseCase("", "pass123")
        assertTrue(result is Result.Error)
        assertEquals("Username cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun `returns error when password is blank`() = runTest {
        val result = loginUseCase("operator1", "")
        assertTrue(result is Result.Error)
        assertEquals("Password cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun `delegates to repository when credentials are non-blank`() = runTest {
        coEvery { authRepository.login("operator1", "pass123") } returns Result.Success(mockUser)
        val result = loginUseCase("operator1", "pass123")
        assertTrue(result is Result.Success)
        assertEquals(mockUser, (result as Result.Success).data)
        coVerify(exactly = 1) { authRepository.login("operator1", "pass123") }
    }

    @Test
    fun `propagates repository error`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns Result.Error("Invalid credentials")
        val result = loginUseCase("operator1", "wrong")
        assertTrue(result is Result.Error)
        assertEquals("Invalid credentials", (result as Result.Error).message)
    }

    @Test
    fun `does not call repository when username is whitespace`() = runTest {
        val result = loginUseCase("   ", "pass123")
        assertTrue(result is Result.Error)
        coVerify(exactly = 0) { authRepository.login(any(), any()) }
    }
}
