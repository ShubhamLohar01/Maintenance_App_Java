package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.MachineDto
import retrofit2.http.GET
import retrofit2.http.Header

interface MachineApi {
    @GET("machines/assigned")
    suspend fun getAssignedMachines(@Header("Authorization") token: String): List<MachineDto>
}
