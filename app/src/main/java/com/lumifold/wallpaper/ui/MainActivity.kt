package com.lumifold.wallpaper.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.lumifold.wallpaper.service.FoldWallpaperService
import com.lumifold.wallpaper.ui.theme.LumifoldTheme

class MainActivity : ComponentActivity(), SensorEventListener {

    private val hingeAngle = mutableFloatStateOf(Float.NaN)
    private val wallpaperActive = mutableStateOf(false)
    private val resumeCount = mutableIntStateOf(0)
    private var sensorManager: SensorManager? = null
    private var hingeSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sensorManager = getSystemService(SensorManager::class.java)
        hingeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)

        setContent {
            LumifoldTheme {
                LumifoldScreen(
                    hingeAngle = hingeAngle,
                    hasHingeSensor = hingeSensor != null,
                    wallpaperActive = wallpaperActive.value,
                    refreshKey = resumeCount.intValue,
                    onOpenPicker = ::openLiveWallpaperPicker
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hingeSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        wallpaperActive.value = WallpaperManager.getInstance(this).wallpaperInfo?.packageName == packageName
        resumeCount.intValue++
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HINGE_ANGLE) {
            hingeAngle.floatValue = (event.values.firstOrNull() ?: return).coerceIn(0f, 180f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun openLiveWallpaperPicker() {
        startActivity(
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, FoldWallpaperService::class.java)
            )
        )
    }
}
