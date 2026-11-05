package com.legitdev.speedometer.app

import android.Manifest
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope

import com.legitdev.speedometer.app.data.repository.LocationRepositoryImpl
import com.legitdev.speedometer.app.di.SpeedometerViewModelFactory
import com.legitdev.speedometer.app.domain.model.GpsReading
import com.legitdev.speedometer.app.domain.model.SessionConfig
import com.legitdev.speedometer.app.domain.model.SpeedometerState
import com.legitdev.speedometer.app.domain.model.SpeedUnit
import com.legitdev.speedometer.app.domain.util.SpeedConverter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {

    private val viewModel: SpeedometerViewModel by viewModels { SpeedometerViewModelFactory(applicationContext) }
    
    private lateinit var locationRepository: LocationRepositoryImpl
    private var watchdogJob: Job? = null
    private var lastFixTime: Long = 0L

    private var isInPipMode by mutableStateOf(false)

    private val powerManager by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.onPowerSaveModeChanged(powerManager.isPowerSaveMode)
        }
    }

    private val pipToggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.onPipBackgroundToggled()
        }
    }

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
        
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        isInPipMode = isInPictureInPictureMode

        locationRepository = LocationRepositoryImpl(this)

        ContextCompat.registerReceiver(
            this,
            pipToggleReceiver,
            IntentFilter(ACTION_TOGGLE_PIP_TRANSPARENCY),
            ContextCompat.RECEIVER_EXPORTED
        )
        
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
                isPowerSaveMode = viewModel.isPowerSaveMode,
                isPipTransparent = viewModel.isPipTransparent,
                onPipBackgroundToggle = { viewModel.onPipBackgroundToggled() },
                onEnterPip = { enterPipMode() },
                onUnitToggle = { viewModel.toggleUnit() }
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
            val toggleIntent = Intent(ACTION_TOGGLE_PIP_TRANSPARENCY).setPackage(packageName)
            val togglePending = PendingIntent.getBroadcast(
                this,
                0,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val toggleAction = RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_pip_opacity),
                "Transparency",
                "Toggle background transparency",
                togglePending
            )
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setActions(listOf(toggleAction))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            powerSaveReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        )
        viewModel.onPowerSaveModeChanged(powerManager.isPowerSaveMode)
        checkPermissionsAndStart()
        startWatchdog()
        viewModel.onSessionStart()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(powerSaveReceiver)
        stopLocationTracking()
        stopWatchdog()

        if (!isChangingConfigurations) {
            viewModel.onSessionReset()
        }
    }

    companion object {
        const val ACTION_TOGGLE_PIP_TRANSPARENCY = "com.legitdev.speedometer.app.TOGGLE_PIP_TRANSPARENCY"
    }

    override fun onDestroy() {
        unregisterReceiver(pipToggleReceiver)
        super.onDestroy()
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000.milliseconds)
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
            onLocationFix = { reading ->
                lastFixTime = reading.timestamp
                viewModel.onGpsReadingReceived(reading)
            },
            onSatelliteCount = { count -> viewModel.onSatelliteCountReceived(count) },
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

private const val MIN_PIP_FONT_SCALE = 0.5f
private const val MAX_PIP_FONT_SCALE = 4f
private const val PIP_TEXT_HEIGHT_FRACTION = 1.25f
private const val PIP_WIDTH_SAFETY = 0.96f
private const val PIP_BASE_SPEED_SP = 64f
private const val PIP_BASE_DECIMAL_SP = 24f
private const val PIP_BASE_UNIT_SP = 14f
private const val PIP_BASE_LETTER_SPACING_SP = -2f

@Composable
fun SpeedometerScreen(
    state: SpeedometerState,
    error: String?,
    isInPipMode: Boolean,
    isPowerSaveMode: Boolean,
    isPipTransparent: Boolean,
    onPipBackgroundToggle: () -> Unit,
    onEnterPip: () -> Unit,
    onUnitToggle: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color.Black else Color.White
    val primaryColor = if (isDark) Color.White else Color.Black
    val secondaryColor = if (isDark) Color.LightGray else Color.DarkGray
    val tertiaryColor = if (isDark) Color.DarkGray else Color.Gray
    val labelColor = if (isDark) Color.Gray else Color.DarkGray
    val dividerColor = if (isDark) Color.DarkGray else Color.LightGray
    val pipContainerColor = if (isDark) Color.Gray else Color.LightGray
    val pipContentColor = if (isDark) Color.White else Color.Black

    val isSearchingGps = state.satelliteCount < SessionConfig.MIN_SATELLITES_FOR_LOCK
    val statusColor = if (isSearchingGps) Color.Red else Color.Green
    val powerSaveColor = Color(0xFFCC8800)

    val pipTransparentActive = isInPipMode && isPipTransparent
    val hudGreen = Color(0xFF00FF41)
    val hudScrim = Color(0x99000000)
    val pipBackground = if (pipTransparentActive) hudScrim else backgroundColor
    val speedColor = if (pipTransparentActive) hudGreen else primaryColor
    val speedDecimalColor = if (pipTransparentActive) hudGreen else secondaryColor
    val speedUnitColor = if (pipTransparentActive) hudGreen else tertiaryColor

    val displaySpeed = if (state.displayUnit == SpeedUnit.KMH) state.currentSpeedKmh else SpeedConverter.kmhToMph(state.currentSpeedKmh)
    val formattedSpeed = "%.2f".format(Locale.US, displaySpeed)
    val parts = formattedSpeed.split(".")
    val intPart = parts[0]
    val decPart = if (parts.size > 1) parts[1] else "00"

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(pipBackground)
            .pointerInput(isInPipMode) {
                detectTapGestures {
                    if (isInPipMode) {
                        onPipBackgroundToggle()
                    }
                }
            }
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        val textMeasurer = rememberTextMeasurer()
        val pipFontScale = if (isInPipMode) {
            val mainStyle = MaterialTheme.typography.displayLarge.copy(
                fontSize = PIP_BASE_SPEED_SP.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = PIP_BASE_LETTER_SPACING_SP.sp
            )
            val decimalStyle = MaterialTheme.typography.headlineMedium.copy(
                fontSize = PIP_BASE_DECIMAL_SP.sp,
                fontWeight = FontWeight.Bold
            )
            val unitStyle = MaterialTheme.typography.headlineMedium.copy(
                fontSize = PIP_BASE_UNIT_SP.sp
            )
            val intLayout = textMeasurer.measure(intPart, mainStyle)
            val decimalLayout = textMeasurer.measure(".$decPart", decimalStyle)
            val unitLayout = textMeasurer.measure(state.displayUnit.displayString, unitStyle)
            val density = LocalDensity.current
            with(density) {
                val textWidthPx = intLayout.size.width +
                    decimalLayout.size.width + 2.dp.toPx() +
                    unitLayout.size.width + 4.dp.toPx()
                val lineHeightPx = maxOf(intLayout.size.height, decimalLayout.size.height).toFloat()
                val scaleByWidth = PIP_WIDTH_SAFETY * maxWidth.toPx() / textWidthPx
                val scaleByHeight = (maxHeight.toPx() * PIP_TEXT_HEIGHT_FRACTION) / lineHeightPx
                minOf(scaleByWidth, scaleByHeight).coerceIn(MIN_PIP_FONT_SCALE, MAX_PIP_FONT_SCALE)
            }
        } else {
            1f
        }
        val mainSpeedSize = if (isInPipMode) (PIP_BASE_SPEED_SP * pipFontScale).sp else 120.sp
        val decimalSize = if (isInPipMode) (PIP_BASE_DECIMAL_SP * pipFontScale).sp else 40.sp
        val unitSize = if (isInPipMode) (PIP_BASE_UNIT_SP * pipFontScale).sp else 24.sp
        val letterSpacing = if (isInPipMode) (PIP_BASE_LETTER_SPACING_SP * pipFontScale).sp else (-4).sp

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
                    GpsStatusDot(
                        color = statusColor,
                        isSearching = isSearchingGps
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "satellites: ",
                        color = labelColor,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${state.satelliteCount}",
                        color = primaryColor,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // --- TOP RIGHT: Power Save Warning ---
                if (isPowerSaveMode) {
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(powerSaveColor, shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "power save",
                            color = labelColor,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // --- CENTER: Speedometer ---
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row {
                    // Integer Part
                    Text(
                        text = intPart,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = mainSpeedSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = letterSpacing,
                            color = speedColor
                        ),
                        modifier = Modifier.alignByBaseline()
                    )
                    
                    // Decimal Part
                    Text(
                        text = ".$decPart",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = decimalSize,
                            fontWeight = FontWeight.Bold,
                            color = speedDecimalColor
                        ),
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = 2.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Unit
                    Text(
                        text = state.displayUnit.displayString,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = unitSize,
                            color = speedUnitColor
                        ),
                        modifier = Modifier
                            .alignByBaseline()
                            .clickable { onUnitToggle() }
                    )
                }
            }

            if (!isInPipMode) {
                // --- BOTTOM LEFT: Stats Area ---
                Column(
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.width(150.dp).padding(bottom = 12.dp),
                        thickness = 1.dp,
                        color = dividerColor
                    )

                    val displayMaxSpeed = if (state.displayUnit == SpeedUnit.KMH) state.maxSpeedKmh else SpeedConverter.kmhToMph(state.maxSpeedKmh)
                    StatRow(label = "top speed", value = "%.1f".format(displayMaxSpeed), labelColor, primaryColor)
                    StatRow(label = "top satellites", value = "${state.maxSatelliteCount}", labelColor, primaryColor)
                }
                
                // --- BOTTOM RIGHT: PiP Button ---
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Button(
                        onClick = onEnterPip,
                        modifier = Modifier
                            .align(Alignment.BottomEnd),
                        colors = ButtonDefaults.buttonColors(containerColor = pipContainerColor)
                    ) {
                        Text(text = "Float", color = pipContentColor)
                    }
                }
            }
        }
    }
}

@Composable
fun GpsStatusDot(color: Color, isSearching: Boolean) {
    val pulse = if (isSearching) {
        rememberInfiniteTransition(label = "gps_search").animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gps_search_alpha"
        )
    } else {
        null
    }

    Box(
        modifier = Modifier
            .size(12.dp)
            .graphicsLayer { alpha = pulse?.value ?: 1f }
            .background(color, shape = CircleShape)
    )
}

@Composable
fun StatRow(label: String, value: String, labelColor: Color, valueColor: Color) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            color = labelColor,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
