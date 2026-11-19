package com.arise.smartkitchenai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arise.smartkitchenai.ui.KitchenAppNavigation
import com.arise.smartkitchenai.ui.theme.SmartKitchenAITheme

class MainActivity : ComponentActivity() {

    private val roleManager get() = (application as KitchenApp).roleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartKitchenAITheme {
                KitchenAppNavigation()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        roleManager.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        // Rotation also stops the activity; that isn't "leaving the app".
        if (!isChangingConfigurations) roleManager.onAppBackgrounded()
    }
}
