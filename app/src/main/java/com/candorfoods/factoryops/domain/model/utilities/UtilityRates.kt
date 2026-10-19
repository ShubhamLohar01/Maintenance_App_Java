package com.candorfoods.factoryops.domain.model.utilities

/**
 * Supervisor-set current prices for one plant. [rates] is keyed by the backend
 * rate field ("diesel_rate", "gas_rate", "water_rate", "electricity_rate"); a
 * value is null when that rate has never been set. Mirrors GET/PUT /utilities/rates.
 */
data class UtilityRates(
    val plant: String,
    val rates: Map<String, Double?>,
    val setBy: String? = null,
    val setAt: String? = null,
)

/** The rate fields shown on the supervisor "Utility Rates" screen, in display order. */
val UTILITY_RATE_KEYS = listOf("diesel_rate", "gas_rate", "water_rate", "electricity_rate")

/** Human labels for [UTILITY_RATE_KEYS]. */
val UTILITY_RATE_LABELS = mapOf(
    "diesel_rate" to "Diesel rate (₹/L)",
    "gas_rate" to "Gas rate (₹/m³)",
    "water_rate" to "Water rate (₹/m³)",
    "electricity_rate" to "Electricity rate (₹/kWh)",
)
