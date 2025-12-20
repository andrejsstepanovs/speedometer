package com.example.gpsspeedometer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.math.max

class SpeedometerViewModel : ViewModel() {

    // State is now safe inside the ViewModel
    var currentSpeedKmh by mutableFloatStateOf(0f)
    var maxSpeedKmh by mutableFloatStateOf(0f)
    var satelliteCount by mutableIntStateOf(0)
    var maxSatelliteCount by mutableIntStateOf(0)
    var errorMessage by mutableStateOf<String?>(null)

    // Logic variables
    var appStartTime = 0L
    var lastFixTime = 0L

    // Actions
    fun updateLocation(speedKmh: Float, sats: Int) {
        currentSpeedKmh = speedKmh
        satelliteCount = sats
        
        // Track max stats
        maxSatelliteCount = max(maxSatelliteCount, sats)
        
        // Max Speed Logic (Wait 5s)
        val timeElapsed = android.os.SystemClock.elapsedRealtime() - appStartTime
        if (timeElapsed > 5000 && sats >= 3) {
            if (speedKmh > maxSpeedKmh) {
                maxSpeedKmh = speedKmh
            }
        }
    }

    fun resetSession() {
        currentSpeedKmh = 0f
        maxSpeedKmh = 0f
        satelliteCount = 0
        maxSatelliteCount = 0
        errorMessage = null
        lastFixTime = 0L
        // We do NOT reset appStartTime here; we reset it when tracking starts
    }
}