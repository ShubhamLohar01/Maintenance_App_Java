package com.candorfoods.factoryops.domain.model

/**
 * One row of the asset register (Postgres `mt_asset_list`, served by
 * GET /mt-machines). `category` is the backend's free-text classification
 * (e.g. "Packaging", "Electric Asset"); `section` is the Production/Others
 * grouping derived from it for the Machine Master screen.
 */
data class MtMachine(
    val assetId: String,            // asset_id, e.g. "W202-0005" / "A185-0001"
    val building: String,           // "W202" | "A185"
    val assetName: String,          // asset_name
    val category: String,           // backend category (free text)
    val subLocation: String,        // sub_location, was floor
    val ratedKw: Double?,           // backend rated_kw, falls back to parsed power_load
    val powerLoadRaw: String?,      // raw power_load text, e.g. "120Watt"
    val section: MachineCategory,   // Production/Others grouping derived from category
    val quantity: Int? = null,
    val condition: String? = null,
    val assignedTo: String? = null,
    val modelNo: String? = null,    // model_no
    val serialNo: String? = null,   // serial_no
    val remarks: String? = null,    // remarks
    // True when a supervisor's active schedule owns this machine (backend schedule_locked).
    // Such a machine records automatically, so the operator's production Start/Stop is off.
    val scheduleLocked: Boolean = false,
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

/**
 * Categories an OPERATOR has no use for — hidden from their asset list (they can't
 * start production on them and don't maintain them). Exact backend category strings,
 * matched case-insensitively.
 */
val OPERATOR_HIDDEN_CATEGORIES: Set<String> = setOf(
    "Electric Asset",
    "Lab Equipment",
    "Utility/Infrastructure",
    "Housekeeping",
    "Furniture/Storage",
    "Infrastructure",
    "IT/Communication",
).map { it.lowercase() }.toSet()

/** True when this asset should NOT be shown to an operator (non-production support assets). */
fun MtMachine.isHiddenFromOperator(): Boolean =
    category.trim().lowercase() in OPERATOR_HIDDEN_CATEGORIES

/** Canonical value written to [MtMachine.condition] to mark a machine as shut down. */
const val CONDITION_SHUT_DOWN = "Shut Down"

/**
 * True when this asset has been shut down (soft-decommissioned). Trim + case-insensitive
 * so pre-existing/backend variants still match. Single source of truth for every guard.
 */
fun MtMachine.isShutDown(): Boolean =
    condition?.trim().equals(CONDITION_SHUT_DOWN, ignoreCase = true) == true
