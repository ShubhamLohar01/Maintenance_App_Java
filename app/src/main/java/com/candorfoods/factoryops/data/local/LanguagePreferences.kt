package com.candorfoods.factoryops.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import com.candorfoods.factoryops.domain.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the selected UI language **per user** (key `lang_<userId>`) in plain
 * SharedPreferences (the choice is not secret) and exposes the live value as a
 * [StateFlow] the app root observes. Follows the [OthersTagPreferences] pattern.
 *
 * Lifecycle: [loadForUser] on login, [resetToDefault] on logout, [setLanguageForUser]
 * from Settings. [languageForUser] is a side-effect-free read for background workers.
 */
@Singleton
class LanguagePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _current = MutableStateFlow(AppLanguage.DEFAULT)
    val currentLanguage: StateFlow<AppLanguage> = _current.asStateFlow()

    fun loadForUser(userId: String) { _current.value = languageForUser(userId) }

    fun setLanguageForUser(userId: String, lang: AppLanguage) {
        prefs.edit().putString(key(userId), lang.tag).apply()
        _current.value = lang
    }

    fun resetToDefault() { _current.value = AppLanguage.DEFAULT }

    fun languageForUser(userId: String): AppLanguage =
        AppLanguage.fromTag(prefs.getString(key(userId), null))

    private fun key(userId: String) = "$KEY_PREFIX$userId"

    companion object {
        private const val PREFS_NAME = "language_prefs"
        private const val KEY_PREFIX = "lang_"
    }
}
