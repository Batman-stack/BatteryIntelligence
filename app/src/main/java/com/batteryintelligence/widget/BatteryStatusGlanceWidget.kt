package com.batteryintelligence.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.batteryintelligence.MainActivity
import com.batteryintelligence.data.datasource.BatteryHardwareDataSource
import com.batteryintelligence.domain.intelligence.ChargingIntelligenceEngine
import com.batteryintelligence.domain.model.ChargingIntelligenceState
import com.batteryintelligence.domain.repository.PreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.abs

class BatteryStatusGlanceWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun batteryHardwareDataSource(): BatteryHardwareDataSource
        fun chargingIntelligenceEngine(): ChargingIntelligenceEngine
        fun preferencesRepository(): PreferencesRepository
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_SIZE, EXPANDED_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val snapshot = entryPoint.batteryHardwareDataSource().readSnapshot()
        val prefs = entryPoint.preferencesRepository().preferences.first()
        val intelligence = entryPoint.chargingIntelligenceEngine().processSnapshot(
            snapshot = snapshot,
            preferences = prefs,
            effectiveCapacityMah = snapshot.designCapacityMah
        )

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_INITIAL_TAB, MainActivity.TAB_CHARGING)
        }

        provideContent {
            val size = LocalSize.current
            val isExpanded = size.height >= 100.dp || size.width >= 240.dp
            WidgetBody(
                state = intelligence,
                targetPercent = prefs.targetChargeAlertPercent ?: 80,
                isExpanded = isExpanded,
                launchIntent = launchIntent
            )
        }
    }

    @Composable
    private fun WidgetBody(
        state: ChargingIntelligenceState,
        targetPercent: Int,
        isExpanded: Boolean,
        launchIntent: Intent
    ) {
        val snapshot = state.snapshot
        val level = snapshot?.levelPercent
        val isCharging = snapshot?.isCharging == true
        val watts = state.smoothedPowerWatts ?: snapshot?.powerWatts
        val currentMa = state.smoothedCurrentMa ?: snapshot?.effectiveCurrentMa
        val tempC = snapshot?.temperatureCelsius

        val surfaceColor = if (isCharging) {
            ColorProvider(day = Color(0xFFE8F5EE), night = Color(0xFF132E20))
        } else {
            ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF111916))
        }
        val titleColor = ColorProvider(day = Color(0xFF111827), night = Color(0xFFF8FAFC))
        val accentTextColor = ColorProvider(day = Color(0xFF15803D), night = Color(0xFF4ADE80))
        val secondaryColor = ColorProvider(day = Color(0xFF475569), night = Color(0xFF94A3B8))

        val powerText = watts?.let { String.format(Locale.US, "%.1f W", it) } ?: "-- W"
        val currentText = currentMa?.let { String.format(Locale.US, "%.0f mA", abs(it)) } ?: "-- mA"
        val tempText = tempC?.let { String.format(Locale.US, "%.1f°C", it) } ?: "--°C"
        val etaText = state.etaEstimate?.timeToTargetMs?.let { ms ->
            val mins = (ms / 60_000L).coerceAtLeast(1L)
            val h = mins / 60L
            val m = mins % 60L
            if (h > 0) "${h}h ${m}m to $targetPercent%" else "${m}m to $targetPercent%"
        } ?: if (isCharging) "Calculating ETA..." else "Target $targetPercent%"

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(surfaceColor)
                .cornerRadius(20.dp)
                .clickable(actionStartActivity(launchIntent))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = level?.let { "$it%" } ?: "--%",
                    style = TextStyle(
                        color = titleColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = if (isCharging) "${state.speedTier.displayLabel} • $powerText" else "On Battery",
                        style = TextStyle(
                            color = if (isCharging) accentTextColor else titleColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = etaText,
                        style = TextStyle(
                            color = secondaryColor,
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current: $currentText",
                        style = TextStyle(color = secondaryColor, fontSize = 11.sp),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        text = "Temp: $tempText",
                        style = TextStyle(color = secondaryColor, fontSize = 11.sp)
                    )
                }
            }
        }
    }

    companion object {
        private val COMPACT_SIZE = DpSize(130.dp, 56.dp)
        private val EXPANDED_SIZE = DpSize(250.dp, 110.dp)
    }
}
