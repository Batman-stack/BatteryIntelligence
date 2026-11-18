package com.batteryintelligence.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object ChargingRoute

@Serializable
data object AnalyticsRoute

@Serializable
data object HealthRoute

@Serializable
data object SettingsRoute

@Serializable
data class ChargingSessionDetailRoute(val sessionId: Long)

@Composable
fun AppNavigation(
    initialTab: String? = null,
    modifier: Modifier = Modifier
) {
    BatteryIntelligenceScaffold(
        initialTab = initialTab,
        modifier = modifier
    )
}