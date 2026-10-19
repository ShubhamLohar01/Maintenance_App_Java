package com.candorfoods.factoryops.presentation.head.reports

/**
 * Converts a Head Reports filter's UI plant value to the backend's short plant-id form.
 *
 * Since 2026-08-29 there is only ONE spelling: `PlantFilterChips` emits "A185"/"W202"
 * (null = All) and every endpoint takes that same compact form, so the hyphen-strip is
 * now a no-op kept for safety — a chip value or saved filter written by an older build
 * still converts correctly. The one thing this still does is map null -> "both", which
 * is what the history endpoints expect for All.
 */
fun String?.toBackendPlantId(): String = if (this == null) "both" else replace("-", "")
