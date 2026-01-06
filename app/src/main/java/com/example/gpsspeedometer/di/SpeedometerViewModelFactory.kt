package com.example.gpsspeedometer.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.gpsspeedometer.SpeedometerViewModel
import com.example.gpsspeedometer.data.ProductionTimeProvider
import com.example.gpsspeedometer.data.repository.LocationRepositoryImpl
import com.example.gpsspeedometer.domain.GpsSignalFilter
import com.example.gpsspeedometer.domain.SessionStatisticsTracker
import com.example.gpsspeedometer.domain.TimeProvider
import com.example.gpsspeedometer.domain.model.SessionConfig

class SpeedometerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpeedometerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return createSpeedometerViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
    
    private fun createSpeedometerViewModel(): SpeedometerViewModel {
        val config = SessionConfig()
        val timeProvider: TimeProvider = ProductionTimeProvider()
        val sessionTracker = SessionStatisticsTracker(config, timeProvider)
        val gpsSignalFilter = GpsSignalFilter(config)
        
        return SpeedometerViewModel(sessionTracker, gpsSignalFilter)
    }
}