package com.example.gpsspeedometer

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

import com.example.gpsspeedometer.data.repository.LocationRepositoryImpl
import com.example.gpsspeedometer.di.SpeedometerViewModelFactory
import com.example.gpsspeedometer.domain.model.GpsReading
import com.example.gpsspeedometer.domain.model.SpeedometerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: SpeedometerViewModel by viewModels { SpeedometerViewModelFactory.INSTANCE }
    
    private lateinit var locationRepository: LocationRepositoryImpl
    private var watchdogJob: Job? = null
    private var lastFixTime: Long = 0L

    private var isInPipMode by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocation) {
            lifecycleScope.launch {
                startLocationTracking()
            }
        } else {
            viewModel.onError(if (coarseLocation) {
                "Precise Location required for GPS speed accuracy.\nPlease allow 'Precise' in settings."
            } else {
                "Location permission denied.\nApp requires GPS access to function."
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationRepository = LocationRepositoryImpl(this)
        
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
                state = viewModel.state,
                error = viewModel.errorMessage,
                isInPipMode = isInPipMode,
                onEnterPip = { enterPipMode() }
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        }
        isInPipMode = isInPictureInPictureMode
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onStart() {
        super.onStart()
        checkPermissionsAndStart()
        startWatchdog()
        viewModel.onSessionStart()
    }

    override fun onStop() {
        super.onStop()
        stopLocationTracking()
        stopWatchdog()

        if (!isChangingConfigurations) {
            viewModel.onSessionReset()
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                val timeSinceLastFix = SystemClock.elapsedRealtime() - lastFixTime
                if (lastFixTime > 0 && timeSinceLastFix > 2000 && viewModel.state.currentSpeedKmh > 0) {
                    val fakeReading = GpsReading(0f, null, viewModel.state.satelliteCount, SystemClock.elapsedRealtime())
                    viewModel.onGpsReadingReceived(fakeReading)
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
            lifecycleScope.launch {
                startLocationTracking()
            }
        }
    }

    private suspend fun startLocationTracking() {
        locationRepository.startLocationUpdates(
            onReadingUpdate = { reading ->
                lastFixTime = reading.timestamp
                viewModel.onGpsReadingReceived(reading)
            },
            onGpsError = { error ->
                if (error != null) {
                    viewModel.onError(error)
                }
            }
        )
    }

    private fun stopLocationTracking() {
        lifecycleScope.launch {
            locationRepository.stopLocationUpdates()
        }
    }
}

@Composable
fun SpeedometerScreen(
    state: SpeedometerState,
    error: String?,
    isInPipMode: Boolean,
    onEnterPip: () -> Unit
) {
    val statusColor = if (state.satelliteCount >= 3) Color.Green else Color.Red

    // Adjust font sizes for PiP mode
    val mainSpeedSize = if (isInPipMode) 64.sp else 120.sp
    val decimalSize = if (isInPipMode) 24.sp else 40.sp
    val unitSize = if (isInPipMode) 14.sp else 24.sp
    val letterSpacing = if (isInPipMode) (-2).sp else (-4).sp

    Box(
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
            if (!isInPipMode) {
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
                        text = "${state.satelliteCount}",
                        color = Color.White,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // --- CENTER: Speedometer ---
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val formattedSpeed = "%.2f".format(Locale.US, state.currentSpeedKmh)
                val parts = formattedSpeed.split(".")
                val intPart = parts[0]
                val decPart = if (parts.size > 1) parts[1] else "00"

                Row {
                    // Integer Part
                    Text(
                        text = intPart,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = mainSpeedSize, 
                            fontWeight = FontWeight.Bold,
                            letterSpacing = letterSpacing,
                            color = Color.White
                        ),
                        modifier = Modifier.alignByBaseline()
                    )
                    
                    // Decimal Part
                    Text(
                        text = ".$decPart",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = decimalSize,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        ),
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = 2.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Unit
                    Text(
                        text = "km/h",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = unitSize,
                            color = Color.DarkGray
                        ),
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }

            if (!isInPipMode) {
                // --- BOTTOM LEFT: Stats Area ---
                Column(
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Divider(
                        modifier = Modifier.width(150.dp).padding(bottom = 12.dp),
                        thickness = 1.dp,
                        color = Color.DarkGray
                    )

                    StatRow(label = "top speed", value = "%.1f".format(state.maxSpeedKmh))
                    StatRow(label = "top satellites", value = "${state.maxSatelliteCount}")
                }
                
                // --- BOTTOM RIGHT: PiP Button ---
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Button(
                        onClick = onEnterPip,
                        modifier = Modifier
                            .align(Alignment.BottomEnd),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) {
                        Text(text = "Float", color = Color.White)
                    }
                }
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
