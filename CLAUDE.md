# Battery Charge Info - Project Reference

## Overview
Android battery monitoring app displaying real-time stats including current, voltage, wattage, capacity, and charging time estimates. Uses MVVM architecture with Samsung-specific unit handling.

**Package:** `com.hootzen.batterychargeinfo`
**SDK:** API 24-35 | **Kotlin:** 2.1.0 | **Java:** 17

## Architecture (MVVM)

```
MainActivity (View)
    ↓
BatteryViewModel (StateFlow<BatteryInfo>)
    ↓
BatteryRepository (Business logic)
    ├── BatteryDataSource (sysfs + BatteryManager API)
    ├── BatteryUnitConverter (Samsung unit normalization)
    └── TimeToFullCalculator (charging time estimation)
```

## Key Files

| File | Purpose |
|------|---------|
| `MainActivity.kt` | UI, broadcast receiver, 1s polling handler |
| `ui/BatteryViewModel.kt` | StateFlow state management |
| `data/BatteryRepository.kt` | Core logic, parses intent extras |
| `data/BatteryDataSource.kt` | Reads sysfs & BatteryManager API |
| `data/BatteryUnitConverter.kt` | Normalizes µA/mA, µAh/mAh units |
| `util/TimeToFullCalculator.kt` | Moving average charging time calc |
| `model/BatteryInfo.kt` | Data class with all battery stats |
| `model/ChargingStatus.kt` | Sealed class + BatteryHealth enum |

## Samsung Handling

Samsung devices report units inconsistently. Solutions:
- **Current:** Wattage validation (>100W = µA units) + threshold fallback (>15000 = µA)
- **Capacity:** Threshold conversion (>10000 = µAh)
- **Sysfs paths:** Falls back to Samsung-specific `/sys/class/power_supply/battery/batt_current_ua_avg`

## Data Sources

1. **Primary:** `BatteryManager` API (`BATTERY_PROPERTY_CURRENT_NOW`, `BATTERY_PROPERTY_CHARGE_COUNTER`)
2. **Fallback:** Sysfs files (`/sys/class/power_supply/battery/current_now`, `charge_counter`)

## Build Commands

```bash
./gradlew assembleDebug    # Debug APK
./gradlew assembleRelease  # Release APK (minified)
```

## Features

- Real-time monitoring (1s updates via broadcast + polling)
- Charging time estimation (10-sample moving average, 5s stabilization)
- USB/AC/Wireless charging detection
- Battery health status (7 states)
- Dark mode support (Material 3)
- Portrait + landscape layouts

## Icons

- **App launcher:** White battery with green lightning bolt on green background (`ic_launcher_foreground.xml`)
- **Settings button:** Material Design gear icon (`ic_battery_settings.xml`)
- **Play Store:** SVG template at `play_store_icon.svg` (convert to 512x512 PNG for upload)

## No Permissions Required

App uses publicly available battery APIs - no special permissions needed.
