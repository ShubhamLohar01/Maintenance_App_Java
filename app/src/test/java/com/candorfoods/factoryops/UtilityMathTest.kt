package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.utilities.UtilityType
import com.candorfoods.factoryops.domain.usecase.utilities.computeUtility
import com.candorfoods.factoryops.domain.usecase.utilities.roundTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UtilityMathTest {

    @Test fun `diesel derives run hours, litres and cost`() {
        val out = computeUtility(
            UtilityType.DIESEL,
            mapOf(
                "initial_kwh_reading" to 100.0, "final_kwh_reading" to 150.0,
                "start_dg_run_hour" to 10.0, "stop_dg_run_hour" to 12.0,
                "diesel_l_per_hour" to 37.5, "diesel_rate" to 95.0,
            ),
        )
        assertEquals(50.0, out["total_consumption"])
        assertEquals(2.0, out["total_run_hour"])
        assertEquals(75.0, out["total_diesel_l"])          // 37.5 * 2
        assertEquals(7125.0, out["total_fuel_cost"])       // 75 * 95
    }

    @Test fun `gas applies conversion factor and cost per unit`() {
        val out = computeUtility(
            UtilityType.GAS,
            mapOf(
                "gas_meter_opening" to 10.0, "gas_meter_closing" to 20.0,
                "gas_conversion_factor" to 1.44, "gas_rate" to 50.0,
                "production_units" to 100.0,
            ),
        )
        assertEquals(14.4, out["gas_consumed_m3"]!!, 1e-9)  // (20-10)*1.44
        assertEquals(720.0, out["daily_gas_cost"]!!, 1e-9)  // 14.4*50
        assertEquals(7.2, out["cost_per_unit"]!!, 1e-9)     // 720/100
    }

    @Test fun `zero production yields null cost per unit`() {
        val out = computeUtility(
            UtilityType.WATER,
            mapOf(
                "water_meter_opening" to 0.0, "water_meter_closing" to 5.0,
                "water_rate" to 30.0, "production_units" to 0.0,
            ),
        )
        assertEquals(5.0, out["water_consumed"])
        assertEquals(150.0, out["daily_water_cost"])
        assertNull(out["cost_per_unit"])                    // units == 0 -> null, not Infinity
    }

    @Test fun `missing production yields null cost per unit`() {
        val out = computeUtility(
            UtilityType.WATER,
            mapOf("water_meter_opening" to 0.0, "water_meter_closing" to 5.0, "water_rate" to 30.0),
        )
        assertNull(out["cost_per_unit"])
    }

    @Test fun `electricity applies CT multiplier on kWh only`() {
        val out = computeUtility(
            UtilityType.ELECTRICITY,
            mapOf(
                "energy_meter_opening_kwh" to 100.0, "energy_meter_closing_kwh" to 110.0,
                "energy_meter_opening_kvah" to 200.0, "energy_meter_closing_kvah" to 215.0,
                "ct_multiplier" to 4.0, "electricity_rate" to 8.0, "production_units" to 40.0,
            ),
        )
        assertEquals(40.0, out["electricity_consumed_kwh"])   // (110-100)*4
        assertEquals(15.0, out["electricity_consumed_kvah"])  // 215-200 (no CT)
        assertEquals(320.0, out["daily_electricity_cost"])    // 40*8
        assertEquals(8.0, out["cost_per_unit"])               // 320/40
    }

    @Test fun `partial inputs leave dependent fields null`() {
        val out = computeUtility(UtilityType.DIESEL, mapOf("final_kwh_reading" to 150.0))
        assertNull(out["total_consumption"])   // initial missing
        assertNull(out["total_fuel_cost"])
    }

    @Test fun `roundTo uses money 2dp and quantity 3dp half-up`() {
        assertEquals(7.13, 7.125.roundTo(2))
        assertEquals(1.235, 1.2345.roundTo(3))
    }
}
