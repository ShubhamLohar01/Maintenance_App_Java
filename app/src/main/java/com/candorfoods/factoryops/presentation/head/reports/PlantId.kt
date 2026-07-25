package com.candorfoods.factoryops.presentation.head.reports

/**
 * Converts a Head Reports filter's UI plant value to the backend's short plant-id form.
 *
 * `PlantFilterChips` (presentation/utilities/UtilityComponents.kt) emits the hyphenated
 * form used by the Reports endpoints ("A-185"/"W-202", null = All). `BreakdownRepository`'s
 * history endpoints expect the short form used everywhere else in the API ("A185"/"W202",
 * "both" = All). Shared here so every Head Reports screen that mixes the two conventions
 * converts the same way.
 */
fun String?.toBackendPlantId(): String = if (this == null) "both" else replace("-", "")
