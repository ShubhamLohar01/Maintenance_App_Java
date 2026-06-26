package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.entity.PmTemplateEntity
import com.candorfoods.factoryops.data.local.entity.PmTemplateItemEntity
import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.domain.model.PmTemplate
import com.candorfoods.factoryops.domain.model.PmTemplateItem
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.SyncStatus
import com.candorfoods.factoryops.domain.model.TradeType
import com.candorfoods.factoryops.domain.repository.PmTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PmTemplateRepositoryImpl @Inject constructor(
    private val db: AppDatabase
) : PmTemplateRepository {

    private val dao get() = db.pmTemplateDao()

    override fun observeActiveTemplates(): Flow<List<PmTemplate>> =
        dao.observeActiveTemplates().map { templates ->
            templates.map { it.toDomain(emptyList()) }
        }

    override fun observeByMachineType(machineType: MachineType): Flow<List<PmTemplate>> =
        dao.observeByMachineType(machineType.name).map { templates ->
            templates.map { it.toDomain(emptyList()) }
        }

    override fun observeTemplateById(id: String): Flow<PmTemplate?> =
        combine(dao.observeTemplateById(id), dao.observeItems(id)) { template, items ->
            template?.toDomain(items.map { it.toDomain() })
        }

    override suspend fun getTemplateById(id: String): PmTemplate? {
        val template = dao.getTemplateById(id) ?: return null
        val items = dao.getItemsForTemplate(id).map { it.toDomain() }
        return template.toDomain(items)
    }

    override suspend fun saveTemplate(
        template: PmTemplate,
        items: List<PmTemplateItem>
    ): Result<PmTemplate> = try {
        dao.replaceTemplateWithItems(
            template = template.toEntity(),
            items = items.mapIndexed { idx, item ->
                item.copy(orderIndex = idx).toEntity()
            }
        )
        Result.Success(template.copy(items = items))
    } catch (e: Exception) {
        Result.Error(e.message ?: "Save failed", e)
    }

    override suspend fun seedMockIfEmpty(
        seedTemplates: List<Pair<PmTemplate, List<PmTemplateItem>>>
    ) {
        if (dao.count() > 0) return
        seedTemplates.forEach { (template, items) ->
            dao.replaceTemplateWithItems(
                template = template.toEntity(),
                items = items.map { it.toEntity() }
            )
        }
    }

    private fun PmTemplateEntity.toDomain(items: List<PmTemplateItem>) = PmTemplate(
        id = id,
        name = name,
        machineType = runCatching { MachineType.valueOf(machineType) }.getOrDefault(MachineType.OTHER),
        description = description,
        estimatedDurationMinutes = estimatedDurationMinutes,
        defaultTradeType = runCatching { TradeType.valueOf(defaultTradeType) }.getOrDefault(TradeType.MECHANICAL),
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isActive = isActive,
        items = items
    )

    private fun PmTemplate.toEntity() = PmTemplateEntity(
        id = id,
        name = name,
        machineType = machineType.name,
        description = description,
        estimatedDurationMinutes = estimatedDurationMinutes,
        defaultTradeType = defaultTradeType.name,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isActive = isActive,
        syncStatus = SyncStatus.PENDING.name
    )

    private fun PmTemplateItemEntity.toDomain() = PmTemplateItem(
        id = id,
        templateId = templateId,
        orderIndex = orderIndex,
        title = title,
        description = description,
        expectedResult = expectedResult,
        requiresPhoto = requiresPhoto,
        requiresMeasurement = requiresMeasurement,
        measurementUnit = measurementUnit,
        measurementMin = measurementMin,
        measurementMax = measurementMax
    )

    private fun PmTemplateItem.toEntity() = PmTemplateItemEntity(
        id = id,
        templateId = templateId,
        orderIndex = orderIndex,
        title = title,
        description = description,
        expectedResult = expectedResult,
        requiresPhoto = requiresPhoto,
        requiresMeasurement = requiresMeasurement,
        measurementUnit = measurementUnit,
        measurementMin = measurementMin,
        measurementMax = measurementMax,
        syncStatus = SyncStatus.PENDING.name
    )
}
