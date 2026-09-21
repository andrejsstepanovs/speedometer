package com.legitdev.speedometer.app.domain.model

data class SessionConfig(
    val warmupPeriodMillis: Long = 5000L,
    val minSatellitesForTracking: Int = MIN_SATELLITES_FOR_LOCK,
    val maxAccuracyMeters: Float = 50f,
    val minSpeedKmh: Float = 1.5f,
    val gpsSignalTimeoutMillis: Long = 2000L
) {
    companion object {
        const val MIN_SATELLITES_FOR_LOCK = 3
    }
}