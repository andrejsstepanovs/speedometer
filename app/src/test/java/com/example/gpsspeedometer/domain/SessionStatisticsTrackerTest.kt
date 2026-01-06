package com.example.gpsspeedometer.domain

import com.example.gpsspeedometer.domain.model.GpsReading
import com.example.gpsspeedometer.domain.model.SessionConfig
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SessionStatisticsTrackerTest {
    
    private val config = SessionConfig(
        warmupPeriodMillis = 5000L,
        minSatellitesForTracking = 3
    )
    
    private val timeProvider = mock<TimeProvider>()
    private val tracker = SessionStatisticsTracker(config, timeProvider)
    
    @Test
    fun `startSession initializes correctly`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(1000L)
        
        tracker.startSession()
        
        val reading = GpsReading(10f, 30f, 5, 1000L)
        val stats = tracker.update(reading)
        
        assertEquals(36.0f, stats.currentSpeedKmh, 0.01f)
        assertEquals(5, stats.currentSatellites)
        assertEquals(5, stats.maxSatellites)
        assertEquals(0f, stats.maxSpeedKmh, 0.01f)
    }
    
    @Test
    fun `maxSpeed is ignored during warmup period`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(0L, 1000L)
        
        tracker.startSession()
        
        val reading = GpsReading(20f, 30f, 5, 1000L)
        val stats = tracker.update(reading)
        
        assertEquals(72.0f, stats.currentSpeedKmh, 0.01f)
        assertEquals(0f, stats.maxSpeedKmh, 0.01f)
    }
    
    @Test
    fun `maxSpeed is tracked after warmup`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(0L, 6000L)
        
        tracker.startSession()
        
        val reading = GpsReading(20f, 30f, 5, 6000L)
        val stats = tracker.update(reading)
        
        assertEquals(72.0f, stats.currentSpeedKmh, 0.01f)
        assertEquals(72.0f, stats.maxSpeedKmh, 0.01f)
    }
    
    @Test
    fun `maxSpeed requires minimum satellites`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(0L, 6000L)
        
        tracker.startSession()
        
        val reading = GpsReading(20f, 30f, 2, 6000L)
        val stats = tracker.update(reading)
        
        assertEquals(72.0f, stats.currentSpeedKmh, 0.01f)
        assertEquals(0f, stats.maxSpeedKmh, 0.01f)
    }
    
    @Test
    fun `maxSatellites always tracked`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(0L, 1000L)
        
        tracker.startSession()
        
        val reading1 = GpsReading(10f, 30f, 3, 1000L)
        tracker.update(reading1)
        
        val reading2 = GpsReading(10f, 30f, 7, 2000L)
        val stats2 = tracker.update(reading2)
        
        assertEquals(7, stats2.maxSatellites)
        
        val reading3 = GpsReading(10f, 30f, 4, 3000L)
        val stats3 = tracker.update(reading3)
        
        assertEquals(7, stats3.maxSatellites)
    }
    
    @Test
    fun `reset clears all statistics`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(0L, 6000L)
        
        tracker.startSession()
        
        val reading = GpsReading(20f, 30f, 5, 6000L)
        tracker.update(reading)
        
        tracker.reset()
        
        val resetReading = GpsReading(5f, 30f, 2, 7000L)
        val stats = tracker.update(resetReading)
        
        assertEquals(0f, stats.maxSpeedKmh, 0.01f)
        assertEquals(2, stats.maxSatellites)
    }
    
    @Test
    fun `currentSpeed always calculated`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(0L, 1000L)
        
        tracker.startSession()
        
        val reading = GpsReading(15f, 30f, 3, 1000L)
        val stats = tracker.update(reading)
        
        assertEquals(54.0f, stats.currentSpeedKmh, 0.01f)
    }
}