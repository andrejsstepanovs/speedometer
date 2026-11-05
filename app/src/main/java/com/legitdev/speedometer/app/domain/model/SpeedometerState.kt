package com.legitdev.speedometer.app.domain.model

data class SpeedometerState(
    val currentSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val satelliteCount: Int = 0,
    val maxSatelliteCount: Int = 0,
    val displayUnit: SpeedUnit = SpeedUnit.KMH,
    val topSpeedStatus: TopSpeedStatus = TopSpeedStatus.INVALID
)