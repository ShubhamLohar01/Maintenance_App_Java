package com.candorfoods.factoryops.presentation.common

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.candorfoods.factoryops.domain.model.AppLanguage
import java.util.Locale

/**
 * Overrides [LocalContext] and [LocalConfiguration] with a locale-set context so every
 * `stringResource(...)` below resolves [language] and recomposes when it changes.
 * This is the whole language-switch mechanism on the Compose side — no activity recreate.
 */
@Composable
fun ProvideLocalizedContext(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val localized = remember(language, ctx) {
        val config = Configuration(ctx.resources.configuration).apply {
            setLocale(Locale(language.tag))
        }
        ctx.createConfigurationContext(config) to config
    }
    CompositionLocalProvider(
        LocalContext provides localized.first,
        LocalConfiguration provides localized.second,
    ) {
        content()
    }
}
