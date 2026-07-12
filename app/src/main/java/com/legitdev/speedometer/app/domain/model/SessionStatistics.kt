package com.legitdev.speedometer.app.domain.model

data class SessionStatistics(
    val currentSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val currentSatellites: Int,
    val maxSatellites: Int
)