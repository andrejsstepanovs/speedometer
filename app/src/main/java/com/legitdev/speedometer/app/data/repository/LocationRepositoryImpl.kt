package com.legitdev.speedometer.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.legitdev.speedometer.app.domain.time.ProductionTimeProvider
import com.legitdev.speedometer.app.domain.TimeProvider
import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SessionConfig

class LocationRepositoryImpl(
    private val context: Context,
    private val timeProvider: TimeProvider = ProductionTimeProvider()
) : LocationRepository {
    
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    private var onReadingUpdate: ((GpsReading) -> Unit)? = null
    private var onGpsError: ((String) -> Unit)? = null
    private var currentSatelliteCount = 0
    private var lastLocation: Location? = null
    private var lastKickstartAt = 0L
    
    override suspend fun startLocationUpdates(
        onReadingUpdate: (GpsReading) -> Unit,
        onGpsError: ((String?) -> Unit)
    ) {
        this.onReadingUpdate = onReadingUpdate
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
        onReadingUpdate = null
        onGpsError = null
        lastKickstartAt = 0L
    }
    
    private fun kickstartGpsAcquisition() {
        val now = timeProvider.currentTimeMillis()
        if (now - lastKickstartAt < KICKSTART_INTERVAL_MILLIS) return
        lastKickstartAt = now

        for (command in KICKSTART_COMMANDS) {
            try {
                locationManager.sendExtraCommand(LocationManager.GPS_PROVIDER, command, null)
            } catch (_: Exception) {
            }
        }
    }
    
    private fun createGpsReading(location: Location): GpsReading {
        return GpsReading(
            speedMetersPerSecond = location.speed,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            satelliteCount = currentSatelliteCount,
            timestamp = timeProvider.currentTimeMillis()
        )
    }
    
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            val reading = createGpsReading(location)
            onReadingUpdate?.invoke(reading)
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

            if (count < SessionConfig.MIN_SATELLITES_FOR_LOCK) {
                kickstartGpsAcquisition()
            }
            
            val location = lastLocation
            if (location != null) {
                val reading = createGpsReading(location)
                onReadingUpdate?.invoke(reading)
            } else {
                val reading = GpsReading(
                    speedMetersPerSecond = 0f,
                    accuracyMeters = null,
                    satelliteCount = currentSatelliteCount,
                    timestamp = timeProvider.currentTimeMillis()
                )
                onReadingUpdate?.invoke(reading)
            }
        }
    }
    
    private companion object {
        const val KICKSTART_INTERVAL_MILLIS = 5000L
        val KICKSTART_COMMANDS = listOf("force_time_injection", "force_xtra_injection")
    }
}