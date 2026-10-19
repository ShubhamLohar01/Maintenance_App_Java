package com.candorfoods.factoryops.domain.usecase.utilities

import com.candorfoods.factoryops.domain.model.utilities.UtilityType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Half-up rounding to [decimals] places, used for both display and the value sent.
 * Uses [BigDecimal.valueOf] (canonical decimal string) so decimal half-values round
 * as a human expects — e.g. 1.2345 → 1.235, not the binary-exact 1.234.
 */
fun Double.roundTo(decimals: Int): Double =
    BigDecimal.valueOf(this).setScale(decimals, RoundingMode.HALF_UP).toDouble()

private fun sub(a: Double?, b: Double?): Double? = if (a != null && b != null) a - b else null
private fun mul(a: Double?, b: Double?): Double? = if (a != null && b != null) a * b else null

/** daily cost ÷ production units, but only when units > 0 (else null — never 0/∞). */
private fun costPerUnit(cost: Double?, units: Double?): Double? =
    if (cost != null && units != null && units > 0.0) cost / units else null

/**
 * Pure derivation of a utility's computed fields from its (possibly partial) numeric
 * [inputs]. Any computed value whose operands are missing comes back null; the UI
 * shows those as blank / "—". No rounding here — callers round for display/send.
 */
fun computeUtility(type: UtilityType, inputs: Map<String, Double?>): Map<String, Double?> {
    fun v(key: String) = inputs[key]
    return when (type) {
        UtilityType.DIESEL -> {
            val totalConsumption = sub(v("final_kwh_reading"), v("initial_kwh_reading"))
            val totalRunHour = sub(v("stop_dg_run_hour"), v("start_dg_run_hour"))
            val totalDieselL = mul(v("diesel_l_per_hour"), totalRunHour)
            val totalFuelCost = mul(totalDieselL, v("diesel_rate"))
            mapOf(
                "total_consumption" to totalConsumption,
                "total_run_hour" to totalRunHour,
                "total_diesel_l" to totalDieselL,
                "total_fuel_cost" to totalFuelCost,
            )
        }

        UtilityType.GAS -> {
            val consumed = mul(sub(v("gas_meter_closing"), v("gas_meter_opening")), v("gas_conversion_factor"))
            val dailyCost = mul(consumed, v("gas_rate"))
            mapOf(
                "gas_consumed_m3" to consumed,
                "daily_gas_cost" to dailyCost,
                "cost_per_unit" to costPerUnit(dailyCost, v("production_units")),
            )
        }

        UtilityType.ELECTRICITY -> {
            val consumedKwh = mul(sub(v("energy_meter_closing_kwh"), v("energy_meter_opening_kwh")), v("ct_multiplier"))
            val consumedKvah = sub(v("energy_meter_closing_kvah"), v("energy_meter_opening_kvah"))
            val dailyCost = mul(consumedKwh, v("electricity_rate"))
            mapOf(
                "electricity_consumed_kwh" to consumedKwh,
                "electricity_consumed_kvah" to consumedKvah,
                "daily_electricity_cost" to dailyCost,
                "cost_per_unit" to costPerUnit(dailyCost, v("production_units")),
            )
        }

        UtilityType.WATER -> {
            val consumed = sub(v("water_meter_closing"), v("water_meter_opening"))
            val dailyCost = mul(consumed, v("water_rate"))
            mapOf(
                "water_consumed" to consumed,
                "daily_water_cost" to dailyCost,
                "cost_per_unit" to costPerUnit(dailyCost, v("production_units")),
            )
        }
    }
}
