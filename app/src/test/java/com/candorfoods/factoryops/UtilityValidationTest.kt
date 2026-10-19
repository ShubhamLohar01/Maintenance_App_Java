package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.utilities.UtilityType
import com.candorfoods.factoryops.domain.model.utilities.canSubmitUtility
import com.candorfoods.factoryops.domain.model.utilities.isRequiredAt
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * W-202's DG set has no kWh meter, so its Initial/Final kWh readings are optional
 * there — without that a W-202 technician can never submit a diesel log. A-185
 * meters both and is unchanged.
 */
class UtilityValidationTest {

    /** A complete diesel entry; override any key to test a gap. */
    private fun diesel(vararg overrides: Pair<String, Double?>): Map<String, Double?> = mapOf(
        "initial_kwh_reading" to 71629.0,
        "final_kwh_reading" to 71815.0,
        "start_dg_run_hour" to 181.5,
        "stop_dg_run_hour" to 184.0,
        "diesel_l_per_hour" to 37.5,
        "diesel_rate" to 95.0,
        "diesel_received_l" to null,
    ) + overrides

    private val noKwh = diesel(
        "initial_kwh_reading" to null,
        "final_kwh_reading" to null,
    )

    @Test fun `A-185 still requires both kWh readings`() {
        assertTrue(canSubmitUtility(UtilityType.DIESEL, "A-185", diesel()))
        assertFalse(canSubmitUtility(UtilityType.DIESEL, "A-185", noKwh))
    }

    @Test fun `W-202 can submit a diesel log with no kWh readings`() {
        assertTrue(canSubmitUtility(UtilityType.DIESEL, "W-202", noKwh))
    }

    @Test fun `W-202 plant id spelling variants are all recognised`() {
        assertTrue(canSubmitUtility(UtilityType.DIESEL, "W202", noKwh))
        assertTrue(canSubmitUtility(UtilityType.DIESEL, " w-202 ", noKwh))
    }

    @Test fun `a half-filled kWh pair is blocked at W-202`() {
        assertFalse(canSubmitUtility(UtilityType.DIESEL, "W-202", diesel("final_kwh_reading" to null)))
        assertFalse(canSubmitUtility(UtilityType.DIESEL, "W-202", diesel("initial_kwh_reading" to null)))
    }

    @Test fun `W-202 still enforces closing at least opening when both kWh are entered`() {
        assertTrue(canSubmitUtility(UtilityType.DIESEL, "W-202", diesel()))
        assertFalse(canSubmitUtility(UtilityType.DIESEL, "W-202", diesel("final_kwh_reading" to 71000.0)))
    }

    @Test fun `DG run hours stay required at W-202`() {
        assertFalse(
            canSubmitUtility(
                UtilityType.DIESEL, "W-202",
                noKwh + mapOf("start_dg_run_hour" to null, "stop_dg_run_hour" to null),
            )
        )
    }

    @Test fun `other utilities are unaffected at W-202`() {
        val water = mapOf(
            "water_meter_opening" to null,
            "water_meter_closing" to null,
            "water_rate" to 25.0,
        )
        assertFalse(canSubmitUtility(UtilityType.WATER, "W-202", water))
        assertTrue(
            canSubmitUtility(
                UtilityType.WATER, "W-202",
                water + mapOf("water_meter_opening" to 10.0, "water_meter_closing" to 12.0),
            )
        )
    }

    @Test fun `the kWh fields render as optional only at W-202`() {
        val initial = UtilityType.DIESEL.inputs.first { it.key == "initial_kwh_reading" }
        val runHour = UtilityType.DIESEL.inputs.first { it.key == "start_dg_run_hour" }
        assertFalse(UtilityType.DIESEL.isRequiredAt(initial, "W-202"))
        assertTrue(UtilityType.DIESEL.isRequiredAt(initial, "A-185"))
        assertTrue(UtilityType.DIESEL.isRequiredAt(runHour, "W-202"))
    }
}
