package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.w202.W202MeterKind
import com.candorfoods.factoryops.domain.model.w202.W202MeterReading
import com.candorfoods.factoryops.domain.model.w202.W202Prefill

/** W-202 Main Meter / Solar daily readings (online-only; backed by W202ReadingsApi). */
interface W202ReadingsRepository {
    /** Save/edit one day for one meter (upsert on plant + date + kind). Returns the stored row. */
    suspend fun submit(reading: W202MeterReading): Result<W202MeterReading>

    /** History for one meter, newest first. `from`/`to` inclusive (ISO yyyy-MM-dd); null = unbounded. */
    suspend fun list(kind: W202MeterKind, from: String? = null, to: String? = null): Result<List<W202MeterReading>>

    /** Opening values for a new day's form — carried forward for MAIN, blank for SOLAR. */
    suspend fun prefill(kind: W202MeterKind, date: String): Result<W202Prefill>

    /**
     * Remove a whole day's reading for one meter. HEAD only — the backend returns 403 for any
     * other role, so callers must gate the UI on it too (see W202ReadingHistoryViewModel).
     */
    suspend fun delete(kind: W202MeterKind, date: String): Result<Unit>
}
