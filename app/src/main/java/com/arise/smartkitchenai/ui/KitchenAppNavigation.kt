package com.arise.smartkitchenai.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arise.smartkitchenai.ui.expiry.ExpiryScreen
import com.arise.smartkitchenai.ui.inventory.InventoryScreen
import com.arise.smartkitchenai.ui.waste.WasteScreen

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    INVENTORY("inventory", "Inventory", Icons.AutoMirrored.Filled.List),
    WASTE("waste", "Waste", Icons.Filled.Delete),
    EXPIRY("expiry", "Expiry", Icons.Filled.DateRange),
}

@Composable
fun KitchenAppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.INVENTORY.route,
            // Consume insets so each screen's own Scaffold doesn't pad for the bars twice.
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable(Destination.INVENTORY.route) { InventoryScreen() }
            composable(Destination.WASTE.route) { WasteScreen() }
            composable(Destination.EXPIRY.route) { ExpiryScreen() }
        }
    }
}
