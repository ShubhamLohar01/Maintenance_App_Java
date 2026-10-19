package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.utilities.UtilityType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Every input's [UtilityField.required] must match what the backend actually requires
 * (see backend app/schemas.py UtilityDieselRequest etc. — all Optional except plant/
 * reading_date). A field marked required here that the backend treats as optional
 * permanently blocks Submit on days that field legitimately has nothing to enter.
 */
class UtilityTypeTest {

    @Test fun `diesel received litres is optional (not every day has a delivery)`() {
        val field = UtilityType.DIESEL.inputs.first { it.key == "diesel_received_l" }
        assertFalse(field.required)
    }
}
