package com.example.gpsspeedometer.domain

import com.example.gpsspeedometer.domain.model.GpsReading
import com.example.gpsspeedometer.domain.model.SessionConfig
import com.example.gpsspeedometer.domain.model.SessionStatistics
import com.example.gpsspeedometer.domain.util.SpeedConverter
import kotlin.math.max

class SessionStatisticsTracker(
    private val config: SessionConfig,
    private val timeProvider: TimeProvider
) {
    private var sessionStartTime: Long = 0L
    private var maxSpeedKmh: Float = 0f
    private var maxSatellites: Int = 0
    
    fun startSession() {
        sessionStartTime = timeProvider.currentTimeMillis()
        maxSpeedKmh = 0f
        maxSatellites = 0
    }
    
    fun update(reading: GpsReading): SessionStatistics {
        val currentSpeedKmh = SpeedConverter.metersPerSecondToKmh(reading.speedMetersPerSecond)
        
        maxSatellites = max(maxSatellites, reading.satelliteCount)
        
        val elapsed = timeProvider.currentTimeMillis() - sessionStartTime
        if (elapsed >= config.warmupPeriodMillis && 
            reading.satelliteCount >= config.minSatellitesForTracking) {
            maxSpeedKmh = max(maxSpeedKmh, currentSpeedKmh)
        }
        
        return SessionStatistics(
            currentSpeedKmh = currentSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            currentSatellites = reading.satelliteCount,
            maxSatellites = maxSatellites
        )
    }
    
    fun reset() {
        sessionStartTime = 0L
        maxSpeedKmh = 0f
        maxSatellites = 0
    }
}