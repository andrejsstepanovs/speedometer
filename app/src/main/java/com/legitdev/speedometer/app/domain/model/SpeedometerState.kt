package com.legitdev.speedometer.app.domain.model

data class SpeedometerState(
    val currentSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val satelliteCount: Int,
    val maxSatelliteCount: Int
)