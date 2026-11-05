package com.legitdev.speedometer.app.domain

import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SessionConfig

/**
 * Validates GPS readings before they are used for tracking.
 *
 * The filter checks accuracy only. It rejects a reading when the reported
 * accuracy is worse than [SessionConfig.maxAccuracyMeters]. Speed is not
 * checked, so a reading with 0 speed is acceptable.
 */
class GpsSignalFilter(private val config: SessionConfig) {
    fun isSignalAcceptable(reading: GpsReading): Boolean {
        return hasAcceptableAccuracy(reading)
    }
    
    private fun hasAcceptableAccuracy(reading: GpsReading): Boolean {
        return reading.accuracyMeters == null || reading.accuracyMeters <= config.maxAccuracyMeters
    }
}
