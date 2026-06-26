package com.candorfoods.factoryops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.candorfoods.factoryops.navigation.AppNavGraph
import com.candorfoods.factoryops.presentation.common.theme.FactoryOpsTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FactoryOpsTheme {
                AppNavGraph()
            }
        }
    }
}
