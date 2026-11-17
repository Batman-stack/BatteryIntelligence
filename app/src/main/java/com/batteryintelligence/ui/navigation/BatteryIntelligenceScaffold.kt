package com.batteryintelligence.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.batteryintelligence.ui.analytics.AnalyticsScreen
import com.batteryintelligence.ui.charging.ChargingMonitorScreen
import com.batteryintelligence.ui.dashboard.HomeDashboardScreen
import com.batteryintelligence.ui.health.BatteryHealthScreen
import com.batteryintelligence.ui.settings.SettingsScreen

enum class NavTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.BatteryChargingFull),
    CHARGING("Charging", Icons.Default.Bolt),
    HEALTH("Health", Icons.Default.Favorite),
    ANALYTICS("Analytics", Icons.Default.BarChart),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryIntelligenceScaffold(
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentTab = NavTab.entries[selectedTabIndex]

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = currentTab.title) }
            )
        },
        bottomBar = {
            NavigationBar {
                NavTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                        label = { Text(text = tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier.padding(innerPadding)
        when (currentTab) {
            NavTab.DASHBOARD -> HomeDashboardScreen(modifier = screenModifier)
            NavTab.CHARGING -> ChargingMonitorScreen(modifier = screenModifier)
            NavTab.HEALTH -> BatteryHealthScreen(modifier = screenModifier)
            NavTab.ANALYTICS -> AnalyticsScreen(modifier = screenModifier)
            NavTab.SETTINGS -> SettingsScreen(modifier = screenModifier)
        }
    }
}
