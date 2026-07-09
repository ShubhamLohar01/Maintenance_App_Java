package com.candorfoods.factoryops.domain.model

/**
 * The languages the app UI can switch between. [tag] is the ISO language tag used
 * both for the Android resource qualifier (values-hi / values-mr) and the [java.util.Locale].
 * [displayName] is always written in its own script so the option is recognizable
 * regardless of the active language.
 */
enum class AppLanguage(val tag: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
    MARATHI("mr", "मराठी");

    companion object {
        val DEFAULT = ENGLISH
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: DEFAULT
    }
}
