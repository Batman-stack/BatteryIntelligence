package com.batteryintelligence.domain.model

enum class ChargingStatus {
    CHARGING,
    DISCHARGING,
    NOT_CHARGING,
    FULL,
    UNKNOWN
}

enum class ChargingSource {
    NONE,
    AC,
    USB,
    WIRELESS,
    DOCK,
    UNKNOWN;

    val isPlugged: Boolean
        get() = this == AC || this == USB || this == WIRELESS || this == DOCK

    val isWired: Boolean
        get() = this == AC || this == USB

    val isWireless: Boolean
        get() = this == WIRELESS

    val displayLabel: String
        get() = when (this) {
            NONE -> "Battery"
            AC -> "AC Adapter"
            USB -> "USB"
            WIRELESS -> "Wireless"
            DOCK -> "Dock"
            UNKNOWN -> "Unknown"
        }
}

enum class BatteryHealthCondition {
    GOOD,
    OVERHEAT,
    DEAD,
    OVER_VOLTAGE,
    UNSPECIFIED_FAILURE,
    COLD,
    UNKNOWN
}

enum class ChargingSpeedTier(val displayLabel: String) {
    DISCHARGING("Discharging"),
    SLOW("Slow"),
    NORMAL("Normal"),
    FAST("Fast"),
    VERY_FAST("Very Fast"),
    PAUSED_HOLD("Paused"),
    CALCULATING("Calculating"),
    UNKNOWN("Unknown");

    companion object {
        fun fromStoredName(value: String?): ChargingSpeedTier {
            return when (value) {
                "TRICKLE" -> SLOW
                "STANDARD" -> NORMAL
                "SUPER_FAST" -> VERY_FAST
                null -> UNKNOWN
                else -> runCatching { valueOf(value) }.getOrDefault(UNKNOWN)
            }
        }
    }
}

enum class ChargingPhase(val displayLabel: String) {
    CC("Constant Current"),
    CV("Constant Voltage"),
    TAPER("Taper"),
    FULL("Full"),
    PAUSED("Paused"),
    DISCHARGING("Discharging"),
    UNKNOWN("Unknown")
}

enum class ChargingConfidence(val displayLabel: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
    CALCULATING("Calculating")
}

enum class ChargingTerminationReason(val displayLabel: String) {
    ACTIVE("Active"),
    USER_UNPLUGGED("Unplugged"),
    REACHED_FULL("Reached Full"),
    STALE_GAP_CLOSED("Recovered After Gap"),
    SERVICE_RESTART("Service Restart"),
    UNKNOWN("Unknown")
}

enum class MetricSource(val displayLabel: String) {
    BATTERY_MANAGER("Hardware Fuel Gauge"),
    ACTION_BATTERY_CHANGED("System Broadcast"),
    POWER_PROFILE("Device Power Profile"),
    SYSFS("Kernel Sysfs"),
    SESSION_DERIVED("Session-Derived"),
    USER_CONFIGURED("User Override"),
    UNAVAILABLE("Unavailable")
}

enum class AppThemeMode(val displayLabel: String) {
    LIGHT("Light (Default)"),
    DARK("Dark"),
    SYSTEM("Follow System")
}