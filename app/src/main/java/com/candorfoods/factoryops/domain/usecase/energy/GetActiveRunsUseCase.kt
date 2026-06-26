package com.candorfoods.factoryops.domain.usecase.energy

import com.candorfoods.factoryops.domain.model.ActiveRun
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.EnergyRepository
import javax.inject.Inject

/**
 * Live snapshot of production runs in progress across all operators/devices.
 * Used by the Head machine list to mark which assets are "currently working".
 */
class GetActiveRunsUseCase @Inject constructor(
    private val repository: EnergyRepository,
) {
    suspend operator fun invoke(): Result<List<ActiveRun>> = repository.getActiveRuns()
}
