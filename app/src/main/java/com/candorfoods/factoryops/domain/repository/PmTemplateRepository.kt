package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.domain.model.PmTemplate
import com.candorfoods.factoryops.domain.model.PmTemplateItem
import com.candorfoods.factoryops.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface PmTemplateRepository {

    fun observeActiveTemplates(): Flow<List<PmTemplate>>
    fun observeByMachineType(machineType: MachineType): Flow<List<PmTemplate>>
    fun observeTemplateById(id: String): Flow<PmTemplate?>
    suspend fun getTemplateById(id: String): PmTemplate?

    /**
     * Creates or updates a template, replacing all of its items in one transaction.
     * Used by the supervisor template editor.
     */
    suspend fun saveTemplate(
        template: PmTemplate,
        items: List<PmTemplateItem>
    ): Result<PmTemplate>

    suspend fun seedMockIfEmpty(seedTemplates: List<Pair<PmTemplate, List<PmTemplateItem>>>)
}
