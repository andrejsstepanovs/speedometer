package com.example.gpsspeedometer.domain

import com.example.gpsspeedometer.domain.model.GpsReading
import com.example.gpsspeedometer.domain.model.SessionConfig
import org.junit.Test
import org.junit.Assert.*

class GpsSignalFilterTest {
    
    private val config = SessionConfig(
        maxAccuracyMeters = 50f,
        minSpeedKmh = 1.5f
    )
    
    private val filter = GpsSignalFilter(config)
    
    @Test
    fun `acceptable signal passes through`() {
        val reading = GpsReading(
            speedMetersPerSecond = 10f,
            accuracyMeters = 30f,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertTrue(filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `null accuracy is acceptable`() {
        val reading = GpsReading(
            speedMetersPerSecond = 10f,
            accuracyMeters = null,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertTrue(filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `poor accuracy is rejected`() {
        val reading = GpsReading(
            speedMetersPerSecond = 10f,
            accuracyMeters = 60f,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertFalse(filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `too slow speed is rejected`() {
        val reading = GpsReading(
            speedMetersPerSecond = 0.2f,
            accuracyMeters = 30f,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertFalse(filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `minimum acceptable speed passes`() {
        val reading = GpsReading(
            speedMetersPerSecond = 0.42f,
            accuracyMeters = 30f,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertTrue(filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `maximum acceptable accuracy passes`() {
        val reading = GpsReading(
            speedMetersPerSecond = 10f,
            accuracyMeters = 50f,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertTrue(filter.isSignalAcceptable(reading))
    }
}