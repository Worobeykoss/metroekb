package com.worobeyko.metroekb.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** Погода «наверху» у станции. code - код погоды WMO (Open-Meteo). */
data class Weather(val tempC: Double, val code: Int, val precipitationMm: Double, val windMs: Double) {

    private val isSnow get() = code in 71..77 || code in 85..86
    private val isRain get() = code in 51..67 || code in 80..82 || code in 95..99 || (precipitationMm > 0.1 && !isSnow)

    val icon: String
        get() = when {
            code in 95..99 -> "⛈"
            isSnow -> "❄"
            isRain -> "🌧"
            code == 45 || code == 48 -> "🌫"
            code == 3 -> "☁"
            code in 1..2 -> "⛅"
            else -> "☀"
        }

    val description: String
        get() = when (code) {
            0 -> "ясно"
            1 -> "в основном ясно"
            2 -> "переменная облачность"
            3 -> "пасмурно"
            45, 48 -> "туман"
            51, 53, 55 -> "морось"
            56, 57 -> "ледяная морось"
            61 -> "небольшой дождь"
            63 -> "дождь"
            65 -> "сильный дождь"
            66, 67 -> "ледяной дождь"
            71 -> "небольшой снег"
            73 -> "снег"
            75 -> "сильный снег"
            77 -> "снежная крупа"
            80, 81 -> "ливень"
            82 -> "сильный ливень"
            85, 86 -> "снегопад"
            95 -> "гроза"
            96, 99 -> "гроза с градом"
            else -> ""
        }

    /** Короткий совет или null. */
    val hint: String?
        get() = when {
            isRain -> "возьми зонт"
            isSnow -> "скользко"
            tempC <= -15 -> "очень холодно"
            tempC >= 28 -> "жарко"
            windMs >= 10 -> "сильный ветер"
            else -> null
        }

    val tempText: String
        get() {
            val t = tempC.roundToInt()
            return if (t > 0) "+$t°" else "$t°"
        }
}

/** Погода из Open-Meteo (без ключа). Кэш на 15 минут по станции. */
object WeatherRepository {
    private const val TTL_MS = 15 * 60_000L
    private val cache = HashMap<String, Pair<Long, Weather>>()

    fun cached(key: String): Weather? = synchronized(cache) {
        cache[key]?.takeIf { System.currentTimeMillis() - it.first < TTL_MS }?.second
    }

    suspend fun load(key: String, lat: Double, lon: Double): Weather? {
        cached(key)?.let { return it }
        val w = withContext(Dispatchers.IO) { fetch(lat, lon) } ?: return null
        synchronized(cache) { cache[key] = System.currentTimeMillis() to w }
        return w
    }

    private fun fetch(lat: Double, lon: Double): Weather? = try {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,precipitation,weather_code,wind_speed_10m&wind_speed_unit=ms"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "MetroEkb/1.0")
        }
        val cur = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
        Weather(
            tempC = cur.getDouble("temperature_2m"),
            code = cur.optInt("weather_code", 0),
            precipitationMm = cur.optDouble("precipitation", 0.0),
            windMs = cur.optDouble("wind_speed_10m", 0.0),
        )
    } catch (_: Exception) {
        null
    }
}

/** Погода у станции для UI; null - ещё грузится или нет сети. */
@Composable
fun rememberWeather(station: Station?): State<Weather?> {
    val state = remember(station?.id) { mutableStateOf(station?.let { WeatherRepository.cached(it.id) }) }
    LaunchedEffect(station?.id) {
        if (station != null) state.value = WeatherRepository.load(station.id, station.lat, station.lon)
    }
    return state
}
