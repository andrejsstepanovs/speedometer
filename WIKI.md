# GPS Speedometer - Product Requirements & UX Documentation

## Overview

**App Name:** GPS Speedometer  
**Package:** `com.legitdev.speedometer.app`  
**Type:** Native Android application  
**Core Function:** Real-time GPS-based speedometer with HUD interface for drivers

---

## 1. Information Architecture

### App Structure

This is a **single-screen utility app** with no traditional navigation. It displays real-time speed data with a minimalist HUD-style interface.

```
User launches app
       ↓
MainActivity
       ↓
Check/request location permissions
       ↓
SpeedometerScreen (Compose UI)
       ↓
GPS location updates via LocationRepository
       ↓
SpeedometerViewModel (state management)
```

### Navigation Elements

- **None** — No menus, tabs, or navigation graph
- **Single entry point:** MainActivity is the launcher activity
- **No settings screen** — App is "fire and forget"
- **Picture-in-Picture (PiP) mode** — Overlay support for multitasking

---

## 2. Screen Inventory

### Screen: SpeedometerScreen (Main UI)

**Type:** Jetpack Compose screen (fullscreen)  
**Purpose:** Display real-time GPS speed with session statistics

#### Visual Layout (Top to Bottom)

**A. Satellite Status (Top Left)**
- Green/Red dot indicator (12dp circle)
- Text: `satellites: N` (monospace font, 16sp)
- Color: Green when ≥3 satellites, Red when <3

**B. Speed Display (Center)**
- Large speed number split into integer + decimal parts
- Integer part: 120sp bold, large letter-spacing (-4sp)
- Decimal part: `.XX` in 40sp, secondary color
- Unit: `km/h` in 24sp, tertiary color
- Format: `XXX.XX km/h`

**C. Statistics Area (Bottom Left)**
- Horizontal divider (150dp width)
- `top speed: X.X` — tracked maximum speed
- `top satellites: N` — highest satellite count seen

**D. PiP Button (Bottom Right)**
- Button labeled "Float"
- Opens Picture-in-Picture mode (Android O+)
- Uses `PictureInPictureParams.Builder` with 16:9 aspect ratio

#### PiP Mode Layout

When in Picture-in-Picture:
- Speed display scales down: main=64sp, decimal=24sp, unit=14sp
- Satellite status and stats areas are hidden
- Minimal overlay showing just the speed reading

#### Color Scheme (Dark/Light Theme Aware)

| Element | Dark Theme | Light Theme |
|---------|------------|-------------|
| Background | Black | White |
| Primary (speed integer) | White | Black |
| Secondary (decimal) | LightGray | DarkGray |
| Tertiary (unit) | Gray | Gray |
| Label text | Gray | DarkGray |
| Divider | DarkGray | LightGray |
| PiP button container | Gray | LightGray |
| PiP button text | White | Black |
| Satellite indicator | Green (≥3) / Red (<3) | Same |

---

## 3. User Interactions & Flows

### Flow 1: First Launch

```
1. User taps "GPS Speedometer" icon
2. MainActivity.onCreate() initializes:
   - SpeedometerViewModel via ViewModelFactory
   - LocationRepositoryImpl instance
3. Permission check:
   - If not granted: Request ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION
   - If granted: Start location tracking immediately
4. setContent() renders SpeedometerScreen with ViewModel state
```

### Flow 2: Permission Handling

```
1. requestPermissionLauncher registers for multiple permissions
2. System shows permission dialog(s)
3. On result:
   - ACCESS_FINE_LOCATION granted → startLocationTracking()
   - ACCESS_FINE_LOCATION denied:
     - If coarse granted: "Precise Location required for GPS speed accuracy."
     - If both denied: "Location permission denied."
4. Error message displayed in red on SpeedometerScreen
```

### Flow 3: GPS Location Updates

```
1. LocationRepositoryImpl.requestLocationUpdates(GPS_PROVIDER)
2. GnssStatus.Callback tracks satellite count (usedInFix)
3. On each LocationListener.onLocationChanged():
   - Create GpsReading(speed, accuracy, satelliteCount, timestamp)
   - Push to SpeedometerViewModel.onGpsReadingReceived()
4. SpeedometerViewModel:
   - GpsSignalFilter validates reading (accuracy ≤50m, speed ≥1.5km/h)
   - SessionStatisticsTracker updates current/max stats
   - Compose state updates trigger recomposition
```

### Flow 4: Watchdog Timer (Tunnel Detection)

```
1. startWatchdog() launches 1-second interval coroutine
2. Tracks lastFixTime (updated on each GPS reading)
3. If >2000ms pass with speed >0:
   - Inject fake GpsReading with speed=0
   - Simulates GPS signal loss handling
4. stopWatchdog() cancels on onStop()
```

### Flow 5: Session Lifecycle

```
onStart():
  - checkPermissionsAndStart()
  - startWatchdog()
  - viewModel.onSessionStart()

onStop():
  - stopLocationTracking()
  - stopWatchdog()
  - If NOT changing configurations:
      viewModel.onSessionReset() → clears all stats
```

### Flow 6: Picture-in-Picture Mode

```
1. User taps "Float" button
2. enterPipMode() builds PictureInPictureParams:
   - aspectRatio: Rational(16, 9)
3. enterPictureInPictureMode(params)
4. onPictureInPictureModeChanged() updates isInPipMode state
5. UI hides satellite status and stats, scales down fonts
```

---

## 4. Edge Cases & Error Handling

### Edge Case: GPS Signal Loss

| Scenario | Behavior |
|----------|----------|
| GPS stops providing updates | Watchdog injects speed=0 readings after 2s timeout |
| No satellites (<3) | Satellite indicator turns red, speed still displays |
| Signal returns | Normal updates resume, watchdog resets |

### Edge Case: Invalid GPS Readings

| Condition | Filter Behavior | Result |
|-----------|----------------|--------|
| Accuracy > 50m | `hasAcceptableAccuracy()` returns false | Speed set to 0 |
| Speed < 1.5 km/h | `hasAcceptableSpeed()` returns false | Speed set to 0 |
| Both invalid | Both checks fail | Speed set to 0, stats preserved |

**Code Reference:** `GpsSignalFilter.kt`

```kotlin
fun isSignalAcceptable(reading: GpsReading): Boolean {
    return hasAcceptableAccuracy(reading) && hasAcceptableSpeed(reading)
}

private fun hasAcceptableAccuracy(reading: GpsReading): Boolean {
    return reading.accuracyMeters == null || reading.accuracyMeters <= config.maxAccuracyMeters
}

private fun hasAcceptableSpeed(reading: GpsReading): Boolean {
    val speedKmh = SpeedConverter.metersPerSecondToKmh(reading.speedMetersPerSecond)
    return speedKmh >= config.minSpeedKmh
}
```

### Edge Case: Warmup Period

| Scenario | Behavior |
|----------|----------|
| First 5 seconds after session start | maxSpeedKmh not updated |
| After warmup (≥5s) + ≥3 satellites | maxSpeedKmh tracked normally |

**Code Reference:** `SessionStatisticsTracker.kt`

```kotlin
val elapsed = timeProvider.currentTimeMillis() - sessionStartTime
if (elapsed >= config.warmupPeriodMillis && 
    reading.satelliteCount >= config.minSatellitesForTracking) {
    maxSpeedKmh = max(maxSpeedKmh, currentSpeedKmh)
}
```

### Edge Case: Permission Denied

| Permission | Error Message |
|------------|---------------|
| Only ACCESS_COARSE_LOCATION granted | "Precise Location required for GPS speed accuracy.\nPlease allow 'Precise' in settings." |
| Both denied | "Location permission denied.\nApp requires GPS access to function." |

### Edge Case: GPS Provider Disabled

| Scenario | Behavior |
|----------|----------|
| GPS provider turned off | `onProviderDisabled()` fires, error pushed to ViewModel |
| User sees error message in red | Overlay stops updating |

### Edge Case: App Backgrounded

| Scenario | Behavior |
|----------|----------|
| User switches away (onStop) | GPS hardware disconnected, session reset |
| User returns (onStart) | New session starts, stats cleared |

---

## 5. Technical Components

### Core Architecture

| Component | File | Purpose |
|-----------|------|---------|
| Entry Point | `MainActivity.kt` | Permission handling, lifecycle, GPS management |
| ViewModel | `SpeedometerViewModel.kt` | State management, GPS reading processing |
| Repository Interface | `data/repository/LocationRepository.kt` | Location update contract |
| Repository Impl | `data/repository/LocationRepositoryImpl.kt` | Android LocationManager/GnssStatus integration |
| DI Factory | `di/SpeedometerViewModelFactory.kt` | Manual dependency injection |
| GPS Signal Filter | `domain/GpsSignalFilter.kt` | Validates GPS reading quality |
| Session Tracker | `domain/SessionStatisticsTracker.kt` | Tracks speed and satellite stats |
| Time Abstraction | `domain/TimeProvider.kt` | Interface for time (testability) |
| Time Impl | `domain/time/ProductionTimeProvider.kt` | `SystemClock.elapsedRealtime()` implementation |
| Speed Converter | `domain/util/SpeedConverter.kt` | m/s ↔ km/h conversion |
| Domain Models | `domain/model/` | GpsReading, SpeedometerState, SessionConfig, SessionStatistics |

### Default Configuration (SessionConfig)

| Parameter | Default | Purpose |
|-----------|---------|---------|
| `warmupPeriodMillis` | 5000L | 5-second warmup before tracking max speed |
| `minSatellitesForTracking` | 3 | Minimum satellites for valid reading |
| `maxAccuracyMeters` | 50f | Max GPS accuracy tolerance |
| `minSpeedKmh` | 1.5f | Minimum speed to register as valid |
| `gpsSignalTimeoutMillis` | 2000L | Watchdog timeout threshold |

### State Management

**SpeedometerState (Compose UI state):**
```kotlin
data class SpeedometerState(
    val currentSpeedKmh: Float,    // Current speed from GPS
    val maxSpeedKmh: Float,         // Session max speed
    val satelliteCount: Int,        // Current satellite count
    val maxSatelliteCount: Int      // Session max satellites
)
```

**GpsReading (from LocationRepository):**
```kotlin
data class GpsReading(
    val speedMetersPerSecond: Float,
    val accuracyMeters: Float?,
    val satelliteCount: Int,
    val timestamp: Long
)
```

### Permissions

| Permission | Purpose |
|------------|---------|
| `ACCESS_FINE_LOCATION` | GPS speed calculation (required) |
| `ACCESS_COARSE_LOCATION` | Fallback location (secondary) |

**No internet permissions** — Privacy-focused, no tracking or telemetry.

### Dependencies

| Library | Version |
|---------|---------|
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 1.9.0 |
| Compose Compiler | 1.5.1 |
| Compose BOM | 2023.08.00 |
| Core KTX | 1.12.0 |
| Lifecycle Runtime KTX | 2.7.0 |
| Activity Compose | 1.8.2 |
| Material3 | (via BOM) |

### Build Commands

```bash
# Debug APK (faster, no signing)
./gradlew assembleDebug

# Release APK (requires keystore.properties)
./gradlew assembleRelease

# Install to connected device
./gradlew installDebug

# Run tests
./gradlew test

# Lint checks
./gradlew lint
```

### APK Locations

- **Debug:** `app/build/outputs/apk/debug/app-debug.apk`
- **Release:** `app/build/outputs/apk/release/app-release.apk`

---

## 6. Key Implementation Details

### LocationRepository (GnssCallback)

The `LocationRepositoryImpl` uses two parallel data sources:

1. **LocationListener** — Provides speed (`location.speed` in m/s) and accuracy (`location.accuracy`)
2. **GnssStatus.Callback** — Counts satellites used in fix (`status.usedInFix(i)`)

Both sources feed into `onReadingUpdate` callbacks, ensuring speed and satellite count are always delivered together.

### Speed Display Formatting

Speed is displayed as `XXX.XX km/h` with:
- Integer part in large display font (120sp → 64sp in PiP)
- Decimal part in headline medium (40sp → 24sp in PiP)
- Monospace font for satellite/stats labels

### Session Reset Behavior

- Stats reset when app goes to background (`onStop`)
- New session starts fresh on `onStart`
- No persistence between app launches (privacy-focused)

---

## 7. Project Structure

```
app/src/main/java/com/example/gpsspeedometer/
├── MainActivity.kt                    # Activity + Compose UI
├── SpeedometerViewModel.kt           # State management
├── domain/
│   ├── model/
│   │   ├── GpsReading.kt             # GPS data model
│   │   ├── SpeedometerState.kt      # UI state model
│   │   ├── SessionConfig.kt         # Configuration model
│   │   └── SessionStatistics.kt     # Statistics model
│   ├── util/
│   │   └── SpeedConverter.kt        # Unit conversion
│   ├── GpsSignalFilter.kt           # GPS quality filter
│   ├── SessionStatisticsTracker.kt  # Session tracking
│   ├── TimeProvider.kt              # Time abstraction interface
│   └── time/
│       └── ProductionTimeProvider.kt # SystemClock implementation
├── data/
│   └── repository/
│       ├── LocationRepository.kt     # Repository interface
│       └── LocationRepositoryImpl.kt # Android GPS implementation
└── di/
    └── SpeedometerViewModelFactory.kt # Dependency injection
```

---

## 8. Design Decisions

| Decision | Rationale |
|----------|-----------|
| No internet permissions | Privacy-focused — no data leaves the device |
| Session-based stats | All data resets on background — no tracking |
| Monospace fonts | Consistent digit width for stable HUD display |
| Green/Red satellite indicator | Instant visual feedback on GPS quality |
| 5-second warmup | Prevents spurious max speed during GPS initialization |
| PiP mode | Allows speedometer overlay while using other apps |
| Watchdog timer | Graceful handling of GPS signal loss (tunnel scenarios) |
| Manual DI (no Hilt/Koin) | Minimal dependencies, single-purpose app |