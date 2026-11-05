package com.legitdev.speedometer.app.domain

import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SessionConfig

/**
 * Validity of a GPS fix.
 */
enum class FixValidity {
    VALID,
    INVALID,
    STALE
}

/**
 * Maximum age of a GPS fix before it is considered stale.
 */
const val STALE_TIMEOUT_MS = 3000L

/**
 * Validates GPS readings before they are used for tracking.
 *
 * The filter checks the age of the fix and its accuracy. A fix older than
 * [STALE_TIMEOUT_MS] is reported as [FixValidity.STALE]. Otherwise, the filter
 * rejects a reading when the reported accuracy is worse than
 * [SessionConfig.maxAccuracyMeters]. Speed is not checked, so a reading with
 * 0 speed is acceptable.
 */
class GpsSignalFilter(
    private val config: SessionConfig,
    private val timeProvider: TimeProvider
) {
    fun isSignalAcceptable(reading: GpsReading): FixValidity {
        val ageMillis = timeProvider.currentTimeMillis() - reading.timestamp
        if (ageMillis > STALE_TIMEOUT_MS) {
            return FixValidity.STALE
        }

        return if (hasAcceptableAccuracy(reading)) {
            FixValidity.VALID
        } else {
            FixValidity.INVALID
        }
    }
    
    private fun hasAcceptableAccuracy(reading: GpsReading): Boolean {
        return reading.accuracyMeters == null || reading.accuracyMeters <= config.maxAccuracyMeters
    }
}
