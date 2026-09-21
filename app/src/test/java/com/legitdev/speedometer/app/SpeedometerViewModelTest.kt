package com.legitdev.speedometer.app

import com.legitdev.speedometer.app.domain.GpsSignalFilter
import com.legitdev.speedometer.app.domain.SessionStatisticsTracker
import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SessionStatistics
import com.legitdev.speedometer.app.domain.model.SpeedUnit
import com.legitdev.speedometer.app.domain.repository.SettingsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SpeedometerViewModelTest {

    private val sessionTracker = mock<SessionStatisticsTracker>()
    private val gpsSignalFilter = mock<GpsSignalFilter>()
    private val settingsRepository = mock<SettingsRepository>()

    @Before
    fun setup() {
        whenever(settingsRepository.getSpeedUnit()).thenReturn(SpeedUnit.KMH)
    }

    private fun createViewModel() =
        SpeedometerViewModel(sessionTracker, gpsSignalFilter, settingsRepository)

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
        whenever(gpsSignalFilter.isSignalAcceptable(any())).thenReturn(true)
        whenever(sessionTracker.update(any())).thenReturn(
            SessionStatistics(
                currentSpeedKmh = 10f,
                maxSpeedKmh = 12f,
                currentSatellites = 3,
                maxSatellites = 5
            )
        )
        val viewModel = createViewModel()

        viewModel.onPowerSaveModeChanged(true)
        viewModel.onGpsReadingReceived(GpsReading(3f, 5f, 3, 1000L))

        assertTrue(viewModel.isPowerSaveMode)
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
