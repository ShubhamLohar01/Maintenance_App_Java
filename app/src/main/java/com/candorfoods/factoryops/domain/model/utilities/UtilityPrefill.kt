package com.candorfoods.factoryops.domain.model.utilities

/**
 * Form pre-fill for a new reading. [openings] maps each opening meter field
 * (e.g. "water_meter_opening") to the previous reading's closing value (null when
 * there is no earlier row). [rate] is the current supervisor price for the
 * utility; [sourceDate] is the date the openings were copied from. Mirrors
 * GET /utilities/{util}/prefill.
 */
data class UtilityPrefill(
    val openings: Map<String, Double?>,
    val rate: Double?,
    val sourceDate: String?,
)
