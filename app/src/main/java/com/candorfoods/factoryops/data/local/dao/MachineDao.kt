package com.candorfoods.factoryops.data.local.dao

import androidx.room.*
import com.candorfoods.factoryops.data.local.entity.MachineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MachineDao {
    @Query("SELECT * FROM machines ORDER BY name ASC")
    fun getAllMachines(): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines WHERE id = :machineId")
    fun getMachineById(machineId: String): Flow<MachineEntity?>

    @Query("SELECT * FROM machines WHERE id = :machineId")
    suspend fun getMachineByIdOnce(machineId: String): MachineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(machines: List<MachineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(machine: MachineEntity)

    @Query("UPDATE machines SET currentStatus = :status, updatedAt = :updatedAt WHERE id = :machineId")
    suspend fun updateStatus(machineId: String, status: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM machines")
    suspend fun count(): Int
}
