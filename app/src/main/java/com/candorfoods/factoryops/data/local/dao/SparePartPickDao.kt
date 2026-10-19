package com.candorfoods.factoryops.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.candorfoods.factoryops.data.local.entity.SparePartPickEntity

@Dao
interface SparePartPickDao {

    @Query("SELECT * FROM spare_part_picks")
    suspend fun getAll(): List<SparePartPickEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<SparePartPickEntity>)

    @Query("DELETE FROM spare_part_picks")
    suspend fun clear()

    /**
     * Replace the cached catalogue wholesale. A part that was soft-deleted or moved
     * to the other warehouse must DISAPPEAR from the picker, which an upsert alone
     * would never do — hence clear-then-insert, in one transaction so a crash
     * midway cannot leave the technician with an empty picker.
     */
    @Transaction
    suspend fun replaceAll(rows: List<SparePartPickEntity>) {
        clear()
        insertAll(rows)
    }
}
