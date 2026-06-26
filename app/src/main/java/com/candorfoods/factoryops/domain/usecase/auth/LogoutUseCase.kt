package com.candorfoods.factoryops.domain.usecase.auth

import com.candorfoods.factoryops.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() = authRepository.logout()
}
