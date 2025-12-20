package com.example.gpsspeedometer

import java.util.Locale
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
import androidx.activity.viewModels // Required for ViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var locationManager: LocationManager
    
    // The ViewModel holds our data now
    private val viewModel: SpeedometerViewModel by viewModels()
    
    private var watchdogJob: Job? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocation) {
            viewModel.errorMessage = null
            startTracking()
        } else {
            viewModel.errorMessage = if (coarseLocation) {
                "Precise Location required for GPS speed accuracy.\nPlease allow 'Precise' in settings."
            } else {
                "Location permission denied.\nApp requires GPS access to function."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // Initial permission check (just UI logic, not starting GPS)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        setContent {
            SpeedometerScreen(
                currentSpeed = viewModel.currentSpeedKmh,
                maxSpeed = viewModel.maxSpeedKmh,
                satellites = viewModel.satelliteCount,
                topSatellites = viewModel.maxSatelliteCount,
                error = viewModel.errorMessage
            )
        }
    }

    override fun onStart() {
        super.onStart()
        checkPermissionsAndStart()
        startWatchdog()
    }

    override fun onStop() {
        super.onStop()
        // 1. Always stop hardware to save battery
        stopGpsHardware()
        stopWatchdog()

        // 2. ONLY wipe data if we are NOT rotating
        if (!isChangingConfigurations) {
            viewModel.resetSession()
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                val timeSinceLastFix = SystemClock.elapsedRealtime() - viewModel.lastFixTime
                // Tunnel Detection: If no data for > 2s, force speed to 0
                if (viewModel.lastFixTime > 0 && timeSinceLastFix > 2000 && viewModel.currentSpeedKmh > 0) {
                    viewModel.currentSpeedKmh = 0f
                }
            }
        }
    }

    private fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    private fun checkPermissionsAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            startTracking()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
             return
        }

        // Only reset start time if this is a "fresh" start (not a rotation)
        // If speed is 0 and max is 0, we assume it's a new session
        if (viewModel.appStartTime == 0L || viewModel.maxSpeedKmh == 0f) {
            viewModel.appStartTime = SystemClock.elapsedRealtime()
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L, 
                0f, 
                locationListener
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locationManager.registerGnssStatusCallback(gnssCallback, null)
            }
        } catch (e: Exception) {
            viewModel.errorMessage = "Error starting GPS: ${e.message}"
        }
    }

    private fun stopGpsHardware() {
        locationManager.removeUpdates(locationListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                locationManager.unregisterGnssStatusCallback(gnssCallback)
            } catch (e: IllegalArgumentException) { }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (viewModel.errorMessage != null) viewModel.errorMessage = null
            viewModel.lastFixTime = SystemClock.elapsedRealtime()

            var newSpeed = 0f
            if (location.hasSpeed()) {
                val speedKmh = location.speed * 3.6f
                // Noise Filter
                val isAccuracyAcceptable = !location.hasAccuracy() || location.accuracy < 50
                val isSpeedSignificant = speedKmh > 1.5f
                
                if (isAccuracyAcceptable && isSpeedSignificant) {
                    newSpeed = speedKmh
                }
            }
            // Delegate logic to ViewModel
            viewModel.updateLocation(newSpeed, viewModel.satelliteCount)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                viewModel.errorMessage = "GPS Provider is disabled."
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
            // Update VM
            viewModel.updateLocation(viewModel.currentSpeedKmh, count)
        }
    }
}

// ... Copy SpeedometerScreen and StatRow functions here (unchanged) ...
@Composable
fun SpeedometerScreen(
    currentSpeed: Float,
    maxSpeed: Float,
    satellites: Int,
    topSatellites: Int,
    error: String?
) {
    val statusColor = if (satellites >= 3) Color.Green else Color.Red

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        if (error != null) {
            Text(
                text = error,
                color = Color.Red,
                textAlign = TextAlign.Center,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val baseVal = maxWidth.value / 3.5f
            val baseFontSize = baseVal.sp
            val basePadding = (baseVal * 0.1f).dp
            
            // --- TOP LEFT: Satellite Status ---
            Row(
                modifier = Modifier.align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "satellites: ",
                    color = Color.Gray,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 16.sp
                )
                Text(
                    text = "$satellites",
                    color = Color.White,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            // --- CENTER: Speedometer ---
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val formattedSpeed = "%.2f".format(Locale.US, currentSpeed)
                val parts = formattedSpeed.split(".")
                val intPart = parts[0]
                val decPart = if (parts.size > 1) parts[1] else "00"

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = intPart,
                        color = Color.White,
                        fontSize = baseFontSize, 
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp,
                        lineHeight = baseFontSize,
                        maxLines = 1,
                        softWrap = false
                    )
                    
                    Text(
                        text = ".$decPart",
                        color = Color.LightGray, 
                        fontSize = (baseVal * 0.5f).sp, 
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = basePadding) 
                    )

                    Spacer(modifier = Modifier.width(basePadding))

                    Text(
                        text = "km/h",
                        color = Color.DarkGray,
                        fontSize = (baseVal * 0.25f).sp, 
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = basePadding)
                    )
                }
            }

            // --- BOTTOM: Stats Area ---
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Divider(
                    modifier = Modifier.width(150.dp).padding(bottom = 12.dp),
                    thickness = 1.dp,
                    color = Color.DarkGray
                )

                StatRow(label = "top speed", value = "%.1f".format(maxSpeed))
                StatRow(label = "top satellites", value = "$topSatellites")
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Session data: resets on app restart",
                    color = Color.DarkGray,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            color = Color.Gray,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}