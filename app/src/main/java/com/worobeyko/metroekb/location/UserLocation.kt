package com.worobeyko.metroekb.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.worobeyko.metroekb.data.Station
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Точка пользователя и когда она получена (для обучения скорости ходьбы). */
data class UserLocation(val lat: Double, val lon: Double, val timeMs: Long = System.currentTimeMillis())

/** Средняя скорость пешехода, м/с (~4.9 км/ч). */
private const val WALK_SPEED_MPS = 1.35

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** Расстояние по формуле гаверсинуса, метры. */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

data class NearestStation(val station: Station, val meters: Double) {
    /** Время пешком до станции, минуты (не меньше 1). */
    val walkMinutes: Int get() = (meters / WALK_SPEED_MPS / 60.0).roundToInt().coerceAtLeast(1)
}

fun nearestStation(loc: UserLocation, stations: List<Station>): NearestStation? {
    return stations
        .map { NearestStation(it, distanceMeters(loc.lat, loc.lon, it.lat, it.lon)) }
        .minByOrNull { it.meters }
}

/**
 * Подписка на геопозицию через системный LocationManager (GPS + сеть), с запросом
 * разрешения. Без Google Play Services. Возвращает State, обновляемый на главном потоке.
 */
@SuppressLint("MissingPermission")
@Composable
fun rememberUserLocation(): State<UserLocation?> {
    val context = LocalContext.current
    val location = remember { mutableStateOf<UserLocation?>(null) }
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> granted = result.values.any { it } }

    LaunchedEffect(Unit) {
        if (!granted) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@DisposableEffect onDispose { }

        // Полный объект (не лямбда): на API 26-29 у LocationListener нет default-методов,
        // иначе система вызовет неимплементированный метод -> AbstractMethodError.
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                location.value = UserLocation(loc.latitude, loc.longitude)
            }

            @Deprecated("до API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            val last: Location? = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (last != null) location.value = UserLocation(last.latitude, last.longitude)

            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 2000L, 10f, listener, Looper.getMainLooper()
                )
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 2000L, 10f, listener, Looper.getMainLooper()
                )
            }
        } catch (_: SecurityException) {
        }

        onDispose { lm.removeUpdates(listener) }
    }

    return location
}
