package com.candorfoods.factoryops.domain.usecase.mtmachine

import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.isRunnable
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import javax.inject.Inject

/**
 * Runnable production machinery from the asset register: assets with a non-blank
 * power_load whose category isn't in the non-machinery blocklist (see isRunnable).
 */
class GetRunnableAssetsUseCase @Inject constructor(
    private val repository: MtMachineRepository,
) {
    suspend operator fun invoke(): Result<List<MtMachine>> = when (val r = repository.list()) {
        is Result.Success -> Result.Success(
            r.data.filter { it.isRunnable() }.sortedBy { it.assetName.lowercase() }
        )
        is Result.Error -> r
        is Result.Loading -> r
    }
}
