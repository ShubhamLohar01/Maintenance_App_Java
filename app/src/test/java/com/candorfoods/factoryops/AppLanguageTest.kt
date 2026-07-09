package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.AppLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppLanguageTest {

    @Test fun `fromTag maps known tags`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.HINDI, AppLanguage.fromTag("hi"))
        assertEquals(AppLanguage.MARATHI, AppLanguage.fromTag("mr"))
    }

    @Test fun `fromTag falls back to default for null or unknown`() {
        assertEquals(AppLanguage.DEFAULT, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.DEFAULT, AppLanguage.fromTag("fr"))
    }

    @Test fun `default is English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.DEFAULT)
    }
}
