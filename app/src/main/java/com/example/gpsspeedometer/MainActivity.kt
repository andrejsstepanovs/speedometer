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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.Divider // Changed from HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    private var errorMessage by mutableStateOf<String?>(null)
    
    // Logic control
    private var appStartTime = 0L

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocation) {
            errorMessage = null
            startTracking()
        } else {
            errorMessage = if (coarseLocation) {
                "Precise Location required for GPS speed accuracy.\nPlease allow 'Precise' in settings."
            } else {
                "Location permission denied.\nApp requires GPS access to function."
            }
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
                topSatellites = maxSatelliteCount,
                error = errorMessage
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
             errorMessage = "Permission missing."
             return
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
            errorMessage = "Error starting GPS: ${e.message}"
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (errorMessage != null) errorMessage = null

            val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
            currentSpeedKmh = speedKmh

            val timeElapsed = SystemClock.elapsedRealtime() - appStartTime
            if (timeElapsed > 5000 && satelliteCount >= 3) {
                if (speedKmh > maxSpeedKmh) {
                    maxSpeedKmh = speedKmh
                }
            }
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                errorMessage = "GPS Provider is disabled."
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
            satelliteCount = count
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
            // FIX: Calculate raw value first to avoid Type Mismatch (TextUnit vs Dp)
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
                        fontSize = (baseVal * 0.5f).sp, // Fixed math
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = basePadding) // Fixed padding type
                    )

                    Spacer(modifier = Modifier.width(basePadding))

                    Text(
                        text = "km/h",
                        color = Color.DarkGray,
                        fontSize = (baseVal * 0.25f).sp, // Fixed math
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = basePadding)
                    )
                }
            }

            // --- BOTTOM: Stats Area ---
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                // FIX: Replaced HorizontalDivider with Divider for compatibility
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