package com.candorfoods.factoryops.domain.model

/**
 * Adapters that present an asset-register row (or a bare snapshot) as the legacy
 * [Machine] shape the PM detail screens consume. Those screens only surface
 * name / code / location, so the remaining fields are harmless placeholders.
 *
 * PM binds to `mt_asset_list.asset_id`, but the detail screens predate that; these
 * adapters bridge the two without rewriting every screen.
 */
fun MtMachine.toMachine(): Machine = Machine(
    id = assetId,
    code = assetId,
    name = assetName,
    location = subLocation,
    building = building,
    plantId = "",
    machineType = MachineType.OTHER,
    category = category,
    ratedKw = ratedKw ?: 0.0,
    loadFactor = 0.7,
    loadFactorSource = LoadFactorSource.ASSUMED,
    criticality = Criticality.C,
    expectedRunHours = 0.0,
    currentStatus = MachineStatus.IDLE,
    updatedAt = 0L,
)

/** Fallback when the asset can't be resolved (e.g. legacy/seeded work orders):
 *  show the snapshot name so the screen still renders. */
fun placeholderMachine(id: String, name: String): Machine = Machine(
    id = id,
    code = id,
    name = name,
    location = "",
    building = "",
    plantId = "",
    machineType = MachineType.OTHER,
    category = null,
    ratedKw = 0.0,
    loadFactor = 0.7,
    loadFactorSource = LoadFactorSource.ASSUMED,
    criticality = Criticality.C,
    expectedRunHours = 0.0,
    currentStatus = MachineStatus.IDLE,
    updatedAt = 0L,
)
