package com.batteryintelligence.data.datasource

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.batteryintelligence.domain.model.BatteryHealthCondition
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.BatteryTelemetrySources
import com.batteryintelligence.domain.model.ChargingSource
import com.batteryintelligence.domain.model.ChargingStatus
import com.batteryintelligence.domain.model.MetricSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class AndroidBatteryDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : BatteryHardwareDataSource {

    private val batteryManager: BatteryManager? by lazy {
        context.getSystemService(BatteryManager::class.java)
    }

    private val hardwareCacheMutex = Mutex()
    private var designCapacityEvaluated = false
    private var cachedDesignCapacity: Pair<Double?, MetricSource> = null to MetricSource.UNAVAILABLE

    override suspend fun readSnapshot(): BatterySnapshot = withContext(Dispatchers.IO) {
        val stickyIntent = readStickyBatteryIntent()
        buildSnapshot(stickyIntent)
    }

    override fun observeBatteryChanges(samplingIntervalMs: Long): Flow<BatterySnapshot> = callbackFlow {
        val safeIntervalMs = samplingIntervalMs.coerceAtLeast(500L)
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    launch {
                        trySend(buildSnapshot(intent))
                    }
                }
            }
        }

        val initialStickyIntent = ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        trySend(buildSnapshot(initialStickyIntent))

        val tickerJob = launch {
            while (isActive) {
                delay(safeIntervalMs)
                val sticky = readStickyBatteryIntent()
                trySend(buildSnapshot(sticky))
            }
        }

        awaitClose {
            tickerJob.cancel()
            runCatching { context.unregisterReceiver(receiver) }
        }
    }.flowOn(Dispatchers.IO)

    private fun readStickyBatteryIntent(): Intent? {
        return runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
    }

    private suspend fun buildSnapshot(intent: Intent?): BatterySnapshot {
        val nowMs = System.currentTimeMillis()

        val (levelPercent, levelPrecisePercent) = resolveBatteryLevel(intent)
        val chargingStatus = resolveChargingStatus(intent)
        val chargingSource = resolveChargingSource(intent)
        val healthCondition = resolveHealthCondition(intent)

        val isBatteryPresent = intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_PRESENT) }
            ?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)

        val isBatteryLow = intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_BATTERY_LOW) }
            ?.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false)

        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val temperatureCelsius = resolveTemperatureCelsius(intent)
        val voltageMv = resolveVoltageMv(intent)

        val currentNowMa = readLongPropertySafely(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.let(BatteryMetricNormalizer::normalizeCurrentToMilliamps)

        val currentAverageMa = readLongPropertySafely(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            ?.let(BatteryMetricNormalizer::normalizeCurrentToMilliamps)

        val chargeCounterUah = readLongPropertySafely(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.let(BatteryMetricNormalizer::normalizeChargeCounterUah)

        val energyCounterNwh = readLongPropertySafely(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            ?.let(BatteryMetricNormalizer::normalizeEnergyCounterNwh)

        val (cycleCount, cycleCountSource) = resolveCycleCount(intent)
        val (designCapacityMah, designCapacitySource) = resolveDesignCapacityOnce()
        val (sohPercent, sohSource) = resolveHardwareStateOfHealth()

        val chargeTimeRemainingMs = if (chargingSource.isPlugged) {
            runCatching { batteryManager?.computeChargeTimeRemaining() }
                .getOrNull()
                ?.takeIf { it > 0L }
        } else {
            null
        }

        return BatterySnapshot(
            timestampMs = nowMs,
            levelPercent = levelPercent,
            levelPrecisePercent = levelPrecisePercent,
            chargingStatus = chargingStatus,
            chargingSource = chargingSource,
            healthCondition = healthCondition,
            isBatteryPresent = isBatteryPresent,
            isBatteryLow = isBatteryLow,
            technology = technology,
            temperatureCelsius = temperatureCelsius,
            voltageMv = voltageMv,
            currentNowMa = currentNowMa,
            currentAverageMa = currentAverageMa,
            chargeCounterUah = chargeCounterUah,
            energyCounterNwh = energyCounterNwh,
            cycleCount = cycleCount,
            hardwareStateOfHealthPercent = sohPercent,
            designCapacityMah = designCapacityMah,
            chargeTimeRemainingMs = chargeTimeRemainingMs,
            telemetrySources = BatteryTelemetrySources(
                cycleCountSource = cycleCountSource,
                stateOfHealthSource = sohSource,
                designCapacitySource = designCapacitySource
            )
        )
    }

    private fun resolveBatteryLevel(intent: Intent?): Pair<Int?, Float?> {
        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val rawScale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (rawLevel >= 0 && rawScale > 0) {
            val precise = ((rawLevel.toFloat() / rawScale.toFloat()) * 100f).coerceIn(0f, 100f)
            val rounded = ((rawLevel * 100) / rawScale).coerceIn(0, 100)
            return rounded to precise
        }

        val propCapacity = readIntPropertySafely(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (propCapacity != null && propCapacity in 0..100) {
            return propCapacity to propCapacity.toFloat()
        }
        return null to null
    }

    private fun resolveChargingStatus(intent: Intent?): ChargingStatus {
        val rawExtra = intent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        val statusCode = if (rawExtra != BatteryManager.BATTERY_STATUS_UNKNOWN) {
            rawExtra
        } else {
            readIntPropertySafely(BatteryManager.BATTERY_PROPERTY_STATUS)
                ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        }

        return when (statusCode) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargingStatus.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargingStatus.DISCHARGING
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargingStatus.NOT_CHARGING
            BatteryManager.BATTERY_STATUS_FULL -> ChargingStatus.FULL
            else -> ChargingStatus.UNKNOWN
        }
    }

    private fun resolveChargingSource(intent: Intent?): ChargingSource {
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        if (plugged < 0) return ChargingSource.UNKNOWN
        if (plugged == 0) return ChargingSource.NONE

        val isDock = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (plugged and BatteryManager.BATTERY_PLUGGED_DOCK) != 0

        return when {
            (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> ChargingSource.WIRELESS
            (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> ChargingSource.AC
            (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> ChargingSource.USB
            isDock -> ChargingSource.DOCK
            else -> ChargingSource.UNKNOWN
        }
    }

    private fun resolveHealthCondition(intent: Intent?): BatteryHealthCondition {
        val rawHealth = intent?.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_UNKNOWN
        ) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN

        return when (rawHealth) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealthCondition.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealthCondition.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealthCondition.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealthCondition.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealthCondition.UNSPECIFIED_FAILURE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealthCondition.COLD
            else -> BatteryHealthCondition.UNKNOWN
        }
    }

    private fun resolveTemperatureCelsius(intent: Intent?): Float? {
        val rawExtra = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        BatteryMetricNormalizer.normalizeTemperatureCelsius(rawExtra)?.let { return it }

        val sysfsTemp = readSysfsInt(SYSFS_TEMP_PATHS)
        return sysfsTemp?.let(BatteryMetricNormalizer::normalizeTemperatureCelsius)
    }

    private fun resolveVoltageMv(intent: Intent?): Int? {
        val rawExtra = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        BatteryMetricNormalizer.normalizeVoltageToMillivolts(rawExtra)?.let { return it }

        val sysfsVoltage = readSysfsInt(SYSFS_VOLTAGE_PATHS)
        return sysfsVoltage?.let(BatteryMetricNormalizer::normalizeVoltageToMillivolts)
    }

    private fun resolveCycleCount(intent: Intent?): Pair<Int?, MetricSource> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val rawExtra = intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, Int.MIN_VALUE)
                ?: Int.MIN_VALUE
            val normalized = BatteryMetricNormalizer.normalizeCycleCount(rawExtra)
            if (normalized != null) {
                return normalized to MetricSource.ACTION_BATTERY_CHANGED
            }
        }

        val sysfsCycle = readSysfsInt(SYSFS_CYCLE_COUNT_PATHS)
            ?.let(BatteryMetricNormalizer::normalizeCycleCount)
        if (sysfsCycle != null) {
            return sysfsCycle to MetricSource.SYSFS
        }

        return null to MetricSource.UNAVAILABLE
    }

    private fun resolveHardwareStateOfHealth(): Pair<Int?, MetricSource> {
        // Android 14+ (API 34+) defines BATTERY_PROPERTY_STATE_OF_HEALTH = 10 as a system property;
        // query safely without direct @SystemApi reference and validate against 1..100.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val propSoh = readIntPropertySafely(BATTERY_PROPERTY_STATE_OF_HEALTH_ID)
                ?.let(BatteryMetricNormalizer::normalizeStateOfHealth)
            if (propSoh != null) {
                return propSoh to MetricSource.BATTERY_MANAGER
            }
        }

        val chargeFull = readSysfsLong(SYSFS_CHARGE_FULL_PATHS)
        val chargeFullDesign = readSysfsLong(SYSFS_CHARGE_FULL_DESIGN_PATHS)
        if (chargeFull != null && chargeFullDesign != null && chargeFull > 0L && chargeFullDesign > 0L) {
            val calculatedSoh = ((chargeFull.toDouble() / chargeFullDesign.toDouble()) * 100.0)
                .roundToInt()
                .let(BatteryMetricNormalizer::normalizeStateOfHealth)
            if (calculatedSoh != null) {
                return calculatedSoh to MetricSource.SYSFS
            }
        }

        return null to MetricSource.UNAVAILABLE
    }

    private suspend fun resolveDesignCapacityOnce(): Pair<Double?, MetricSource> {
        if (designCapacityEvaluated) {
            return cachedDesignCapacity
        }
        return hardwareCacheMutex.withLock {
            if (!designCapacityEvaluated) {
                cachedDesignCapacity = lookupDesignCapacity()
                designCapacityEvaluated = true
            }
            cachedDesignCapacity
        }
    }

    private fun lookupDesignCapacity(): Pair<Double?, MetricSource> {
        readDesignCapacityFromPowerProfile()?.let { mah ->
            return mah to MetricSource.POWER_PROFILE
        }

        val sysfsDesignMah = readSysfsLong(SYSFS_CHARGE_FULL_DESIGN_PATHS)
            ?.let(BatteryMetricNormalizer::normalizeCapacityToMah)
        if (sysfsDesignMah != null) {
            return sysfsDesignMah to MetricSource.SYSFS
        }

        return null to MetricSource.UNAVAILABLE
    }

    private fun readDesignCapacityFromPowerProfile(): Double? {
        return runCatching {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val constructor = powerProfileClass.getConstructor(Context::class.java)
            val powerProfile = constructor.newInstance(context)

            val rawCapacity = runCatching {
                val method = powerProfileClass.getMethod("getBatteryCapacity")
                (method.invoke(powerProfile) as? Number)?.toDouble()
            }.getOrNull() ?: runCatching {
                val method = powerProfileClass.getMethod("getAveragePower", String::class.java)
                (method.invoke(powerProfile, "battery.capacity") as? Number)?.toDouble()
            }.getOrNull()

            rawCapacity?.takeIf { it.isFinite() && it > 100.0 }
        }.getOrNull()
    }

    private fun readIntPropertySafely(propertyId: Int): Int? {
        return runCatching {
            batteryManager?.getIntProperty(propertyId)
        }.getOrNull()?.takeIf { it != Int.MIN_VALUE }
    }

    private fun readLongPropertySafely(propertyId: Int): Long? {
        return runCatching {
            batteryManager?.getLongProperty(propertyId)
        }.getOrNull()?.takeIf { it != Long.MIN_VALUE && it != Int.MIN_VALUE.toLong() }
    }

    private fun readSysfsInt(paths: List<String>): Int? {
        return readSysfsLong(paths)?.toInt()
    }

    private fun readSysfsLong(paths: List<String>): Long? {
        for (path in paths) {
            val value = runCatching {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    file.readText().trim().toLongOrNull()
                } else {
                    null
                }
            }.getOrNull()
            if (value != null) {
                return value
            }
        }
        return null
    }

    private companion object {
        const val BATTERY_PROPERTY_STATE_OF_HEALTH_ID = 10

        val SYSFS_CYCLE_COUNT_PATHS = listOf(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/bms/cycle_count"
        )

        val SYSFS_CHARGE_FULL_PATHS = listOf(
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/bms/charge_full"
        )

        val SYSFS_CHARGE_FULL_DESIGN_PATHS = listOf(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/bms/charge_full_design"
        )

        val SYSFS_VOLTAGE_PATHS = listOf(
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/bms/voltage_now"
        )

        val SYSFS_TEMP_PATHS = listOf(
            "/sys/class/power_supply/battery/temp",
            "/sys/class/power_supply/bms/temp"
        )
    }
}