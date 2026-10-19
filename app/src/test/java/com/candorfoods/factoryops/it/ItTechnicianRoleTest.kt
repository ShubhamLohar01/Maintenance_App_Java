package com.candorfoods.factoryops.it

import com.candorfoods.factoryops.data.repository.parseUserRole
import com.candorfoods.factoryops.domain.model.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ItTechnicianRoleTest {

    @Test fun `backend it_technician maps to IT_TECHNICIAN`() {
        assertEquals(UserRole.IT_TECHNICIAN, parseUserRole("it_technician"))
        assertEquals(UserRole.IT_TECHNICIAN, parseUserRole("IT_TECHNICIAN"))
    }

    @Test fun `casing and whitespace variants still map`() {
        assertEquals(UserRole.IT_TECHNICIAN, parseUserRole("  It_Technician  "))
        assertEquals(UserRole.IT_TECHNICIAN, parseUserRole("IT TECHNICIAN"))
        assertEquals(UserRole.IT_TECHNICIAN, parseUserRole("IT"))
    }

    @Test fun `every pre-existing role mapping is unchanged`() {
        assertEquals(UserRole.TECHNICIAN, parseUserRole("technician"))
        assertEquals(UserRole.HEAD, parseUserRole("HEAD"))
        assertEquals(UserRole.QC_HEAD, parseUserRole("QC"))
        assertEquals(UserRole.QC_HEAD, parseUserRole("QC_HEAD"))
        assertEquals(UserRole.QC_MEMBER, parseUserRole("QC_MEMBER"))
        assertEquals(UserRole.SUPERVISOR, parseUserRole("SUPERVISOR"))
        assertEquals(UserRole.ADMIN, parseUserRole("ADMIN"))
    }

    @Test fun `unknown roles still fall back to OPERATOR`() {
        assertEquals(UserRole.OPERATOR, parseUserRole("operator"))
        assertEquals(UserRole.OPERATOR, parseUserRole("janitor"))
        assertEquals(UserRole.OPERATOR, parseUserRole(""))
    }
}
