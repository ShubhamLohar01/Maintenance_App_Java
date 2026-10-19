package com.candorfoods.factoryops.domain.model.utilities

/** A form field is either a typed number (parsed, rounded, computed on) or free text. */
enum class FieldKind { NUMBER, TEXT }

/**
 * One field of a utility form. [decimals] is the rounding used for BOTH on-screen
 * display and the value sent to the backend (money → 2, quantities → 3). Ignored
 * for [FieldKind.TEXT]. [required] gates Submit; text fields and production_units
 * are optional (blank production → null cost_per_unit).
 */
data class UtilityField(
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.NUMBER,
    val default: String = "",
    val decimals: Int = 3,
    val required: Boolean = true,
)

/**
 * The four daily-utility consumption forms. Each declares its editable [inputs], its
 * read-only [computed] fields (filled live by [computeUtility]), the meter
 * [pairs] that must satisfy closing ≥ opening, and the [headline] computed keys
 * surfaced on the history list. Pure Kotlin — no Android deps, so it unit-tests.
 */
enum class UtilityType(
    val slug: String,
    val label: String,
    val inputs: List<UtilityField>,
    val computed: List<UtilityField>,
    /** (closingKey to openingKey) — Submit requires closing ≥ opening for each. */
    val pairs: List<Pair<String, String>>,
    val headline: List<String>,
    /** The input key holding this utility's price — supervisor-managed, shown
     * read-only in the reading form and filled from the backend. */
    val rateKey: String,
) {
    DIESEL(
        slug = "diesel",
        label = "Diesel",
        inputs = listOf(
            UtilityField("initial_kwh_reading", "Initial kWh reading"),
            UtilityField("final_kwh_reading", "Final kWh reading"),
            UtilityField("start_dg_run_hour", "Start DG run hour"),
            UtilityField("stop_dg_run_hour", "Stop DG run hour"),
            UtilityField("diesel_l_per_hour", "Diesel L/hour", default = "37.5"),
            UtilityField("diesel_rate", "Diesel rate (₹/L)", default = "95", decimals = 2),
            UtilityField("diesel_received_l", "Diesel received (L)", required = false),
            UtilityField("remark", "Remark", kind = FieldKind.TEXT, required = false),
        ),
        computed = listOf(
            UtilityField("total_consumption", "Total consumption"),
            UtilityField("total_run_hour", "Total run hour"),
            UtilityField("total_diesel_l", "Total diesel (L)"),
            UtilityField("total_fuel_cost", "Total fuel cost (₹)", decimals = 2),
        ),
        pairs = listOf(
            "final_kwh_reading" to "initial_kwh_reading",
            "stop_dg_run_hour" to "start_dg_run_hour",
        ),
        headline = listOf("total_fuel_cost", "total_diesel_l"),
        rateKey = "diesel_rate",
    ),

    GAS(
        slug = "gas",
        label = "Gas",
        inputs = listOf(
            UtilityField("gas_meter_opening", "Gas meter opening"),
            UtilityField("gas_meter_closing", "Gas meter closing"),
            UtilityField("gas_conversion_factor", "Conversion factor", default = "1.44"),
            UtilityField("gas_rate", "Gas rate (₹/m³)", decimals = 2),
            UtilityField("production_units", "Production units", required = false),
            UtilityField("remark", "Remark", kind = FieldKind.TEXT, required = false),
        ),
        computed = listOf(
            UtilityField("gas_consumed_m3", "Gas consumed (m³)"),
            UtilityField("daily_gas_cost", "Daily gas cost (₹)", decimals = 2),
            UtilityField("cost_per_unit", "Cost / unit (₹)", decimals = 2),
        ),
        pairs = listOf("gas_meter_closing" to "gas_meter_opening"),
        headline = listOf("daily_gas_cost", "cost_per_unit"),
        rateKey = "gas_rate",
    ),

    ELECTRICITY(
        slug = "electricity",
        label = "Electricity",
        inputs = listOf(
            UtilityField("department", "Department", kind = FieldKind.TEXT, required = false),
            UtilityField("energy_meter_opening_kwh", "Opening kWh"),
            UtilityField("energy_meter_closing_kwh", "Closing kWh"),
            UtilityField("energy_meter_opening_kvah", "Opening kVAh"),
            UtilityField("energy_meter_closing_kvah", "Closing kVAh"),
            UtilityField("ct_multiplier", "CT multiplier", default = "4"),
            UtilityField("electricity_rate", "Rate (₹/kWh)", decimals = 2),
            UtilityField("production_units", "Production units", required = false),
            UtilityField("remark", "Remark", kind = FieldKind.TEXT, required = false),
        ),
        computed = listOf(
            UtilityField("electricity_consumed_kwh", "Consumed kWh"),
            UtilityField("electricity_consumed_kvah", "Consumed kVAh"),
            UtilityField("daily_electricity_cost", "Daily cost (₹)", decimals = 2),
            UtilityField("cost_per_unit", "Cost / unit (₹)", decimals = 2),
        ),
        pairs = listOf(
            "energy_meter_closing_kwh" to "energy_meter_opening_kwh",
            "energy_meter_closing_kvah" to "energy_meter_opening_kvah",
        ),
        headline = listOf("daily_electricity_cost", "cost_per_unit"),
        rateKey = "electricity_rate",
    ),

    WATER(
        slug = "water",
        label = "Water",
        inputs = listOf(
            UtilityField("water_meter_opening", "Water meter opening"),
            UtilityField("water_meter_closing", "Water meter closing"),
            UtilityField("water_rate", "Water rate (₹/m³)", decimals = 2),
            UtilityField("production_units", "Production units", required = false),
            UtilityField("remark", "Remark", kind = FieldKind.TEXT, required = false),
        ),
        computed = listOf(
            UtilityField("water_consumed", "Water consumed"),
            UtilityField("daily_water_cost", "Daily water cost (₹)", decimals = 2),
            UtilityField("cost_per_unit", "Cost / unit (₹)", decimals = 2),
        ),
        pairs = listOf("water_meter_closing" to "water_meter_opening"),
        headline = listOf("daily_water_cost", "cost_per_unit"),
        rateKey = "water_rate",
    );

    /** All numeric fields (inputs + computed), keyed — used for rounding on send. */
    fun decimalsFor(key: String): Int =
        (inputs + computed).firstOrNull { it.key == key }?.decimals ?: 3

    fun labelFor(key: String): String =
        (inputs + computed).firstOrNull { it.key == key }?.label ?: key

    companion object {
        fun fromSlug(slug: String?): UtilityType? = entries.firstOrNull { it.slug == slug }
    }
}
