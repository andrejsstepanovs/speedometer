package com.legitdev.speedometer.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.legitdev.speedometer.app.domain.FixValidity
import com.legitdev.speedometer.app.domain.GpsSignalFilter
import com.legitdev.speedometer.app.domain.SessionStatisticsTracker
import com.legitdev.speedometer.app.domain.TimeProvider
import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SpeedUnit
import com.legitdev.speedometer.app.domain.model.SpeedometerState
import com.legitdev.speedometer.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SpeedometerViewModel(
    private val sessionTracker: SessionStatisticsTracker,
    private val gpsSignalFilter: GpsSignalFilter,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private var lastFixTimeMillis: Long = 0L
    private var staleTimerJob: Job? = null

    var state by mutableStateOf(SpeedometerState(0f, 0f, 0, 0, displayUnit = settingsRepository.getSpeedUnit()))
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    var isPowerSaveMode by mutableStateOf(false)
        private set

    var isPipTransparent by mutableStateOf(settingsRepository.isPipTransparent())
        private set

    fun onPowerSaveModeChanged(active: Boolean) {
        isPowerSaveMode = active
    }

    fun onPipBackgroundToggled() {
        val newValue = !isPipTransparent
        isPipTransparent = newValue
        settingsRepository.setPipTransparent(newValue)
    }

    fun onGpsReadingReceived(reading: GpsReading) {
        errorMessage = null

        lastFixTimeMillis = timeProvider.currentTimeMillis()
        staleTimerJob?.cancel()
        staleTimerJob = viewModelScope.launch {
            delay(STALE_TIMEOUT_MILLIS)
            state = state.copy(currentSpeedKmh = 0f)
        }

        if (gpsSignalFilter.isSignalAcceptable(reading) == FixValidity.VALID) {
            val stats = sessionTracker.update(reading)
            state = state.copy(
                currentSpeedKmh = stats.currentSpeedKmh,
                maxSpeedKmh = stats.maxSpeedKmh
            )
        } else {
            val sanitizedReading = reading.copy(speedMetersPerSecond = 0f)
            val stats = sessionTracker.update(sanitizedReading)

            state = state.copy(
                currentSpeedKmh = 0f,
                maxSpeedKmh = stats.maxSpeedKmh
            )
        }
    }

    fun onSatelliteCountReceived(count: Int) {
        state = state.copy(
            satelliteCount = count,
            maxSatelliteCount = maxOf(state.maxSatelliteCount, count)
        )
    }

    fun onSessionStart() {
        sessionTracker.startSession()
    }
    
    fun onSessionReset() {
        staleTimerJob?.cancel()
        staleTimerJob = null
        lastFixTimeMillis = 0L
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

    private companion object {
        const val STALE_TIMEOUT_MILLIS = 2000L
    }
}
