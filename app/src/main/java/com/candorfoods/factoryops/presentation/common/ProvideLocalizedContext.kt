package com.candorfoods.factoryops.presentation.common

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.candorfoods.factoryops.domain.model.AppLanguage
import java.util.Locale

/**
 * Applies [language] to everything composed under [content] so every `stringResource(...)`
 * resolves the selected language and recomposes when it changes — no activity recreate.
 *
 * [LocalContext] is overridden with a [ContextWrapper] (NOT the raw
 * `createConfigurationContext(...)` result) so consumers that walk the context chain to
 * find the host Activity still succeed — notably `hiltViewModel()`. Providing a bare
 * configuration context (a `ContextImpl`) breaks that lookup and crashes with
 * "Expected an activity context for creating a HiltViewModelFactory".
 */
@Composable
fun ProvideLocalizedContext(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val baseContext = LocalContext.current
    val localized = remember(language, baseContext) {
        val config = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale(language.tag))
        }
        val localizedResources = baseContext.createConfigurationContext(config).resources
        LocalizedContextWrapper(baseContext, localizedResources) to config
    }
    CompositionLocalProvider(
        LocalContext provides localized.first,
        LocalConfiguration provides localized.second,
    ) {
        content()
    }
}

/**
 * Wraps the host context (kept reachable via [ContextWrapper.getBaseContext] so the
 * Activity can still be found) while serving locale-adjusted [Resources].
 */
private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources,
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}
