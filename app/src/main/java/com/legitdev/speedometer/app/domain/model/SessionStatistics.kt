package com.legitdev.speedometer.app.domain.model

/**
 * Status of the top speed value.
 */
enum class TopSpeedStatus {
    VALID,
    INVALID,
    STALE
}

data class SessionStatistics(
    val currentSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val currentSatellites: Int,
    val maxSatellites: Int,
    val topSpeedStatus: TopSpeedStatus = TopSpeedStatus.INVALID
)