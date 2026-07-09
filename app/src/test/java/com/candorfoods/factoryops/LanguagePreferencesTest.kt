package com.candorfoods.factoryops

import android.content.Context
import android.content.SharedPreferences
import com.candorfoods.factoryops.data.local.LanguagePreferences
import com.candorfoods.factoryops.domain.model.AppLanguage
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LanguagePreferencesTest {

    private val store = mutableMapOf<String, String?>()
    private lateinit var prefs: LanguagePreferences

    @BeforeEach
    fun setUp() {
        store.clear()
        val editor = mockk<SharedPreferences.Editor>()
        val sharedPrefs = mockk<SharedPreferences>()
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.getString(any(), any()) } answers { store[firstArg<String>()] ?: secondArg() }
        every { sharedPrefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers { store[firstArg<String>()] = secondArg<String>(); editor }
        every { editor.apply() } just Runs
        prefs = LanguagePreferences(context)
    }

    @Test fun `defaults to English before any load`() {
        assertEquals(AppLanguage.ENGLISH, prefs.currentLanguage.value)
    }

    @Test fun `setLanguageForUser persists and publishes`() {
        prefs.setLanguageForUser("u1", AppLanguage.HINDI)
        assertEquals(AppLanguage.HINDI, prefs.currentLanguage.value)
        assertEquals("hi", store["lang_u1"])
    }

    @Test fun `loadForUser publishes stored choice`() {
        store["lang_u2"] = "mr"
        prefs.loadForUser("u2")
        assertEquals(AppLanguage.MARATHI, prefs.currentLanguage.value)
    }

    @Test fun `loadForUser defaults to English when nothing stored`() {
        prefs.loadForUser("nobody")
        assertEquals(AppLanguage.ENGLISH, prefs.currentLanguage.value)
    }

    @Test fun `resetToDefault reverts live flow but keeps stored value`() {
        prefs.setLanguageForUser("u1", AppLanguage.HINDI)
        prefs.resetToDefault()
        assertEquals(AppLanguage.ENGLISH, prefs.currentLanguage.value)
        assertEquals("hi", store["lang_u1"])            // retained on disk
        prefs.loadForUser("u1")
        assertEquals(AppLanguage.HINDI, prefs.currentLanguage.value)  // restored on next login
    }

    @Test fun `languageForUser reads without touching live flow`() {
        store["lang_u3"] = "hi"
        assertEquals(AppLanguage.HINDI, prefs.languageForUser("u3"))
        assertEquals(AppLanguage.ENGLISH, prefs.currentLanguage.value)  // flow unchanged
    }
}
