package com.arise.smartkitchenai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arise.smartkitchenai.KitchenApp
import com.arise.smartkitchenai.auth.Role
import com.arise.smartkitchenai.auth.RoleManager
import com.arise.smartkitchenai.auth.Session
import com.arise.smartkitchenai.ui.expiry.ExpiryScreen
import com.arise.smartkitchenai.ui.insights.InsightsScreen
import com.arise.smartkitchenai.ui.inventory.InventoryScreen
import com.arise.smartkitchenai.ui.login.FirstTimeSetupScreen
import com.arise.smartkitchenai.ui.login.LoginScreen
import com.arise.smartkitchenai.ui.manager.ManagerSalesScreen
import com.arise.smartkitchenai.ui.manager.PurchasingScreen
import com.arise.smartkitchenai.ui.owner.OverviewScreen
import com.arise.smartkitchenai.ui.owner.ProfileScreen
import com.arise.smartkitchenai.ui.owner.ReportsScreen
import com.arise.smartkitchenai.ui.staff.QuickEntryScreen
import com.arise.smartkitchenai.ui.waste.WasteScreen

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    // Owner
    OVERVIEW("overview", "Overview", Icons.Filled.Home),
    REPORTS("reports", "Reports", Icons.AutoMirrored.Filled.List),
    INSIGHTS("insights", "Insights", Icons.Filled.Star),
    PROFILE("profile", "Profile", Icons.Filled.Person),
    // Manager
    INVENTORY("inventory", "Inventory", Icons.AutoMirrored.Filled.List),
    PURCHASING("purchasing", "Purchasing", Icons.Filled.ShoppingCart),
    SALES("sales", "Sales", Icons.Filled.Edit),
    WASTE("waste", "Waste", Icons.Filled.Delete),
    EXPIRY("expiry", "Expiry", Icons.Filled.DateRange),
    // Kitchen staff
    QUICK_ENTRY("quick_entry", "Quick entry", Icons.AutoMirrored.Filled.List),
}

/** Tabs shown for each role's own screens. Higher roles reach lower roles via "View as". */
private fun tabsFor(role: Role): List<Destination> = when (role) {
    Role.OWNER -> listOf(Destination.OVERVIEW, Destination.REPORTS, Destination.INSIGHTS, Destination.PROFILE)
    Role.MANAGER -> listOf(
        Destination.INVENTORY, Destination.PURCHASING, Destination.SALES, Destination.WASTE, Destination.EXPIRY,
    )
    Role.STAFF -> listOf(Destination.QUICK_ENTRY)
}

@Composable
fun KitchenAppNavigation() {
    val roleManager = (LocalContext.current.applicationContext as KitchenApp).roleManager
    val isSetUp by roleManager.isSetUp.collectAsStateWithLifecycle()
    val session by roleManager.session.collectAsStateWithLifecycle()

    val current = session
    when {
        current != null -> key(current.viewing) { RoleShell(current, roleManager) }
        !isSetUp -> FirstTimeSetupScreen(roleManager)
        else -> LoginScreen(roleManager)
    }
}

@Composable
private fun RoleShell(session: Session, roleManager: RoleManager) {
    val tabs = tabsFor(session.viewing)
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val goToTab: (Destination) -> Unit = { dest ->
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        topBar = { RoleBar(session, onViewAs = roleManager::viewAs, onLock = roleManager::logout) },
        bottomBar = {
            if (tabs.size > 1) {
                NavigationBar {
                    tabs.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { goToTab(dest) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = tabs.first().route,
            // Consume insets so each screen's own Scaffold doesn't pad for the bars twice.
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            // Only the current role's screens are registered, so other roles' screens can't be reached.
            tabs.forEach { dest ->
                composable(dest.route) {
                    when (dest) {
                        Destination.OVERVIEW -> OverviewScreen(
                            onOpenInsights = { goToTab(Destination.INSIGHTS) },
                            onOpenProfile = { goToTab(Destination.PROFILE) },
                        )
                        Destination.REPORTS -> ReportsScreen()
                        Destination.INSIGHTS -> InsightsScreen()
                        Destination.PROFILE -> ProfileScreen()
                        Destination.INVENTORY -> InventoryScreen()
                        Destination.PURCHASING -> PurchasingScreen()
                        Destination.SALES -> ManagerSalesScreen()
                        Destination.WASTE -> WasteScreen()
                        Destination.EXPIRY -> ExpiryScreen()
                        Destination.QUICK_ENTRY -> QuickEntryScreen()
                    }
                }
            }
        }
    }
}

/** Shows who is signed in, lets Owner/Manager view lower roles' screens, and locks the app. */
@Composable
private fun RoleBar(session: Session, onViewAs: (Role) -> Unit, onLock: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = if (session.viewing == session.loggedIn) {
                session.loggedIn.label
            } else {
                "${session.loggedIn.label} · viewing ${session.viewing.label}"
            }
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))

            val viewable = session.loggedIn.canView
            if (viewable.size > 1) {
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text("View as") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        viewable.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.label + if (role == session.viewing) "  ✓" else "") },
                                onClick = {
                                    menuOpen = false
                                    onViewAs(role)
                                },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onLock) {
                Icon(Icons.Filled.Lock, contentDescription = "Lock / switch user")
            }
        }
    }
}
