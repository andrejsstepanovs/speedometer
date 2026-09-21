package com.legitdev.speedometer.app.domain

class GpsKickstartPolicy(
    private val minSatellites: Int,
    private val intervalMillis: Long,
    private val timeProvider: TimeProvider
) {
    private var lastKickstartAt: Long = 0L

    fun shouldKickstart(satelliteCount: Int): Boolean {
        if (satelliteCount >= minSatellites) return false

        val now = timeProvider.currentTimeMillis()
        if (now - lastKickstartAt < intervalMillis) return false

        lastKickstartAt = now
        return true
    }

    fun reset() {
        lastKickstartAt = 0L
    }
}
