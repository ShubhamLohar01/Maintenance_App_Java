package com.candorfoods.factoryops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                    AppNavGraph()
                }
            }
        }
    }
}
