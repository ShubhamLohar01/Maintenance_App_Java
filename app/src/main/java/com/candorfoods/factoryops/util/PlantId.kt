package com.candorfoods.factoryops.util

/**
 * Canonical plant/building code — `"A-185"`, `"a185"`, `" A 185 "` all become `"A185"`.
 *
 * `mt_users.location` is free text managed in pgAdmin and the login response passes it
 * through verbatim (backend `MtUser.plant_id` only trims), so the plantId this app holds
 * is in whatever spelling was typed — `"A185"`, `"A-185"`, `"185-Koparkhairnae"`.
 * Never compare a raw `plantId` against a literal; run both sides through here first.
 * Mirrors the backend's `building_for()`, which returns this same compact form since
 * 2026-08-29 — both sides now agree on ONE spelling, so a value that has been through
 * either function compares equal to one that has been through the other.
 *
 * An unrecognised value is returned trimmed but otherwise untouched, so a new site code
 * still compares equal to itself.
 */
fun canonicalPlant(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    val squashed = trimmed.uppercase().filter { it.isLetterOrDigit() }
    // `contains`, not equality: location is hand-typed, so "A-185 Unit 2" must still
    // resolve to A185. A miss here silently hides A185-only screens, which is the
    // worse failure — showing one to a non-A185 user is caught by the backend's 400.
    //
    // The bare NUMBER counts too. Real rows in mt_users.location read
    // "185-Koparkhairnae" with no leading "A", so matching only "A185" left them
    // unresolved and every plant comparison against them failed. This is the same
    // rule the backend's building_for() and ChecklistCatalog.plantOf() already use;
    // all three now agree on the same input.
    return when {
        squashed.contains("A185") || squashed.contains("185") -> "A185"
        squashed.contains("W202") || squashed.contains("202") -> "W202"
        else -> trimmed
    }
}
