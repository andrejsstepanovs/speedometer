package com.legitdev.speedometer.app

import com.legitdev.speedometer.app.domain.GpsSignalFilter
import com.legitdev.speedometer.app.domain.SessionStatisticsTracker
import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SessionStatistics
import com.legitdev.speedometer.app.domain.model.SpeedUnit
import com.legitdev.speedometer.app.domain.repository.SettingsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SpeedometerViewModelTest {

    private val sessionTracker = mock<SessionStatisticsTracker>()
    private val gpsSignalFilter = mock<GpsSignalFilter>()
    private val settingsRepository = object : SettingsRepository {
        override fun getSpeedUnit() = SpeedUnit.KMH
        override fun setSpeedUnit(unit: SpeedUnit) {}
    }

    private val viewModel = SpeedometerViewModel(sessionTracker, gpsSignalFilter, settingsRepository)

    @Test
    fun `power save mode is disabled by default`() {
        assertFalse(viewModel.isPowerSaveMode)
    }

    @Test
    fun `power save mode can be enabled and disabled`() {
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

        viewModel.onPowerSaveModeChanged(true)
        viewModel.onGpsReadingReceived(GpsReading(3f, 5f, 3, 1000L))

        assertTrue(viewModel.isPowerSaveMode)
    }

    @Test
    fun `power save state survives session reset`() {
        viewModel.onPowerSaveModeChanged(true)

        viewModel.onSessionReset()

        assertTrue(viewModel.isPowerSaveMode)
    }
}
