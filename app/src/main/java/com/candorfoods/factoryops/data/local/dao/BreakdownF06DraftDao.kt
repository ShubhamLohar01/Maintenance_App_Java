package com.candorfoods.factoryops.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.candorfoods.factoryops.data.local.entity.BreakdownF06DraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BreakdownF06DraftDao {
    @Upsert
    suspend fun upsert(draft: BreakdownF06DraftEntity)

    @Query("SELECT * FROM breakdown_f06_drafts WHERE breakdownId = :id")
    fun observeDraft(id: String): Flow<BreakdownF06DraftEntity?>

    @Query("SELECT * FROM breakdown_f06_drafts WHERE breakdownId = :id")
    suspend fun getDraftOnce(id: String): BreakdownF06DraftEntity?

    @Query("SELECT breakdownId FROM breakdown_f06_drafts")
    fun observeSavedIds(): Flow<List<String>>
}
