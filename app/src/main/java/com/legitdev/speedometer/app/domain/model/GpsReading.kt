package com.legitdev.speedometer.app.domain.model

data class GpsReading(
    /**
     * Speed in meters per second.
     */
    val speedMetersPerSecond: Float,
    val accuracyMeters: Float?,
    val satelliteCount: Int,
    val timestamp: Long
)