package com.example.gpsspeedometer.domain.model

data class GpsReading(
    val speedMetersPerSecond: Float,
    val accuracyMeters: Float?,
    val satelliteCount: Int,
    val timestamp: Long
)