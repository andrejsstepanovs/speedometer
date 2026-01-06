package com.example.gpsspeedometer.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.os.SystemClock
import com.example.gpsspeedometer.data.ProductionTimeProvider
import com.example.gpsspeedometer.domain.TimeProvider
import com.example.gpsspeedometer.domain.model.GpsReading

class LocationRepositoryImpl(
    private val context: Context,
    private val timeProvider: TimeProvider = ProductionTimeProvider()
) : LocationRepository {
    
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    private var onReadingUpdate: ((GpsReading) -> Unit)? = null
    private var onGpsError: ((String) -> Unit)? = null
    private var currentSatelliteCount = 0
    private var lastLocation: Location? = null
    
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
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locationManager.registerGnssStatusCallback(gnssCallback, null)
            }
        } catch (e: Exception) {
            onGpsError("Error starting GPS: ${e.message}")
        }
    }
    
    override suspend fun stopLocationUpdates() {
        locationManager.removeUpdates(locationListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                locationManager.unregisterGnssStatusCallback(gnssCallback)
            } catch (e: IllegalArgumentException) {
            }
        }
        onReadingUpdate = null
        onGpsError = null
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
}