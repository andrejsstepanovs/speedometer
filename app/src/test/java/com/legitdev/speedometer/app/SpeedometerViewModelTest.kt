package com.legitdev.speedometer.app

import com.legitdev.speedometer.app.domain.GpsSignalFilter
import com.legitdev.speedometer.app.domain.SessionStatisticsTracker
import com.legitdev.speedometer.app.domain.TimeProvider
import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SessionStatistics
import com.legitdev.speedometer.app.domain.model.SpeedUnit
import com.legitdev.speedometer.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedometerViewModelTest {

    private val sessionTracker = mock<SessionStatisticsTracker>()
    private val gpsSignalFilter = mock<GpsSignalFilter>()
    private val settingsRepository = mock<SettingsRepository>()
    private val timeProvider = mock<TimeProvider>()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(settingsRepository.getSpeedUnit()).thenReturn(SpeedUnit.KMH)
        whenever(timeProvider.currentTimeMillis()).thenReturn(1000L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        SpeedometerViewModel(sessionTracker, gpsSignalFilter, settingsRepository, timeProvider)

    private fun stubAcceptedReading() {
        whenever(gpsSignalFilter.isSignalAcceptable(any())).thenReturn(true)
        whenever(sessionTracker.update(any())).thenReturn(
            SessionStatistics(
                currentSpeedKmh = 10f,
                maxSpeedKmh = 12f,
                currentSatellites = 3,
                maxSatellites = 5
            )
        )
    }

    @Test
    fun `power save mode is disabled by default`() {
        val viewModel = createViewModel()

        assertFalse(viewModel.isPowerSaveMode)
    }

    @Test
    fun `power save mode can be enabled and disabled`() {
        val viewModel = createViewModel()

        viewModel.onPowerSaveModeChanged(true)
        assertTrue(viewModel.isPowerSaveMode)

        viewModel.onPowerSaveModeChanged(false)
        assertFalse(viewModel.isPowerSaveMode)
    }

    @Test
    fun `power save state survives gps reading updates`() {
        stubAcceptedReading()
        val viewModel = createViewModel()

        viewModel.onPowerSaveModeChanged(true)
        viewModel.onGpsReadingReceived(GpsReading(3f, 5f, 3, 1000L))

        assertTrue(viewModel.isPowerSaveMode)
    }

    @Test
    fun `valid reading shows non-zero speed`() {
        stubAcceptedReading()
        val viewModel = createViewModel()

        viewModel.onGpsReadingReceived(GpsReading(3f, 5f, 3, 1000L))

        assertTrue(viewModel.state.currentSpeedKmh > 0f)
        assertEquals(10f, viewModel.state.currentSpeedKmh, 0.001f)
    }

    @Test
    fun `initial state is zero`() {
        val viewModel = createViewModel()

        assertEquals(0f, viewModel.state.currentSpeedKmh, 0.001f)
        assertEquals(0f, viewModel.state.maxSpeedKmh, 0.001f)
        assertEquals(0, viewModel.state.satelliteCount)
        assertEquals(0, viewModel.state.maxSatelliteCount)
    }

    @Test
    fun `speed resets to zero after stale timeout`() {
        stubAcceptedReading()
        val viewModel = createViewModel()

        viewModel.onGpsReadingReceived(GpsReading(3f, 5f, 3, 1000L))
        assertEquals(10f, viewModel.state.currentSpeedKmh, 0.001f)

        testDispatcher.scheduler.advanceTimeBy(2001L)
        testDispatcher.scheduler.runCurrent()

        assertEquals(0f, viewModel.state.currentSpeedKmh, 0.001f)
    }

    @Test
    fun `satellite count updates without changing speed`() {
        stubAcceptedReading()
        val viewModel = createViewModel()

        viewModel.onGpsReadingReceived(GpsReading(3f, 5f, 3, 1000L))
        viewModel.onSatelliteCountReceived(7)

        assertEquals(7, viewModel.state.satelliteCount)
        assertEquals(7, viewModel.state.maxSatelliteCount)
        assertEquals(10f, viewModel.state.currentSpeedKmh, 0.001f)
    }

    @Test
    fun `power save state survives session reset`() {
        val viewModel = createViewModel()

        viewModel.onPowerSaveModeChanged(true)
        viewModel.onSessionReset()

        assertTrue(viewModel.isPowerSaveMode)
    }

    @Test
    fun `pip transparency is loaded from settings on init`() {
        whenever(settingsRepository.isPipTransparent()).thenReturn(true)

        val viewModel = createViewModel()

        assertTrue(viewModel.isPipTransparent)
    }

    @Test
    fun `pip transparency defaults to opaque when unset`() {
        val viewModel = createViewModel()

        assertFalse(viewModel.isPipTransparent)
    }

    @Test
    fun `toggling pip transparency persists inverted value`() {
        val viewModel = createViewModel()

        viewModel.onPipBackgroundToggled()
        assertTrue(viewModel.isPipTransparent)
        verify(settingsRepository).setPipTransparent(true)

        viewModel.onPipBackgroundToggled()
        assertFalse(viewModel.isPipTransparent)
        verify(settingsRepository).setPipTransparent(false)
    }
}
