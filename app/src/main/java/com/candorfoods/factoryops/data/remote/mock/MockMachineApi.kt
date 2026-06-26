package com.candorfoods.factoryops.data.remote.mock

import com.candorfoods.factoryops.data.remote.api.MachineApi
import com.candorfoods.factoryops.data.remote.dto.MachineDto
import kotlinx.coroutines.delay

class MockMachineApi : MachineApi {
    override suspend fun getAssignedMachines(token: String): List<MachineDto> {
        delay(400)
        return MockSeedData.machines
    }
}
