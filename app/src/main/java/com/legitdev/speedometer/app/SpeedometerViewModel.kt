package com.legitdev.speedometer.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.legitdev.speedometer.app.domain.GpsSignalFilter
import com.legitdev.speedometer.app.domain.SessionStatisticsTracker
import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SpeedUnit
import com.legitdev.speedometer.app.domain.model.SpeedometerState
import com.legitdev.speedometer.app.domain.repository.SettingsRepository

class SpeedometerViewModel(
    private val sessionTracker: SessionStatisticsTracker,
    private val gpsSignalFilter: GpsSignalFilter,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    var state by mutableStateOf(SpeedometerState(0f, 0f, 0, 0, displayUnit = settingsRepository.getSpeedUnit()))
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    var isPowerSaveMode by mutableStateOf(false)
        private set
    
    fun onPowerSaveModeChanged(active: Boolean) {
        isPowerSaveMode = active
    }
    
    fun onGpsReadingReceived(reading: GpsReading) {
        errorMessage = null
        
        if (gpsSignalFilter.isSignalAcceptable(reading)) {
            val stats = sessionTracker.update(reading)
            state = state.copy(
                currentSpeedKmh = stats.currentSpeedKmh,
                maxSpeedKmh = stats.maxSpeedKmh,
                satelliteCount = stats.currentSatellites,
                maxSatelliteCount = stats.maxSatellites
            )
        } else {
            val sanitizedReading = reading.copy(speedMetersPerSecond = 0f)
            val stats = sessionTracker.update(sanitizedReading)
            
            state = state.copy(
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
        state = state.copy(currentSpeedKmh = 0f, maxSpeedKmh = 0f, satelliteCount = 0, maxSatelliteCount = 0)
        errorMessage = null
    }
    
    fun onError(message: String) {
        errorMessage = message
    }
    
    fun toggleUnit() {
        val newUnit = if (state.displayUnit == SpeedUnit.KMH) SpeedUnit.MPH else SpeedUnit.KMH
        settingsRepository.setSpeedUnit(newUnit)
        state = state.copy(displayUnit = newUnit)
    }
}