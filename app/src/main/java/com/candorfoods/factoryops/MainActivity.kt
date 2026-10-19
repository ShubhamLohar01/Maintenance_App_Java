package com.candorfoods.factoryops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import com.candorfoods.factoryops.data.local.LanguagePreferences
import com.candorfoods.factoryops.navigation.AppNavGraph
import com.candorfoods.factoryops.presentation.common.ProvideLocalizedContext
import com.candorfoods.factoryops.presentation.common.theme.FactoryOpsTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var languagePreferences: LanguagePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val language by languagePreferences.currentLanguage.collectAsStateWithLifecycle()
            ProvideLocalizedContext(language) {
                FactoryOpsTheme {
                    // enableEdgeToEdge() sets decorFitsSystemWindows=false, so the manifest's
                    // adjustResize no longer shrinks the window for the soft keyboard. Consume the
                    // IME inset here instead: the whole nav content lifts above the keyboard, and
                    // any scrollable form auto-scrolls the focused field into the smaller viewport.
                    Box(Modifier.fillMaxSize().imePadding()) {
                        AppNavGraph()
                    }
                }
            }
        }
    }
}
