package com.candorfoods.factoryops.domain.model.utilities

import com.candorfoods.factoryops.util.canonicalPlant

/**
 * Fields a plant has no meter for, so they can't be required there.
 *
 * W-202's DG set has no kWh meter: with Initial/Final kWh required (as they are at
 * A-185) a W-202 technician could never submit a diesel log at all. Left blank, the
 * kWh readings and the `total_consumption` they feed are simply sent as null — the
 * backend's diesel columns and DTO are all Optional, and its closing-≥-opening check
 * only fires when both values are present, so nothing server-side needs to change.
 *
 * Keyed by canonical plant (the compact form canonicalPlant returns), so any
 * spelling of `mt_users.location` matches.
 */
private val OPTIONAL_KEYS_BY_PLANT: Map<String, Map<UtilityType, Set<String>>> = mapOf(
    "W202" to mapOf(
        UtilityType.DIESEL to setOf("initial_kwh_reading", "final_kwh_reading"),
    ),
)

/** Keys this utility doesn't require at [plant] (empty for every plant/utility with full metering). */
fun UtilityType.optionalKeysAt(plant: String): Set<String> =
    OPTIONAL_KEYS_BY_PLANT[canonicalPlant(plant)]?.get(this).orEmpty()

/** Whether [field] must be filled at [plant] — drives both the Submit gate and the "(optional)" label. */
fun UtilityType.isRequiredAt(field: UtilityField, plant: String): Boolean =
    field.required && field.key !in optionalKeysAt(plant)

/**
 * Submit gate: every field required at [plant] is present and parseable, and each
 * meter pair holds up. A pair may be left entirely blank only when the plant doesn't
 * meter it; a half-filled pair is always rejected, since one reading alone can't
 * produce a consumption figure.
 */
fun canSubmitUtility(type: UtilityType, plant: String, numbers: Map<String, Double?>): Boolean {
    val optional = type.optionalKeysAt(plant)
    val requiredOk = type.inputs
        .filter { it.kind == FieldKind.NUMBER && it.required && it.key !in optional }
        .all { numbers[it.key] != null }
    val pairsOk = type.pairs.all { (closing, opening) ->
        val c = numbers[closing]
        val o = numbers[opening]
        when {
            c != null && o != null -> c >= o
            c == null && o == null -> closing in optional && opening in optional
            else -> false
        }
    }
    return requiredOk && pairsOk
}
