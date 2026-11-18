package com.batteryintelligence.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.batteryintelligence.MainActivity
import com.batteryintelligence.ui.analytics.AnalyticsScreen
import com.batteryintelligence.ui.charging.ChargingMonitorScreen
import com.batteryintelligence.ui.charging.ChargingSessionDetailScreen
import com.batteryintelligence.ui.components.BatterySidePowerDisclosureDialog
import com.batteryintelligence.ui.dashboard.HomeDashboardScreen
import com.batteryintelligence.ui.health.BatteryHealthScreen
import com.batteryintelligence.ui.settings.SettingsScreen
import com.batteryintelligence.ui.theme.LocalBatterySemanticColors

enum class TopLevelDestination(
    val title: String,
    val icon: ImageVector,
    val route: Any
) {
    HOME("Home", Icons.Default.BatteryChargingFull, HomeRoute),
    CHARGING("Charging", Icons.Default.Bolt, ChargingRoute),
    ANALYTICS("Analytics", Icons.Default.BarChart, AnalyticsRoute),
    HEALTH("Health", Icons.Default.Favorite, HealthRoute),
    SETTINGS("Settings", Icons.Default.Settings, SettingsRoute)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryIntelligenceScaffold(
    initialTab: String? = null,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val semantic = LocalBatterySemanticColors.current

    var showPowerDisclosure by remember { mutableStateOf(false) }

    LaunchedEffect(initialTab) {
        if (initialTab == MainActivity.TAB_CHARGING) {
            navController.navigate(ChargingRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val isDetailRoute = currentDestination?.hasRoute<ChargingSessionDetailRoute>() == true
    val currentTopTab = TopLevelDestination.entries.firstOrNull { dest ->
        when (dest) {
            TopLevelDestination.HOME -> currentDestination?.hasRoute<HomeRoute>() == true
            TopLevelDestination.CHARGING -> currentDestination?.hasRoute<ChargingRoute>() == true
            TopLevelDestination.ANALYTICS -> currentDestination?.hasRoute<AnalyticsRoute>() == true
            TopLevelDestination.HEALTH -> currentDestination?.hasRoute<HealthRoute>() == true
            TopLevelDestination.SETTINGS -> currentDestination?.hasRoute<SettingsRoute>() == true
        }
    } ?: TopLevelDestination.HOME

    if (showPowerDisclosure) {
        BatterySidePowerDisclosureDialog(onDismiss = { showPowerDisclosure = false })
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Text(
                        text = if (isDetailRoute) "Session Details" else currentTopTab.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (isDetailRoute) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    if (currentTopTab == TopLevelDestination.HOME ||
                        currentTopTab == TopLevelDestination.CHARGING ||
                        isDetailRoute
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showPowerDisclosure = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "Battery-side wattage info",
                                    tint = semantic.emeraldText
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isDetailRoute) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = dest == currentTopTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = dest.icon, contentDescription = dest.title) },
                            label = { Text(text = dest.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = semantic.emeraldText,
                                selectedTextColor = semantic.emeraldText,
                                indicatorColor = semantic.mintSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier.padding(innerPadding)
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = screenModifier
        ) {
            composable<HomeRoute> {
                HomeDashboardScreen(
                    onOpenDisclosure = { showPowerDisclosure = true }
                )
            }
            composable<ChargingRoute> {
                ChargingMonitorScreen(
                    onSessionClick = { sessionId ->
                        navController.navigate(ChargingSessionDetailRoute(sessionId))
                    },
                    onOpenDisclosure = { showPowerDisclosure = true }
                )
            }
            composable<ChargingSessionDetailRoute> {
                ChargingSessionDetailScreen(
                    onOpenDisclosure = { showPowerDisclosure = true }
                )
            }
            composable<AnalyticsRoute> {
                AnalyticsScreen()
            }
            composable<HealthRoute> {
                BatteryHealthScreen(
                    onNavigateToSettings = {
                        navController.navigate(SettingsRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
        }
    }
}
