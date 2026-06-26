package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.entity.BreakdownF06DraftEntity
import com.candorfoods.factoryops.domain.model.BreakdownF06Draft
import com.candorfoods.factoryops.domain.repository.BreakdownF06DraftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreakdownF06DraftRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
) : BreakdownF06DraftRepository {

    private val dao get() = db.breakdownF06DraftDao()

    override fun observeDraft(breakdownId: String): Flow<BreakdownF06Draft?> =
        dao.observeDraft(breakdownId).map { it?.toDomain() }

    override suspend fun getDraftOnce(breakdownId: String): BreakdownF06Draft? =
        dao.getDraftOnce(breakdownId)?.toDomain()

    override suspend fun saveDraft(draft: BreakdownF06Draft) = dao.upsert(draft.toEntity())

    override fun observeSavedIds(): Flow<Set<String>> =
        dao.observeSavedIds().map { it.toSet() }
}

private fun BreakdownF06DraftEntity.toDomain() = BreakdownF06Draft(
    breakdownId = breakdownId, location = location, equipmentModel = equipmentModel,
    typeOfMaintenance = typeOfMaintenance, partOfMachine = partOfMachine, reason = reason,
    durationStart = durationStart, durationEnd = durationEnd,
    machineOperatorSign = machineOperatorSign, maintenancePersonSign = maintenancePersonSign,
    qcClearanceSign = qcClearanceSign, verifiedBy = verifiedBy, updatedAt = updatedAt,
)

private fun BreakdownF06Draft.toEntity() = BreakdownF06DraftEntity(
    breakdownId = breakdownId, location = location, equipmentModel = equipmentModel,
    typeOfMaintenance = typeOfMaintenance, partOfMachine = partOfMachine, reason = reason,
    durationStart = durationStart, durationEnd = durationEnd,
    machineOperatorSign = machineOperatorSign, maintenancePersonSign = maintenancePersonSign,
    qcClearanceSign = qcClearanceSign, verifiedBy = verifiedBy, updatedAt = updatedAt,
)
