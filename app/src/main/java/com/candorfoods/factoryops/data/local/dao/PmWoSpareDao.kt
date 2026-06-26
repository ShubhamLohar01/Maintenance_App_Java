package com.candorfoods.factoryops.data.local.dao

import androidx.room.*
import com.candorfoods.factoryops.data.local.entity.PmWoSpareEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PmWoSpareDao {

    @Query("SELECT * FROM pm_wo_spares WHERE woId = :woId ORDER BY loggedAt ASC")
    fun observeForWo(woId: String): Flow<List<PmWoSpareEntity>>

    @Query("SELECT * FROM pm_wo_spares WHERE woId = :woId ORDER BY loggedAt ASC")
    suspend fun getForWo(woId: String): List<PmWoSpareEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(spare: PmWoSpareEntity)

    @Query("DELETE FROM pm_wo_spares WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pm_wo_spares WHERE woId = :woId")
    suspend fun deleteForWo(woId: String)

    /**
     * Autocomplete for spare-name reuse across the whole app history.
     * Ordered by usage frequency descending so common parts surface first.
     * Q3 in 2C-2 — prevents "belt"/"Belt"/"v-belt" duplicates that would haunt Phase 3 inventory migration.
     */
    @Query(
        """
        SELECT spareName
        FROM pm_wo_spares
        WHERE :query = '' OR spareName LIKE '%' || :query || '%' COLLATE NOCASE
        GROUP BY spareName
        ORDER BY COUNT(*) DESC, spareName ASC
        LIMIT :limit
        """
    )
    suspend fun suggestSpareNames(query: String, limit: Int): List<String>
}

