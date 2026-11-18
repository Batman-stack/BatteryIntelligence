package com.batteryintelligence

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryintelligence.domain.model.MonitoringPreferences
import com.batteryintelligence.domain.repository.PreferencesRepository
import com.batteryintelligence.ui.navigation.AppNavigation
import com.batteryintelligence.ui.theme.BatteryIntelligenceTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    private var initialTabExtra by mutableStateOf<String?>(null)

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result observed reactively by UI screens on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initialTabExtra = intent?.getStringExtra(EXTRA_INITIAL_TAB)

        if (savedInstanceState == null) {
            requestPostNotificationsIfNeeded()
        }

        setContent {
            val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = MonitoringPreferences()
            )
            BatteryIntelligenceTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.useDynamicColor,
                hapticsEnabled = prefs.hapticsEnabled
            ) {
                AppNavigation(initialTab = initialTabExtra)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_INITIAL_TAB)?.let {
            initialTabExtra = it
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"
        const val TAB_CHARGING = "charging"
    }
}