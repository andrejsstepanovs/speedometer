package com.example.gpsspeedometer.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.gpsspeedometer.SpeedometerViewModel
import com.example.gpsspeedometer.domain.GpsSignalFilter
import com.example.gpsspeedometer.domain.SessionStatisticsTracker
import com.example.gpsspeedometer.domain.TimeProvider
import com.example.gpsspeedometer.domain.model.SessionConfig
import com.example.gpsspeedometer.domain.time.ProductionTimeProvider
import android.os.SystemClock

class SpeedometerViewModelFactory : ViewModelProvider.Factory {
    
    companion object {
        val INSTANCE = SpeedometerViewModelFactory()
    }
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpeedometerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return createSpeedometerViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
    
    private fun createSpeedometerViewModel(): SpeedometerViewModel {
        val config = SessionConfig()
        val timeProvider = ProductionTimeProvider()
        val sessionTracker = SessionStatisticsTracker(config, timeProvider)
        val gpsSignalFilter = GpsSignalFilter(config)
        
        return SpeedometerViewModel(sessionTracker, gpsSignalFilter)
    }
}