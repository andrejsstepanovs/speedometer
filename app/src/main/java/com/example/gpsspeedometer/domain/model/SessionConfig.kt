package com.example.gpsspeedometer.domain.model

data class SessionConfig(
    val warmupPeriodMillis: Long = 5000L,
    val minSatellitesForTracking: Int = 3,
    val maxAccuracyMeters: Float = 50f,
    val minSpeedKmh: Float = 1.5f,
    val gpsSignalTimeoutMillis: Long = 2000L
)