package com.worobeyko.metroekb.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Куда «смотрит» верх экрана, градусы от ИСТИННОГО севера (0..360), сглаженно.
 * null - у телефона нет компаса. [lat]/[lon] нужны для поправки на магнитное склонение.
 */
@Composable
fun rememberHeading(enabled: Boolean, lat: Double?, lon: Double?): State<Float?> {
    val context = LocalContext.current
    val heading = remember { mutableStateOf<Float?>(null) }

    DisposableEffect(enabled, lat != null) {
        if (!enabled) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return@DisposableEffect onDispose { }
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
            ?: return@DisposableEffect onDispose { }

        val declination = if (lat != null && lon != null) {
            GeomagneticField(lat.toFloat(), lon.toFloat(), 250f, System.currentTimeMillis()).declination
        } else 0f

        @Suppress("DEPRECATION")
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        // Сглаживание по кругу (через sin/cos), чтобы не прыгало через 0/360.
        var sx = 0.0
        var sy = 0.0
        var initialized = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                val (ax, ay) = when (display.rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotation, ax, ay, remapped)
                SensorManager.getOrientation(remapped, orientation)
                val deg = Math.toDegrees(orientation[0].toDouble()) + declination
                val rad = Math.toRadians(deg)
                if (!initialized) {
                    sx = cos(rad); sy = sin(rad); initialized = true
                } else {
                    sx += (cos(rad) - sx) * 0.15
                    sy += (sin(rad) - sy) * 0.15
                }
                heading.value = ((Math.toDegrees(atan2(sy, sx)) + 360.0) % 360.0).toFloat()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return heading
}

/** Азимут от точки 1 к точке 2, градусы от севера (0..360). */
fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLon = Math.toRadians(lon2 - lon1)
    val la1 = Math.toRadians(lat1)
    val la2 = Math.toRadians(lat2)
    val y = sin(dLon) * cos(la2)
    val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}
