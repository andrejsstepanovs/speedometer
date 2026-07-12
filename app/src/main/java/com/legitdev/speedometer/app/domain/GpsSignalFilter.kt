package com.legitdev.speedometer.app.domain

import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SessionConfig
import com.legitdev.speedometer.app.domain.util.SpeedConverter

class GpsSignalFilter(private val config: SessionConfig) {
    fun isSignalAcceptable(reading: GpsReading): Boolean {
        return hasAcceptableAccuracy(reading) && hasAcceptableSpeed(reading)
    }
    
    private fun hasAcceptableAccuracy(reading: GpsReading): Boolean {
        return reading.accuracyMeters == null || reading.accuracyMeters <= config.maxAccuracyMeters
    }
    
    private fun hasAcceptableSpeed(reading: GpsReading): Boolean {
        val speedKmh = SpeedConverter.metersPerSecondToKmh(reading.speedMetersPerSecond)
        return speedKmh >= config.minSpeedKmh
    }
}