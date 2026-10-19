package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.ElectricAssetSchedule
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.ScheduleInterval

interface AssetScheduleRepository {
    /** Electric assets + their daily recording schedule. plantId: "W202" / "A185" / "both". */
    suspend fun list(plantId: String = "both"): Result<List<ElectricAssetSchedule>>

    /** Supervisor: set/replace an asset's daily windows (minute-of-day, IST).
     *  [intervals] must be non-empty, sorted and non-overlapping — the same windows run
     *  on every day in `days` (0=Sunday … 6=Saturday). `is24h` = run all day instead. */
    suspend fun setSchedule(
        machineId: String,
        intervals: List<ScheduleInterval>,
        active: Boolean,
        days: Set<Int>,
        is24h: Boolean,
    ): Result<ElectricAssetSchedule>

    /** Supervisor: clear an asset's schedule (past records are kept). */
    suspend fun clearSchedule(machineId: String): Result<ElectricAssetSchedule>
}
