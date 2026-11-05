package com.legitdev.speedometer.app.domain

import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SessionConfig
import org.junit.Test
import org.junit.Assert.*

class GpsSignalFilterTest {

    private class FakeTimeProvider(var now: Long = 1000L) : TimeProvider {
        override fun currentTimeMillis(): Long = now
    }
    
    private val config = SessionConfig(
        maxAccuracyMeters = 50f,
        minSpeedKmh = 1.5f
    )
    
    private val timeProvider = FakeTimeProvider()
    private val filter = GpsSignalFilter(config, timeProvider)
    
    @Test
    fun `acceptable signal is valid`() {
        val reading = GpsReading(
            speedMetersPerSecond = 10f,
            accuracyMeters = 30f,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertEquals(FixValidity.VALID, filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `null accuracy is valid`() {
        val reading = GpsReading(
            speedMetersPerSecond = 10f,
            accuracyMeters = null,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertEquals(FixValidity.VALID, filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `poor accuracy is invalid`() {
        val reading = GpsReading(
            speedMetersPerSecond = 10f,
            accuracyMeters = 60f,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertEquals(FixValidity.INVALID, filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `zero speed is valid`() {
        val reading = GpsReading(
            speedMetersPerSecond = 0f,
            accuracyMeters = 30f,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertEquals(FixValidity.VALID, filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `minimum acceptable speed is valid`() {
        val reading = GpsReading(
            speedMetersPerSecond = 0.42f,
            accuracyMeters = 30f,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertEquals(FixValidity.VALID, filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `maximum acceptable accuracy is valid`() {
        val reading = GpsReading(
            speedMetersPerSecond = 10f,
            accuracyMeters = 50f,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertEquals(FixValidity.VALID, filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `zero speed with many satellites is valid`() {
        val reading = GpsReading(
            speedMetersPerSecond = 0f,
            accuracyMeters = 30f,
            satelliteCount = 21,
            timestamp = 1000L
        )
        
        assertEquals(FixValidity.VALID, filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `movement with many satellites is valid`() {
        val reading = GpsReading(
            speedMetersPerSecond = 10f,
            accuracyMeters = 30f,
            satelliteCount = 21,
            timestamp = 1000L
        )
        
        assertEquals(FixValidity.VALID, filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `fix older than stale timeout is stale`() {
        timeProvider.now = 4001L
        val reading = GpsReading(
            speedMetersPerSecond = 10f,
            accuracyMeters = 30f,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertEquals(FixValidity.STALE, filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `fix at stale timeout is not stale`() {
        timeProvider.now = 4000L
        val reading = GpsReading(
            speedMetersPerSecond = 10f,
            accuracyMeters = 30f,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertEquals(FixValidity.VALID, filter.isSignalAcceptable(reading))
    }
    
    @Test
    fun `stale fix takes precedence over accuracy check`() {
        timeProvider.now = 4001L
        val reading = GpsReading(
            speedMetersPerSecond = 10f,
            accuracyMeters = 60f,
            satelliteCount = 5,
            timestamp = 1000L
        )
        
        assertEquals(FixValidity.STALE, filter.isSignalAcceptable(reading))
    }
}
