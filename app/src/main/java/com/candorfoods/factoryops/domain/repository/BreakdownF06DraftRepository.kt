package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.BreakdownF06Draft
import kotlinx.coroutines.flow.Flow

interface BreakdownF06DraftRepository {
    fun observeDraft(breakdownId: String): Flow<BreakdownF06Draft?>
    suspend fun getDraftOnce(breakdownId: String): BreakdownF06Draft?
    suspend fun saveDraft(draft: BreakdownF06Draft)
    /** Ids of breakdowns that already have a saved draft (= "completed"). */
    fun observeSavedIds(): Flow<Set<String>>
}
