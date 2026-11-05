package com.legitdev.speedometer.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.legitdev.speedometer.app.domain.time.ProductionTimeProvider
import com.legitdev.speedometer.app.domain.GpsKickstartPolicy
import com.legitdev.speedometer.app.domain.TimeProvider
import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SessionConfig

class LocationRepositoryImpl(
    private val context: Context,
    private val timeProvider: TimeProvider = ProductionTimeProvider()
) : LocationRepository {
    
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    private var onLocationFix: ((GpsReading) -> Unit)? = null
    private var onSatelliteCount: ((Int) -> Unit)? = null
    private var onGpsError: ((String?) -> Unit)? = null
    private var currentSatelliteCount = 0
    private var lastLocation: Location? = null
    private val kickstartPolicy = GpsKickstartPolicy(
        minSatellites = SessionConfig.MIN_SATELLITES_FOR_LOCK,
        intervalMillis = KICKSTART_INTERVAL_MILLIS,
        timeProvider = timeProvider
    )
    
    override suspend fun startLocationUpdates(
        onLocationFix: (GpsReading) -> Unit,
        onSatelliteCount: (Int) -> Unit,
        onGpsError: (String?) -> Unit
    ) {
        this.onLocationFix = onLocationFix
        this.onSatelliteCount = onSatelliteCount
        this.onGpsError = onGpsError
        
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    locationListener
                )
            }
            
            locationManager.registerGnssStatusCallback(gnssCallback, null)
        } catch (e: Exception) {
            onGpsError("Error starting GPS: ${e.message}")
        }
    }
    
    override suspend fun stopLocationUpdates() {
        locationManager.removeUpdates(locationListener)
        try {
            locationManager.unregisterGnssStatusCallback(gnssCallback)
        } catch (_: IllegalArgumentException) {
        }
        onLocationFix = null
        onSatelliteCount = null
        onGpsError = null
        kickstartPolicy.reset()
    }

    private fun kickstartGpsAcquisition() {
        for (command in KICKSTART_COMMANDS) {
            try {
                locationManager.sendExtraCommand(LocationManager.GPS_PROVIDER, command, null)
            } catch (_: Exception) {
            }
        }
    }
    
    /**
     * Creates a [GpsReading] from the given [location].
     *
     * The speed source is `Location.getSpeed()` when available. Otherwise, the speed is computed
     * from the positions of [location] and [previousLocation].
     */
    private fun createGpsReading(location: Location, previousLocation: Location?): GpsReading {
        val speedMetersPerSecond = if (location.hasSpeed()) {
            location.speed
        } else {
            computeSpeedFromPositions(location, previousLocation)
        }
        return GpsReading(
            speedMetersPerSecond = speedMetersPerSecond,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            satelliteCount = currentSatelliteCount,
            timestamp = SystemClock.elapsedRealtime()
        )
    }

    private fun computeSpeedFromPositions(location: Location, previousLocation: Location?): Float {
        if (previousLocation == null) {
            return 0f
        }
        val elapsedMillis = location.time - previousLocation.time
        if (elapsedMillis == 0L) {
            return 0f
        }
        val distanceMeters = previousLocation.distanceTo(location)
        return distanceMeters / (elapsedMillis / 1000f)
    }
    
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val previousLocation = lastLocation
            lastLocation = location
            val reading = createGpsReading(location, previousLocation)
            onLocationFix?.invoke(reading)
        }
        
        override fun onProviderEnabled(provider: String) {}
        
        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                onGpsError?.invoke("GPS Provider is disabled.")
            }
        }
        
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }
    
    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var count = 0
            val total = status.satelliteCount
            for (i in 0 until total) {
                if (status.usedInFix(i)) {
                    count++
                }
            }
            currentSatelliteCount = count
            onSatelliteCount?.invoke(count)

            if (kickstartPolicy.shouldKickstart(count)) {
                kickstartGpsAcquisition()
            }
        }
    }
    
    private companion object {
        const val KICKSTART_INTERVAL_MILLIS = 5000L
        val KICKSTART_COMMANDS = listOf("force_time_injection", "force_xtra_injection")
    }
}