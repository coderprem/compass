package com.example.compass

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var accelerometer: Sensor? = null

    private var _degree by mutableStateOf(0f)
    private var _xTilt by mutableStateOf(0f)
    private var _yTilt by mutableStateOf(0f)
    private var _isDarkMode by mutableStateOf(false)

    private var mediaPlayer: MediaPlayer? = null
    private var lastBeepTime = 0L

    private val rotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)

    private var _currentPage by mutableStateOf(0) // 0 = compass, 1 = leveler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        mediaPlayer = MediaPlayer.create(this, R.raw.beep)

        setContent {
            CompassTheme(useDarkTheme = _isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainApp(
                        currentDegree = _degree,
                        xTilt = _xTilt,
                        yTilt = _yTilt,
                        onPageChanged = { page -> _currentPage = page }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
        lightSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
        sensorManager.flush(this)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        mediaPlayer?.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationValues)

                val azimuthInDegrees = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                _degree = (azimuthInDegrees + 360) % 360

                // Only beep if we're on the compass screen (page 0)
                if (_currentPage == 0 && (_degree in 0f..2f || _degree in 358f..360f)) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBeepTime > 2000) {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer.create(this, R.raw.beep)
                        mediaPlayer?.start()
                        lastBeepTime = currentTime
                    }
                }
            }

            Sensor.TYPE_LIGHT -> {
                _isDarkMode = event.values[0] < 10
            }

            Sensor.TYPE_ACCELEROMETER -> {
                _xTilt = event.values[0]
                _yTilt = event.values[1]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun CompassTheme(useDarkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

@Composable
fun MainApp(currentDegree: Float, xTilt: Float, yTilt: Float, onPageChanged: (Int) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    // Notify when page changes
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> CompassScreen(currentDegree)
                1 -> LevelerScreen(xTilt, yTilt)
            }
        }

        // Pager indicator dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(2) { index ->
                val color = if (pagerState.currentPage == index)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(8.dp)
                        .background(color, CircleShape)
                )
            }
        }
    }
}

@Composable
fun CompassScreen(currentDegree: Float) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .rotate(-currentDegree)
        ) {
            Image(
                painter = painterResource(R.drawable.compass2),
                contentDescription = "Compass",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "%.1f°".format(currentDegree),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = getCardinalDirection(currentDegree),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun LevelerScreen(xTilt: Float, yTilt: Float) {
    // Precision control states
    var precisionMode by remember { mutableStateOf(false) }
    val bubbleSize = if (precisionMode) 20.dp else 40.dp
    val sensitivity = if (precisionMode) 100f else 50f // Higher sensitivity in precision mode
    val levelThreshold = if (precisionMode) 0.2f else 1f // Tighter threshold for precision

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Precision mode toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Precision Mode", style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = precisionMode,
                onCheckedChange = { precisionMode = it },
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Level container with enhanced graphics
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(Color.LightGray.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            // Precision crosshair with degree markers
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = size / 2f

                // Main crosshair
                drawLine(
                    color = Color.Gray.copy(alpha = 0.7f),
                    start = Offset(center.width, 0f),
                    end = Offset(center.width, size.height),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Color.Gray.copy(alpha = 0.7f),
                    start = Offset(0f, center.height),
                    end = Offset(size.width, center.height),
                    strokeWidth = 1.5f
                )

                // Degree markers (every 5°)
                for (i in -45..45 step 5) {
                    if (i != 0) {
                        val offset = (i * sensitivity / 45).dp.toPx()
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.3f),
                            radius = 2f,
                            center = Offset(center.width + offset, center.height),
                        )
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.3f),
                            radius = 2f,
                            center = Offset(center.width, center.height + offset),
                        )
                    }
                }
            }

            // Bubble with center indicator
            Box(
                modifier = Modifier
                    .offset(x = (-xTilt * sensitivity).dp, y = (yTilt * sensitivity).dp)
                    .size(bubbleSize)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (abs(xTilt) < levelThreshold && abs(yTilt) < levelThreshold)
                                    Color.Green else Color.Red,
                                Color.Black
                            ),
                        )
                    )
            )
        }

        Spacer(Modifier.height(24.dp))

        // Digital inclinometer with high precision
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DIGITAL INCLINOMETER",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "X-AXIS",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "${"%.2f°".format(xTilt)}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (abs(xTilt) < levelThreshold) Color.Green else MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Y-AXIS",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "${"%.2f°".format(yTilt)}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (abs(yTilt) < levelThreshold) Color.Green else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Level status with haptic feedback
        val haptic = LocalHapticFeedback.current
        var lastLevelState by remember { mutableStateOf(false) }
        val isLevel = abs(xTilt) < levelThreshold && abs(yTilt) < levelThreshold

        LaunchedEffect(isLevel) {
            if (isLevel && !lastLevelState) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            lastLevelState = isLevel
        }

        // Replace the Text composable with this enhanced version
        Text(
            text = when {
                isLevel -> "PERFECTLY LEVEL"
                abs(xTilt) < levelThreshold * 2 && abs(yTilt) < levelThreshold * 2 -> "ALMOST LEVEL"
                else -> "ADJUST SURFACE"
            },
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
            ),
            color = when {
                isLevel -> Color(0xFF00C853)  // Vibrant green
                abs(xTilt) < levelThreshold * 2 && abs(yTilt) < levelThreshold * 2 ->
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)  // Darker version of surface color
                else -> MaterialTheme.colorScheme.error  // Using theme's error color (usually red)
            },
            modifier = Modifier
                .padding(top = 16.dp)
                .background(
                    when {
                        isLevel -> Color.Green.copy(alpha = 0.1f)
                        else -> Color.Transparent
                    },
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}


private fun getCardinalDirection(degree: Float): String {
    return when {
        degree >= 337.5f || degree < 22.5f -> "N"
        degree >= 22.5f && degree < 67.5f -> "NE"
        degree >= 67.5f && degree < 112.5f -> "E"
        degree >= 112.5f && degree < 157.5f -> "SE"
        degree >= 157.5f && degree < 202.5f -> "S"
        degree >= 202.5f && degree < 247.5f -> "SW"
        degree >= 247.5f && degree < 292.5f -> "W"
        degree >= 292.5f && degree < 337.5f -> "NW"
        else -> ""
    }
}
