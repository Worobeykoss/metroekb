package com.worobeyko.metroekb.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.worobeyko.metroekb.data.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Пеший маршрут вдоль дорог: точки (lat, lon) и длина в метрах. */
data class WalkRoute(val points: List<Pair<Double, Double>>, val distanceMeters: Double)

private fun round3(x: Double) = Math.round(x * 1000.0) / 1000.0

/**
 * Строит маршрут от пользователя до станции по дорогам через публичный OSRM (без ключа).
 * Перезапрашивается, когда пользователь сместился примерно на 100 м или сменилась станция.
 */
@Composable
fun rememberWalkRoute(user: UserLocation?, target: Station?): State<WalkRoute?> {
    val route = remember { mutableStateOf<WalkRoute?>(null) }
    val userKey = user?.let { "${round3(it.lat)},${round3(it.lon)}" }
    val targetId = target?.id

    LaunchedEffect(userKey, targetId) {
        if (user == null || target == null) {
            route.value = null
            return@LaunchedEffect
        }
        route.value = withContext(Dispatchers.IO) {
            fetchRoute(user.lat, user.lon, target.lat, target.lon)
        }
    }
    return route
}

private fun fetchRoute(uLat: Double, uLon: Double, sLat: Double, sLon: Double): WalkRoute? {
    return try {
        val url = URL(
            "https://router.project-osrm.org/route/v1/driving/" +
                "$uLon,$uLat;$sLon,$sLat?overview=full&geometries=geojson"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MetroEkb/1.0")
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        if (json.optString("code") != "Ok") return null
        val r = json.getJSONArray("routes").getJSONObject(0)
        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        val pts = ArrayList<Pair<Double, Double>>(coords.length())
        for (i in 0 until coords.length()) {
            val c = coords.getJSONArray(i)
            pts.add(c.getDouble(1) to c.getDouble(0)) // [lon, lat] -> (lat, lon)
        }
        if (pts.size < 2) null else WalkRoute(pts, r.optDouble("distance", 0.0))
    } catch (_: Exception) {
        null
    }
}
