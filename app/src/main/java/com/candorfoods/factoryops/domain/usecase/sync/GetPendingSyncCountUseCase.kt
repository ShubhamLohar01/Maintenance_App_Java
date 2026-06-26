package com.candorfoods.factoryops.domain.usecase.sync

import com.candorfoods.factoryops.domain.repository.EnergyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPendingSyncCountUseCase @Inject constructor(
    private val energyRepository: EnergyRepository
) {
    operator fun invoke(): Flow<Int> = energyRepository.getPendingSyncCount()
}
