package com.candorfoods.factoryops.domain.model.utilities

/**
 * One stored (or about-to-be-stored) daily utility reading, held generically so the
 * same type drives all four utilities. [numbers] carries every numeric field
 * (inputs + computed) keyed by its snake_case name (null = absent / not applicable,
 * e.g. cost_per_unit with zero production); [texts] carries remark / department.
 */
data class UtilityRecord(
    val plant: String,
    val readingDate: String,          // "YYYY-MM-DD"
    val numbers: Map<String, Double?> = emptyMap(),
    val texts: Map<String, String> = emptyMap(),
    val id: Int? = null,
)
