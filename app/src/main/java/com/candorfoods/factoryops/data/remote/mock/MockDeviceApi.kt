package com.candorfoods.factoryops.data.remote.mock

import com.candorfoods.factoryops.data.remote.api.DeviceApi
import com.candorfoods.factoryops.data.remote.dto.DeviceTokenRequestDto
import retrofit2.Response

/** Mock: there is no push backend in mock mode, so token registration is a no-op success. */
class MockDeviceApi : DeviceApi {
    override suspend fun registerToken(
        token: String,
        body: DeviceTokenRequestDto,
    ): Response<Unit> = Response.success(Unit)
}
