package com.candorfoods.factoryops.domain.model

/**
 * One row of the asset register (Postgres `mt_asset_list`, served by
 * GET /mt-machines). `category` is the backend's free-text classification
 * (e.g. "Packaging", "Electric Asset"); `section` is the Production/Others
 * grouping derived from it for the Machine Master screen.
 */
data class MtMachine(
    val assetId: String,            // asset_id, e.g. "W202-0005" / "A185-0001"
    val building: String,           // "W-202" | "A-185"
    val assetName: String,          // asset_name
    val category: String,           // backend category (free text)
    val subLocation: String,        // sub_location, was floor
    val ratedKw: Double?,           // backend rated_kw, falls back to parsed power_load
    val powerLoadRaw: String?,      // raw power_load text, e.g. "120Watt"
    val section: MachineCategory,   // Production/Others grouping derived from category
    val quantity: Int? = null,
    val condition: String? = null,
    val assignedTo: String? = null,
)

/**
 * Machine Master section grouping: a row is PRODUCTION only when its category is
 * exactly "Production Equipment"; everything else is OTHERS.
 */
fun deriveMachineSection(category: String): MachineCategory =
    if (category.trim().equals("Production Equipment", ignoreCase = true)) {
        MachineCategory.PRODUCTION
    } else {
        MachineCategory.OTHERS
    }

/** Categories that are NOT real production machinery — no "Start production". */
val NON_RUNNABLE_CATEGORIES: Set<String> = setOf(
    "Electric Asset", "Housekeeping", "Utility", "IT/Communication",
    "Power Distribution", "Infrastructure", "Fire & Safety", "Utility/Infrastructure",
).map { it.lowercase() }.toSet()

/**
 * A production run can be started only on real machinery that has a known power
 * draw: a non-blank power_load AND a category outside the non-machinery blocklist.
 */
fun MtMachine.isRunnable(): Boolean =
    !powerLoadRaw.isNullOrBlank() &&
        category.trim().lowercase() !in NON_RUNNABLE_CATEGORIES
