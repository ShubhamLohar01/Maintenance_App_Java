package com.candorfoods.factoryops.domain.usecase.auth

import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<User?> = authRepository.getCurrentUser()
}
