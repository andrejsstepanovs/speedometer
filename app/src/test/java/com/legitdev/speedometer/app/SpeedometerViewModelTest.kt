package com.legitdev.speedometer.app

import com.legitdev.speedometer.app.domain.GpsSignalFilter
import com.legitdev.speedometer.app.domain.STALE_TIMEOUT_MS
import com.legitdev.speedometer.app.domain.SessionStatisticsTracker
import com.legitdev.speedometer.app.domain.TimeProvider
import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SessionConfig
import com.legitdev.speedometer.app.domain.model.SpeedUnit
import com.legitdev.speedometer.app.domain.model.TopSpeedStatus
import com.legitdev.speedometer.app.domain.repository.SettingsRepository
import com.legitdev.speedometer.app.domain.util.SpeedConverter
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

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedometerViewModelTest {

    private class FakeTimeProvider(var now: Long = 1000L) : TimeProvider {
        override fun currentTimeMillis(): Long = now
    }

    private class FakeSettingsRepository(
        var storedSpeedUnit: SpeedUnit = SpeedUnit.KMH,
        var storedPipTransparent: Boolean = false
    ) : SettingsRepository {
        val savedSpeedUnits = mutableListOf<SpeedUnit>()
        val savedPipValues = mutableListOf<Boolean>()

        override fun getSpeedUnit(): SpeedUnit = storedSpeedUnit

        override fun setSpeedUnit(unit: SpeedUnit) {
            storedSpeedUnit = unit
            savedSpeedUnits += unit
        }

        override fun isPipTransparent(): Boolean = storedPipTransparent

        override fun setPipTransparent(transparent: Boolean) {
            storedPipTransparent = transparent
            savedPipValues += transparent
        }
    }

    private val acceptingConfig = SessionConfig(
        warmupPeriodMillis = 0L,
        minSatellitesForTracking = 0,
        maxAccuracyMeters = Float.MAX_VALUE,
        minSpeedKmh = 0f,
        gpsSignalTimeoutMillis = 2000L
    )

    private val timeProvider = FakeTimeProvider()
    private val settingsRepository = FakeSettingsRepository()
    private val gpsSignalFilter = GpsSignalFilter(acceptingConfig, timeProvider)
    private val sessionTracker = SessionStatisticsTracker(acceptingConfig, timeProvider)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        SpeedometerViewModel(sessionTracker, gpsSignalFilter, settingsRepository, timeProvider)

    private fun readingAtKmh(speedKmh: Float) = GpsReading(
        speedMetersPerSecond = SpeedConverter.kmhToMetersPerSecond(speedKmh),
        accuracyMeters = 5f,
        satelliteCount = 3,
        timestamp = timeProvider.currentTimeMillis()
    )

    @Test
    fun `gps reading updates current speed`() {
        val viewModel = createViewModel()

        viewModel.onGpsReadingReceived(readingAtKmh(10f))

        assertEquals(10f, viewModel.state.currentSpeedKmh, 0.01f)
    }

    @Test
    fun `high altitude high speed fix reports 1026 kmh`() {
        val viewModel = createViewModel()

        val reading = GpsReading(
            speedMetersPerSecond = 285.0f,
            accuracyMeters = 5f,
            satelliteCount = 3,
            timestamp = timeProvider.currentTimeMillis()
        )

        viewModel.onGpsReadingReceived(reading)

        assertEquals(1026.0f, viewModel.state.currentSpeedKmh, 0.01f)
    }

    @Test
    fun `first valid gps reading updates current and max speed`() {
        val viewModel = createViewModel()

        viewModel.onGpsReadingReceived(readingAtKmh(10f))

        assertEquals(10f, viewModel.state.currentSpeedKmh, 0.01f)
        assertEquals(10f, viewModel.state.maxSpeedKmh, 0.01f)
    }

    @Test
    fun `speed resets to zero after stale timeout`() {
        val viewModel = createViewModel()

        viewModel.onGpsReadingReceived(readingAtKmh(10f))
        assertEquals(10f, viewModel.state.currentSpeedKmh, 0.01f)

        timeProvider.now += 2001L
        testDispatcher.scheduler.advanceTimeBy(2001L)
        testDispatcher.scheduler.runCurrent()

        assertEquals(0f, viewModel.state.currentSpeedKmh, 0.001f)
    }

    @Test
    fun `satellite count updates without changing speed`() {
        val viewModel = createViewModel()

        viewModel.onGpsReadingReceived(readingAtKmh(10f))
        viewModel.onSatelliteCountReceived(5)

        assertEquals(5, viewModel.state.satelliteCount)
        assertEquals(10f, viewModel.state.currentSpeedKmh, 0.01f)
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
    fun `new view model starts with empty state`() {
        val viewModelA = createViewModel()
        viewModelA.onGpsReadingReceived(readingAtKmh(10f))

        val viewModelB = createViewModel()

        assertEquals(0f, viewModelB.state.currentSpeedKmh, 0.001f)
        assertEquals(0f, viewModelB.state.maxSpeedKmh, 0.001f)
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
        val viewModel = createViewModel()

        viewModel.onPowerSaveModeChanged(true)
        viewModel.onGpsReadingReceived(readingAtKmh(10f))

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
        settingsRepository.storedPipTransparent = true

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

        viewModel.onPipBackgroundToggled()
        assertFalse(viewModel.isPipTransparent)

        assertEquals(listOf(true, false), settingsRepository.savedPipValues)
    }

    @Test
    fun `valid fix sets top speed status to valid`() {
        val viewModel = createViewModel()

        viewModel.onGpsReadingReceived(readingAtKmh(10f))

        assertEquals(10f, viewModel.state.maxSpeedKmh, 0.01f)
        assertEquals(TopSpeedStatus.VALID, viewModel.state.topSpeedStatus)
    }

    @Test
    fun `invalid fix clears display top speed and sets invalid status`() {
        val strictFilter = GpsSignalFilter(acceptingConfig.copy(maxAccuracyMeters = 10f), timeProvider)
        val viewModel = SpeedometerViewModel(sessionTracker, strictFilter, settingsRepository, timeProvider)

        viewModel.onGpsReadingReceived(readingAtKmh(10f))
        assertEquals(TopSpeedStatus.VALID, viewModel.state.topSpeedStatus)

        val invalidReading = GpsReading(
            speedMetersPerSecond = SpeedConverter.kmhToMetersPerSecond(50f),
            accuracyMeters = 50f,
            satelliteCount = 3,
            timestamp = timeProvider.currentTimeMillis()
        )
        viewModel.onGpsReadingReceived(invalidReading)

        assertEquals(0f, viewModel.state.maxSpeedKmh, 0.001f)
        assertEquals(TopSpeedStatus.INVALID, viewModel.state.topSpeedStatus)
    }

    @Test
    fun `stale fix clears display top speed and sets stale status`() {
        val viewModel = createViewModel()

        viewModel.onGpsReadingReceived(readingAtKmh(10f))
        assertEquals(TopSpeedStatus.VALID, viewModel.state.topSpeedStatus)

        val staleReading = GpsReading(
            speedMetersPerSecond = SpeedConverter.kmhToMetersPerSecond(50f),
            accuracyMeters = 5f,
            satelliteCount = 3,
            timestamp = timeProvider.currentTimeMillis() - STALE_TIMEOUT_MS - 1000L
        )
        viewModel.onGpsReadingReceived(staleReading)

        assertEquals(0f, viewModel.state.maxSpeedKmh, 0.001f)
        assertEquals(TopSpeedStatus.STALE, viewModel.state.topSpeedStatus)
    }

    @Test
    fun `stale timeout clears display top speed and sets stale status`() {
        val viewModel = createViewModel()

        viewModel.onGpsReadingReceived(readingAtKmh(10f))

        timeProvider.now += 2001L
        testDispatcher.scheduler.advanceTimeBy(2001L)
        testDispatcher.scheduler.runCurrent()

        assertEquals(0f, viewModel.state.maxSpeedKmh, 0.001f)
        assertEquals(TopSpeedStatus.STALE, viewModel.state.topSpeedStatus)
    }

    @Test
    fun `session reset clears top speed and sets invalid status`() {
        val viewModel = createViewModel()

        viewModel.onGpsReadingReceived(readingAtKmh(10f))
        assertEquals(TopSpeedStatus.VALID, viewModel.state.topSpeedStatus)

        viewModel.onSessionReset()

        assertEquals(0f, viewModel.state.maxSpeedKmh, 0.001f)
        assertEquals(TopSpeedStatus.INVALID, viewModel.state.topSpeedStatus)
    }
}
