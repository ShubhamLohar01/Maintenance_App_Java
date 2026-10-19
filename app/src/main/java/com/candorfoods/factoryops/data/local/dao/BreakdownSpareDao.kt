package com.candorfoods.factoryops.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.candorfoods.factoryops.data.local.entity.BreakdownSpareEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BreakdownSpareDao {

    @Query("SELECT * FROM breakdown_spares WHERE flagId = :flagId ORDER BY loggedAt ASC")
    fun observeForFlag(flagId: String): Flow<List<BreakdownSpareEntity>>

    @Query("SELECT * FROM breakdown_spares WHERE flagId = :flagId ORDER BY loggedAt ASC")
    suspend fun getForFlag(flagId: String): List<BreakdownSpareEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(spare: BreakdownSpareEntity)

    @Query("DELETE FROM breakdown_spares WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Called once the server has accepted the repair. The picked list has served
     * its purpose by then — the authoritative copy lives in
     * mt_breakdown_records.spares, and keeping these rows would re-post them if
     * QC reopened the ticket and the technician resolved it again.
     */
    @Query("DELETE FROM breakdown_spares WHERE flagId = :flagId")
    suspend fun deleteForFlag(flagId: String)
}
