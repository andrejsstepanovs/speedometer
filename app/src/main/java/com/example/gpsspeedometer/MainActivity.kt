package com.example.gpsspeedometer

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
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import kotlin.math.max

class MainActivity : ComponentActivity() {

    private lateinit var locationManager: LocationManager
    
    // State variables
    private var currentSpeedKmh by mutableFloatStateOf(0f)
    private var maxSpeedKmh by mutableFloatStateOf(0f)
    private var satelliteCount by mutableIntStateOf(0)
    private var maxSatelliteCount by mutableIntStateOf(0)
    
    // Logic control
    private var appStartTime = 0L

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startTracking()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        appStartTime = SystemClock.elapsedRealtime()

        checkPermissionsAndStart()

        setContent {
            SpeedometerScreen(
                currentSpeed = currentSpeedKmh,
                maxSpeed = maxSpeedKmh,
                satellites = satelliteCount,
                topSatellites = maxSatelliteCount
            )
        }
    }

    private fun checkPermissionsAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            startTracking()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0L, 
            0f, 
            locationListener
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locationManager.registerGnssStatusCallback(gnssCallback, null)
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
            currentSpeedKmh = speedKmh

            // Max Speed Logic: > 5 seconds uptime AND >= 3 satellites
            val timeElapsed = SystemClock.elapsedRealtime() - appStartTime
            if (timeElapsed > 5000 && satelliteCount >= 3) {
                if (speedKmh > maxSpeedKmh) {
                    maxSpeedKmh = speedKmh
                }
            }
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
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
            satelliteCount = count
            // Track max satellites immediately (no 5s delay needed for signal quality stats)
            maxSatelliteCount = max(maxSatelliteCount, count)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locationManager.unregisterGnssStatusCallback(gnssCallback)
        }
    }
}

@Composable
fun SpeedometerScreen(
    currentSpeed: Float, 
    maxSpeed: Float, 
    satellites: Int,
    topSatellites: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Top Left: Satellite Count
        Text(
            text = "satellites: $satellites",
            color = Color.White,
            fontSize = 24.sp,
            modifier = Modifier.align(Alignment.TopStart)
        )

        // Center: Current Speed + Unit
        // Using a Row to align the number and unit nicely
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.Bottom // Aligns the text to the bottom baseline
        ) {
            Text(
                text = "%.0f".format(currentSpeed),
                color = Color.White,
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                // Fix for font padding issues on large text
                lineHeight = 96.sp 
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "km/h",
                color = Color.Gray,
                fontSize = 32.sp, // 96 / 3 = 32
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(bottom = 12.dp) // Slight lift to align with baseline visual
            )
        }

        // Bottom Left: Stats Column
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                text = "top speed: %.1f".format(maxSpeed),
                color = Color.White,
                fontSize = 24.sp
            )
            Text(
                text = "top satellites: $topSatellites",
                color = Color.White,
                fontSize = 24.sp
            )
        }
    }
}
