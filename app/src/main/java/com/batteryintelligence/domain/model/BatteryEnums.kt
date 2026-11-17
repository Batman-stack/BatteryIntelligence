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

enum class ChargingSpeedTier {
    DISCHARGING,
    TRICKLE,
    STANDARD,
    FAST,
    SUPER_FAST,
    UNKNOWN
}

enum class MetricSource {
    BATTERY_MANAGER,
    ACTION_BATTERY_CHANGED,
    POWER_PROFILE,
    SYSFS,
    SESSION_DERIVED,
    USER_CONFIGURED,
    UNAVAILABLE
}