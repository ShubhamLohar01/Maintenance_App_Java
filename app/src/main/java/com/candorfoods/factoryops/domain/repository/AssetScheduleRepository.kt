package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.ElectricAssetSchedule
import com.candorfoods.factoryops.domain.model.Result

interface AssetScheduleRepository {
    /** Electric assets + their daily recording schedule. plantId: "W202" / "A185" / "both". */
    suspend fun list(plantId: String = "both"): Result<List<ElectricAssetSchedule>>

    /** Supervisor: set/replace an asset's daily window (minute-of-day, IST). */
    suspend fun setSchedule(
        machineId: String,
        startMin: Int,
        endMin: Int,
        active: Boolean,
    ): Result<ElectricAssetSchedule>

    /** Supervisor: clear an asset's schedule (past records are kept). */
    suspend fun clearSchedule(machineId: String): Result<ElectricAssetSchedule>
}
