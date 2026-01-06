package com.example.gpsspeedometer.domain.model

data class SpeedometerState(
    val currentSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val satelliteCount: Int,
    val maxSatelliteCount: Int
)