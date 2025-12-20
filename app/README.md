# GPS Speedometer

A minimalist Android application built with Kotlin and Jetpack Compose. Displays real-time GPS speed, satellite connection status, and conditional max speed tracking.

## Features

* **Real-time Speed:** Centered display in km/h.
* **Satellite Status:** GNSS satellite count (used in fix) shown in top-left.
* **Max Speed Tracking:** Shown in bottom-left.
    * *Logic:* Updates only if `Uptime > 5 seconds` AND `Satellites >= 3`.
* **UI:** High contrast (White text on Black background).

## Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material3)
* **API:** Android `LocationManager` & `GnssStatus` (Raw GPS access)
* **Min SDK:** 24 (Required for `GnssStatus`)
* **Target SDK:** 34

## Permissions

* `ACCESS_FINE_LOCATION`: Required for GPS provider speed and GNSS status.
* `ACCESS_COARSE_LOCATION`: Legacy requirement for compatibility.

## Build & Install (CLI)

Assumes `JAVA_HOME` and `ANDROID_HOME` are set.

1.  **Clone**
    ```bash
    git clone <repo_url>
    cd gps-speedometer
    ```

2.  **Build Debug APK**
    ```bash
    ./gradlew assembleDebug
    ```

3.  **Install to connected device**
    ```bash
    ./gradlew installDebug
    ```

4.  **Logcat Monitoring**
    To view logs or debug output:
    ```bash
    adb logcat -s "MainActivity" "*:S"
    ```

## Logic Reference

The core logic for the max speed filter is located in `MainActivity.kt`:

```kotlin
val timeElapsed = SystemClock.elapsedRealtime() - appStartTime
if (timeElapsed > 5000 && satelliteCount >= 3) {
    if (speedKmh > maxSpeedKmh) {
        maxSpeedKmh = speedKmh
    }
}