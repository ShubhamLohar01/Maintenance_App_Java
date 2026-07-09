package com.candorfoods.factoryops.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.candorfoods.factoryops.data.local.entity.RecessStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecessStateDao {
    @Query("SELECT * FROM recess_state WHERE machineId = :machineId LIMIT 1")
    fun observe(machineId: String): Flow<RecessStateEntity?>

    @Query("SELECT * FROM recess_state WHERE machineId = :machineId LIMIT 1")
    suspend fun getOnce(machineId: String): RecessStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: RecessStateEntity)

    @Query("DELETE FROM recess_state WHERE machineId = :machineId")
    suspend fun deleteForMachine(machineId: String)
}
