package com.candorfoods.factoryops.domain.usecase.mtmachine

import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import javax.inject.Inject

class GetMachineMasterUseCase @Inject constructor(
    private val repository: MtMachineRepository,
) {
    suspend operator fun invoke(
        building: String? = null,
        subLocation: String? = null,
        subCategory: String? = null,
    ): Result<List<MtMachine>> = repository.list(building, subLocation, subCategory)
}
