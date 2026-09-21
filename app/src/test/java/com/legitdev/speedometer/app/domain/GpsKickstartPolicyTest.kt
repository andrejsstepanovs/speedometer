package com.legitdev.speedometer.app.domain

import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GpsKickstartPolicyTest {

    private val timeProvider = mock<TimeProvider>()
    private val policy = GpsKickstartPolicy(
        minSatellites = 3,
        intervalMillis = 5000L,
        timeProvider = timeProvider
    )

    @Test
    fun `kickstarts when satellite count below threshold`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(10000L)

        assertTrue(policy.shouldKickstart(2))
    }

    @Test
    fun `no kickstart when satellite count equals threshold`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(10000L)

        assertFalse(policy.shouldKickstart(3))
    }

    @Test
    fun `no kickstart when satellite count above threshold`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(10000L)

        assertFalse(policy.shouldKickstart(8))
    }

    @Test
    fun `throttles repeated kickstarts within interval`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(10000L, 12000L)

        assertTrue(policy.shouldKickstart(1))
        assertFalse(policy.shouldKickstart(1))
    }

    @Test
    fun `kickstarts again once interval elapsed`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(10000L, 16000L)

        assertTrue(policy.shouldKickstart(1))
        assertTrue(policy.shouldKickstart(1))
    }

    @Test
    fun `kickstarts when exactly interval elapsed`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(10000L, 15000L)

        assertTrue(policy.shouldKickstart(1))
        assertTrue(policy.shouldKickstart(1))
    }

    @Test
    fun `accepted satellite count does not consume throttle window`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(10000L, 16000L)

        assertTrue(policy.shouldKickstart(1))
        assertFalse(policy.shouldKickstart(5))
        assertTrue(policy.shouldKickstart(1))
    }

    @Test
    fun `reset clears throttle window`() {
        whenever(timeProvider.currentTimeMillis()).thenReturn(10000L, 11000L, 12000L)

        assertTrue(policy.shouldKickstart(1))
        assertFalse(policy.shouldKickstart(1))

        policy.reset()

        assertTrue(policy.shouldKickstart(1))
    }
}
