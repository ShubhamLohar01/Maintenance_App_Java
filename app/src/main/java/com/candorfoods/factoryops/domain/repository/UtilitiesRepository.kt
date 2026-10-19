package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.utilities.UtilityPrefill
import com.candorfoods.factoryops.domain.model.utilities.UtilityRates
import com.candorfoods.factoryops.domain.model.utilities.UtilityRecord
import com.candorfoods.factoryops.domain.model.utilities.UtilityType

/** Daily utility consumption records, backed by the backend /utilities/{util} API. */
interface UtilitiesRepository {

    /** Upsert one day's reading (backend keys on plant + reading_date). Returns the stored row. */
    suspend fun submit(type: UtilityType, record: UtilityRecord): Result<UtilityRecord>

    /** History for a utility, newest first. `plant` null → both plants. */
    suspend fun list(type: UtilityType, plant: String?): Result<List<UtilityRecord>>

    /** Current supervisor-set prices for a plant (empty rates when never set). */
    suspend fun rates(plant: String): Result<UtilityRates>

    /** Set one or more rates for a plant (SUPERVISOR/HEAD/ADMIN). Null values are skipped. */
    suspend fun setRates(plant: String, rates: Map<String, Double?>): Result<UtilityRates>

    /** Opening fields (from the previous reading's closing) + current rate for a new form. */
    suspend fun prefill(type: UtilityType, plant: String, date: String): Result<UtilityPrefill>
}
