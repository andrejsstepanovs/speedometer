package com.example.gpsspeedometer.domain.util

import org.junit.Test
import org.junit.Assert.assertEquals

class SpeedConverterTest {
    
    @Test
    fun `metersPerSecondToKmh converts correctly`() {
        assertEquals(36.0f, SpeedConverter.metersPerSecondToKmh(10f), 0.01f)
        assertEquals(0.0f, SpeedConverter.metersPerSecondToKmh(0f), 0.01f)
        assertEquals(18.0f, SpeedConverter.metersPerSecondToKmh(5f), 0.01f)
    }
    
    @Test
    fun `kmhToMetersPerSecond converts correctly`() {
        assertEquals(10.0f, SpeedConverter.kmhToMetersPerSecond(36f), 0.01f)
        assertEquals(0.0f, SpeedConverter.kmhToMetersPerSecond(0f), 0.01f)
        assertEquals(5.0f, SpeedConverter.kmhToMetersPerSecond(18f), 0.01f)
    }
    
    @Test
    fun `conversions are inverses`() {
        val originalSpeed = 15.5f
        val kmh = SpeedConverter.metersPerSecondToKmh(originalSpeed)
        val backToMps = SpeedConverter.kmhToMetersPerSecond(kmh)
        assertEquals(originalSpeed, backToMps, 0.01f)
    }
}