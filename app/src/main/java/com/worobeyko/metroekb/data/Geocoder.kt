package com.worobeyko.metroekb.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Найденное место: подпись и координаты. */
data class Place(val title: String, val subtitle: String, val lat: Double, val lon: Double)

/**
 * Поиск адреса через OpenStreetMap Nominatim, только в пределах Екатеринбурга. Правила
 * сервиса: не чаще запроса в секунду и понятный User-Agent - ищем по кнопке, а не по буквам.
 */
object Geocoder {
    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = URL(
                "https://nominatim.openstreetmap.org/search?format=json&limit=6&accept-language=ru" +
                    "&bounded=1&viewbox=60.45,56.96,60.80,56.70&q=$q"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "MetroEkb/1.0 (Android; metro Ekaterinburg app)")
            }
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val parts = o.optString("display_name").split(", ")
                Place(
                    title = parts.take(2).joinToString(", "),
                    subtitle = parts.drop(2).take(2).joinToString(", "),
                    lat = o.getString("lat").toDouble(),
                    lon = o.getString("lon").toDouble(),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
