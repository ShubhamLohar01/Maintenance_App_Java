package com.candorfoods.factoryops.domain.model

data class Machine(
    val id: String,
    val code: String,
    val name: String,
    val location: String,
    val building: String,
    val plantId: String,
    val machineType: MachineType,
    val category: String? = null,
    val ratedKw: Double,
    val loadFactor: Double,
    val loadFactorSource: LoadFactorSource,
    val criticality: Criticality,
    val expectedRunHours: Double,
    val currentStatus: MachineStatus,
    val updatedAt: Long
)
