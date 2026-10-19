package com.candorfoods.factoryops.domain.model

/**
 * The asset categories an IT technician owns. [backendValue] is the exact
 * `mt_asset_list.category` string (the same values already listed in
 * MtMachine.kt's NON_RUNNABLE_CATEGORIES) — [label] is what the UI shows.
 *
 * Single source of truth for the IT screens: adding a third IT category is one
 * entry here and both the chooser and the list follow.
 *
 * The enum NAME (not [backendValue]) is what travels as the navigation argument —
 * "IT/Communication" contains a slash that would have to be URL-encoded and could
 * break route matching.
 */
enum class ItAssetCategory(val label: String, val backendValue: String) {
    ELECTRICAL("Electrical Assets", "Electric Asset"),
    IT_COMMUNICATION("IT / Communication", "IT/Communication");

    companion object {
        /** Parse a navigation argument. Unknown/absent falls back to the first category. */
        fun of(name: String?): ItAssetCategory =
            entries.firstOrNull { it.name == name?.trim() } ?: ELECTRICAL
    }
}

/** True when [machine] belongs to this IT category. Trim + case-insensitive, so a
 *  casing variant already sitting in the register still shows up. */
fun ItAssetCategory.matches(machine: MtMachine): Boolean =
    machine.category.trim().equals(backendValue, ignoreCase = true)
