package com.legitdev.speedometer.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.legitdev.speedometer.app.domain.GpsSignalFilter
import com.legitdev.speedometer.app.domain.SessionStatisticsTracker
import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SessionConfig
import com.legitdev.speedometer.app.domain.model.SpeedometerState

class SpeedometerViewModel(
    private val sessionTracker: SessionStatisticsTracker,
    private val gpsSignalFilter: GpsSignalFilter
) : ViewModel() {
    
    var state by mutableStateOf(SpeedometerState(0f, 0f, 0, 0))
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    fun onGpsReadingReceived(reading: GpsReading) {
        errorMessage = null
        
        if (gpsSignalFilter.isSignalAcceptable(reading)) {
            val stats = sessionTracker.update(reading)
            state = SpeedometerState(
                currentSpeedKmh = stats.currentSpeedKmh,
                maxSpeedKmh = stats.maxSpeedKmh,
                satelliteCount = stats.currentSatellites,
                maxSatelliteCount = stats.maxSatellites
            )
        } else {
            val sanitizedReading = reading.copy(speedMetersPerSecond = 0f)
            val stats = sessionTracker.update(sanitizedReading)
            
            state = SpeedometerState(
                currentSpeedKmh = 0f,
                maxSpeedKmh = stats.maxSpeedKmh,
                satelliteCount = stats.currentSatellites,
                maxSatelliteCount = stats.maxSatellites
            )
        }
    }
    
    fun onSessionStart() {
        sessionTracker.startSession()
    }
    
    fun onSessionReset() {
        sessionTracker.reset()
        state = SpeedometerState(0f, 0f, 0, 0)
        errorMessage = null
    }
    
    fun onError(message: String) {
        errorMessage = message
    }
}