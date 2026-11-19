# Battery Intelligence — Architecture Blueprint (v3.0.0)

## 1. Architectural Pattern
Battery Intelligence follows **Clean Architecture + MVVM + Unidirectional Data Flow (UDF)** with strict layer isolation:

```
┌──────────────────────────────────────────────────────────────────────┐
│ UI Layer (Jetpack Compose, Material 3, Vico 2.1.2, Glance 1.1.1)     │
│  ├── HomeDashboardScreen / ViewModel                                 │
│  ├── ChargingMonitorScreen & SessionDetailScreen / ViewModels        │
│  ├── AnalyticsScreen / AnalyticsViewModel (Rolling 24h Apps)         │
│  ├── BatteryHealthScreen / BatteryHealthViewModel (+ Device Intel)   │
│  ├── SettingsScreen / SettingsViewModel (Units, CSV, Prune, Alerts)  │
│  └── BatteryStatusGlanceWidget (Home-screen live widget)             │
├──────────────────────────────────────────────────────────────────────┤
│ Domain Layer (Pure Kotlin — Zero Android Framework Dependencies)     │
│  ├── domain/model/* (Enums, Snapshot, Charging, Usage, Device)       │
│  ├── domain/intelligence/                                            │
│  │    ├── ChargingSpeedClassifier (Median + EMA + Hysteresis)        │
│  │    ├── ChargingEtaPredictor (Hybrid Coulomb + %/min + CV Taper)   │
│  │    ├── ThermalSlopeAnalyzer (Linear regression °C/min)            │
│  │    ├── ChargingIntelligenceEngine (Orchestrator + Stall Detect)   │
│  │    ├── AppUsageSessionReconstructor (24h UsageEvents FSM)         │
│  │    ├── AppBatteryAttributionCalculator (3-state Attribution)      │
│  │    └── DeviceDrainDiagnosticsAnalyzer (Plain-English Insights)    │
│  └── domain/repository/* (Repository interfaces)                     │
├──────────────────────────────────────────────────────────────────────┤
│ Data & System Layer (Hilt DI, Room v3, DataStore, Android APIs)      │
│  ├── data/datasource/                                                │
│  │    ├── AndroidBatteryDataSource (Public BatteryManager + sysfs)   │
│  │    ├── BatteryMetricNormalizer (Unit & polarity normalization)    │
│  │    └── DeviceIntelligenceDataSource (CPU/RAM/Storage/Thermal)     │
│  ├── data/local/                                                     │
│  │    ├── BatteryDatabase (Room v3, MIGRATION_1_2 + MIGRATION_2_3)   │
│  │    ├── entity/* (6 entities incl. AppUsageSessionEntity)          │
│  │    └── dao/* (Typed Flow & suspend queries + retention pruning)   │
│  ├── data/session/ (ChargingSessionManager, OvernightLogManager)     │
│  ├── data/repository/* (Repository implementations + LRU icon cache) │
│  └── service/ & worker/ (BatteryMonitorService, TelemetryWorker)     │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 2. Database Schema & Non-Destructive Migrations (Room v3)

`BatteryDatabase` (`version = 3`) uses explicit, non-destructive migrations (`MIGRATION_1_2`, `MIGRATION_2_3`) with zero `fallbackToDestructiveMigration()`:

| Table | Purpose | Introduced |
|---|---|---|
| `battery_samples` | Periodic voltage, current, power, temperature, and level samples | v1 |
| `charging_sessions` | Completed and active charging sessions with peak/avg electrical & thermal metrics, CC/CV knee, and estimated capacity | v1 (extended v2) |
| `overnight_logs` | Unplugged overnight standby windows and drain rate (`%/hr`) | v1 |
| `charging_session_samples` | High-resolution per-session curve points for Vico detail charts | v2 |
| `daily_battery_rollups` | Daily aggregated charging energy, temperature, and discharge summaries | v2 |
| `app_usage_sessions` | Local cache of reconstructed rolling 24h foreground app sessions (`start_time_ms`, `end_time_ms`, `package_name`) | v3 |

---

## 3. Core Algorithmic Pipelines

### 3.1 Electrical Telemetry & Polarity Normalization
- `AndroidBatteryDataSource` reads only public Android APIs (`BatteryManager`, `ACTION_BATTERY_CHANGED`) and readable sysfs paths (zero hidden/reflection APIs).
- `BatteryMetricNormalizer` handles:
  - Voltage normalization: `1..25` → `V × 1000`, `1000..25000` → `mV`, `> 100000` → `µV / 1000`.
  - Current normalization: `µA` vs `mA` disambiguation with charge-counter rate hinting for high-current dual-cell fast charging.
  - Polarity auto-calibration: detects inverted OEM current sign and flags net discharging while plugged in.
  - Thermistor dropout filtering: rejects isolated `0.0°C` spikes when a warm baseline exists.

### 3.2 Charging Speed Classification & Hybrid ETA Prediction
- `ChargingSpeedClassifier`: 5-sample median filter + EMA (`α = 0.25`) + hysteresis band + dwell timer to eliminate tier flicker.
- `ChargingEtaPredictor`: suppresses premature guesses during the first 45s (`CALCULATING`), blends Coulomb current rate with empirical `%/min` progress, integrates piecewise CC→CV taper multipliers above 80–85%, scales down effective rate under thermal throttling (`≥41°C`, `≥44°C`, or heating slope `≥1.0°C/min`), and tracks up to 8 rolling `EtaHistoryPoint` snapshots.

### 3.3 Rolling 24-Hour App Usage Session State Machine
- `AppUsageSessionReconstructor` processes normalized `UsageEvents` (`FOREGROUND_ENTER`, `FOREGROUND_EXIT`, `SCREEN_ON`, `SCREEN_OFF`, `KEYGUARD_SHOWN`, `DEVICE_SHUTDOWN`, `DEVICE_STARTUP`):
  1. Sorts events monotonically (resilient to out-of-order OEM logs).
  2. Coalesces multi-activity transitions inside the same package.
  3. Closes active sessions on screen off, keyguard, reboot, or app switch.
  4. Clips active intervals precisely to `[windowStartMs, windowEndMs]`.
  5. Merges micro-interruptions (`≤ 3s`) for the same package.
  6. Generates 24 hourly buckets and explicit inactivity gap blocks (`≥ 5m`).
- `AppBatteryAttributionCalculator` enforces strict 3-state attribution (`ACTUAL` / `ESTIMATED` / `UNAVAILABLE`) with zero fabricated mAh numbers.

---

## 4. Lifecycle, Power Efficiency & Concurrency
- **Lifecycle-Aware Flows:** All ViewModels expose `StateFlow` via `SharingStarted.WhileSubscribed(5_000)` and UI collects via `collectAsStateWithLifecycle()`.
- **Permission Refresh on Resume:** `AnalyticsScreen`, `HomeDashboardScreen`, and `BatteryHealthScreen` observe `Lifecycle.Event.ON_RESUME` to refresh `PACKAGE_USAGE_STATS` and device diagnostics immediately when returning from Android Settings.
- **Background Safeguards:** High-frequency sampling runs only when the UI is active or the user explicitly enables `BatteryMonitorService` (`specialUse` FGS). Otherwise, low-overhead `WorkManager` (`BatteryTelemetryWorker`) records periodic telemetry and overnight logs.
- **Data Hygiene:** Users can prune historical telemetry older than 30 or 90 days directly from Settings, and export charging session history via CSV.
