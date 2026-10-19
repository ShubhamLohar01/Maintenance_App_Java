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
}

