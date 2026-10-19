package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.a185.A185Prefill
import com.candorfoods.factoryops.domain.model.a185.A185Reading

/** A-185 HT/Cold/UV daily readings (online-only; backed by A185ReadingsApi). */
interface A185ReadingsRepository {
    /** Save/edit one day (upsert on plant + date). Returns the stored row with computed values. */
    suspend fun submit(reading: A185Reading): Result<A185Reading>

    /** History, newest first. `from`/`to` inclusive (ISO yyyy-MM-dd); null = unbounded. */
    suspend fun list(from: String? = null, to: String? = null): Result<List<A185Reading>>

    /** Opening values + fixed PF for a new day's form. */
    suspend fun prefill(date: String): Result<A185Prefill>

    /**
     * Remove a whole day's readings. HEAD only — the backend returns 403 for any other
     * role, so callers must gate the UI on it too (see A185ReadingHistoryViewModel).
     */
    suspend fun delete(date: String): Result<Unit>
}
