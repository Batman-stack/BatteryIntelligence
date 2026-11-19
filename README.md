# Battery Intelligence (v3.0.0 — Final Personal Release)

A local-first, offline-first native Android battery telemetry, charging analytics, 24-hour app usage intelligence, and device diagnostics application built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Hilt**, **Room**, **DataStore**, **WorkManager**, **Vico**, and **Glance**.

- **Target Platforms:** Android 12–17 (`minSdk = 31`, `compileSdk = 36`, `targetSdk = 36`)
- **Privacy Guarantee:** Zero `INTERNET` permission, zero cloud accounts, zero ads, zero analytics SDKs, zero hidden/reflection APIs, zero root required.

---

## Key Capabilities

### 1. Real-Time Charging Intelligence & Power Engine
- **Hardware-Honest Electrical Telemetry:** Reads `BatteryManager` and `ACTION_BATTERY_CHANGED`, normalizing OEM quirks (`µA` vs `mA`, `V` / `mV` / `µV`, inverted current sign polarity, thermistor dropout filtering).
- **Battery-Side Power Disclosure:** Computes `Power (W) = Voltage (mV) × Current (mA) / 1,000,000` at the battery cell fuel gauge and clearly explains PMIC/cable/load losses versus wall-adapter ratings.
- **Anti-Flicker Speed Tier Classifier:** Sliding-window median + EMA smoothing with hysteresis and minimum dwell timers (`Slow`, `Normal`, `Fast`, `Very Fast`) with customizable wattage thresholds—never fabricating unverified protocol claims (`USB-PD`, `Quick Charge`, `SuperVOOC`).
- **Thermal-Aware Hybrid CC/CV ETA Predictor:** Combines Coulomb-counter current rate and empirical `%/min` progress, models Constant-Current (CC) to Constant-Voltage (CV) taper above 80–85%, applies thermal-throttling slowdown factors, scores confidence (`High` / `Medium` / `Low` / `Calculating`), and records rolling ETA prediction history.
- **Stall / Trickle & Smart Alarms:** Detects charging stalls/trickle states, rapid heating slopes (`°C/min`), high temperature thresholds, custom target charge alarms (50–100%), low battery warnings, and optional unexpected charger disconnection alerts.

### 2. Battery Health & Thermal Wear Intelligence
- **State of Health (SoH %):** Displays hardware fuel-gauge SoH when exposed, or derives capacity from qualifying charging sessions (`≥20%` charge delta) against readable sysfs or user-configured design capacity.
- **Cycle Count & Thermal Wear:** Shows Android 14+ hardware cycle count when exposed alongside confidence-labeled cumulative tracked cycles, average charging temperature, peak observed temperature, and hot session count (`≥40°C`).
- **Overnight Standby Logs:** Preserves unplugged overnight drain rate tracking (`%/hr`) and min/max overnight temperatures.

### 3. Rolling 24-Hour App Usage Intelligence & 3-State Battery Attribution
- **Deterministic Session Reconstruction:** Reconstructs exact rolling 24-hour foreground sessions from `UsageStatsManager.queryEvents()` with multi-activity coalescing, `<3s` micro-gap merging, out-of-order sorting, screen-off / keyguard / reboot closure, and exact 24h boundary clipping.
- **Interactive Analytics:** Supports 5 sort modes (*Most Used*, *Least Used*, *Most Opened*, *Recently Opened*, *Highest Est. Battery*), an hourly Vico column chart, an interactive 24-hour timeline with inactivity gaps, and an App Detail modal bottom sheet.
- **Strict 3-State Battery Attribution:** Never invents fake per-app mAh values. Explicitly distinguishes:
  - `ACTUAL` (only if a future documented public Android API exposes direct per-app battery metrics),
  - `ESTIMATED` (bounded relative percentage share of observed 24h screen-on discharge with methodology disclosure and confidence rating),
  - `UNAVAILABLE` (whenever `<2%` discharge or `<2` samples are available).

### 4. Device Intelligence & Drain Diagnostics
- **Hardware-Honest Diagnostics:** Displays SoC/CPU metadata (`Build.SOC_MODEL`, `Build.SOC_MANUFACTURER`, ABIs, core count, readable `/sys/.../cpufreq` frequencies with graceful SELinux fallback), RAM & internal storage utilization, OS/security patch, system uptime vs. Deep Sleep %, display refresh rate & adaptive brightness, active network transport, `PowerManager` thermal severity & thermal headroom forecast, and `SensorManager` hardware sensor inventory.
- **Plain-English Drain Diagnostics:** Synthesizes thermal severity, deep-sleep efficiency, overnight drain, refresh rate, memory/storage pressure, and warm charging history into actionable advisories.

### 5. Light-First Tactile 3D UI, Glance Widget & Local Controls
- **Design System:** Crisp Pure White (`#FFFFFF`) and Off-White (`#F7FAF8`) surfaces, WCAG AA+ Deep Charcoal (`#111827`) typography, Fresh Emerald (`#16A34A` / `#15803D`) accents, Soft Mint (`#E8F5EE`) highlights, spring press scaling, animated hero battery ring, optional haptics, and reduced-motion accessibility compliance.
- **Local Export & Storage Controls:** Export charging sessions to CSV via Android's native Share Sheet, prune historical telemetry (`>30d` or `>90d`), toggle `°C`/`°F` and `mAh`/`Ah`, and inspect the built-in Local-First Privacy Policy.

---

## Permissions Justification

| Permission | Purpose | Optional? |
|---|---|---|
| `POST_NOTIFICATIONS` | Show foreground monitor notification and charging/thermal/disconnect alerts on Android 13+. | Requested on opt-in |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Sustain continuous high-resolution charging telemetry and smart alarms when enabled by user. | User-controlled toggle |
| `PACKAGE_USAGE_STATS` | Reconstruct rolling 24-hour foreground app sessions via `UsageStatsManager.queryEvents()`. | Optional (Analytics tab) |
| `RECEIVE_BOOT_COMPLETED` | Re-arm scheduled WorkManager telemetry and foreground monitor after device reboot if previously enabled. | Automatic |

---

## Known Android 12–17 Hardware & OEM Limitations

1. **No Public Per-App Hardware mAh API:** Android does not expose hardware-metered per-app mAh consumption to non-privileged apps. Battery Intelligence transparently reports `ESTIMATED` ranges or `UNAVAILABLE`.
2. **No Charger Protocol Handshake API:** Public `BatteryManager` does not expose `USB-PD`, `PPS`, `Quick Charge`, or `SuperVOOC` handshakes. Speed tiers are derived strictly from smoothed battery-side wattage.
3. **OEM Cycle Count & Sysfs Access:** Hardware cycle count (`EXTRA_CYCLE_COUNT`) requires Android 14+ OEM HAL support; `/sys/devices/system/cpu/.../cpufreq` may be blocked by SELinux on some OEM kernels. All unavailable readings fall back cleanly to `N/A` or explicit disclosures.
4. **Battery-Side vs. Wall Wattage:** Reported wattage reflects net power entering the cell after cable losses, PMIC conversion heat, and active screen/CPU draw.

---

## Build & Verification Commands

```bash
# Run all JVM unit tests
./gradlew testDebugUnitTest --no-daemon --stacktrace

# Build debug APK
./gradlew assembleDebug --no-daemon --stacktrace

# Run instrumented Room migration tests (requires connected device/emulator)
./gradlew connectedDebugAndroidTest --no-daemon --stacktrace
```
