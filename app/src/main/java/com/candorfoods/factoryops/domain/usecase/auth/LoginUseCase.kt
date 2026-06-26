package com.candorfoods.factoryops.domain.usecase.auth

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(username: String, password: String): Result<User> {
        if (username.isBlank()) return Result.Error("Username cannot be empty")
        if (password.isBlank()) return Result.Error("Password cannot be empty")
        return authRepository.login(username, password)
    }
}
