package com.candorfoods.factoryops.domain.model.w202

import com.candorfoods.factoryops.domain.model.MtMachine

/**
 * One air-conditioning unit the technician takes a daily meter reading from.
 *
 * AC readings ride the same table and endpoint as the floor readings
 * (mt_floor_utility_readings, POST /floor-readings) — the unit's name goes in the
 * `floor` column under [AC_FLOOR_PREFIX], which keeps AC rows identifiable in SQL and
 * makes a collision with a real floor's name impossible on the table's
 * UNIQUE(building, floor, reading_date).
 */
data class AcReadingPoint(
    val assetId: String,
    val name: String,
    val subLocation: String,
) {
    /** What lands in mt_floor_utility_readings.floor, e.g. "AC · Server Room AC". */
    val floorKey: String get() = acFloorKey(name)
}

/** Marks an mt_floor_utility_readings row as an AC reading rather than a floor's. */
const val AC_FLOOR_PREFIX = "AC · "

fun acFloorKey(name: String): String = AC_FLOOR_PREFIX + name.trim()

/** True for a `floor` value written by the AC screen. */
fun isAcFloorKey(floor: String): Boolean = floor.trimStart().startsWith(AC_FLOOR_PREFIX.trim())

/**
 * The only AC the technician takes a daily reading from: the indoor unit.
 *
 * mt_asset_list has no AC category, so this goes by name. The register also carries
 * "Air Conditioner", "Ductable AC" and "AC Out Door" entries, but those aren't read —
 * listing them only gave the form boxes nobody fills. The whole name must match, so a
 * differently-named unit can't slip in, and "Rack" / "Packaging" / "Track" can't either.
 */
private val AC_INDOOR_NAME = Regex("""^\s*a\s*/?\s*c\s+indoor\s*$""", RegexOption.IGNORE_CASE)

fun isIndoorAcAsset(name: String): Boolean = AC_INDOOR_NAME.matches(name)

/** The building's indoor AC units, in the order the technician reads them (floor, then name). */
fun List<MtMachine>.acReadingPoints(building: String): List<AcReadingPoint> =
    asSequence()
        .filter { it.building.trim().equals(building.trim(), ignoreCase = true) }
        .filter { it.assetName.isNotBlank() && isIndoorAcAsset(it.assetName) }
        .map { AcReadingPoint(assetId = it.assetId, name = it.assetName.trim(), subLocation = it.subLocation) }
        .distinctBy { it.floorKey }
        .sortedWith(compareBy({ it.subLocation.lowercase() }, { it.name.lowercase() }))
        .toList()
