package com.candorfoods.factoryops.util

/**
 * Name fragments that flag a machine as plant infrastructure (not operator-run).
 * Match is case-insensitive `contains`. Used by HomeViewModel to hide these from
 * the operator's "Assigned Machines" list — they live under "Others" instead.
 */
object OtherMachineKeywords {
    val list: List<String> = listOf(
        // HVAC / cooling
        "ac", "split ac", "indoor unit", "vrv", "fresh air", "cooling device",
        // Air movement
        "fan", "exhaust", "exhuast", "pedstal", "pedestal",
        // Lighting
        "tube light", "tubes light", "led", "panel light", "flood light",
        // Pest control
        "fly catcher",
        // Cold storage
        "baking oven", "blast freezer", "deep freezer", "freezer",
        // Water / plumbing
        "drain pump", "drian pump", "water heater", "water bath", "ro plant", "hot water tub",
        // Power
        "d.g.", "stabilizer",
    )

    /** True when `name` contains any keyword (case-insensitive). */
    fun matches(name: String): Boolean {
        val n = name.lowercase()
        return list.any { kw -> n.contains(kw) }
    }
}
