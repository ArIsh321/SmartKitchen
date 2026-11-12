package com.arise.smartkitchenai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arise.smartkitchenai.ui.KitchenAppNavigation
import com.arise.smartkitchenai.ui.theme.SmartKitchenAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartKitchenAITheme {
                KitchenAppNavigation()
            }
        }
    }
}
