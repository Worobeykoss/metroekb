package com.worobeyko.metroekb.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Вход в метро (из OpenStreetMap, см. tools/fetch_entrances.py). ref - номер входа. */
@Serializable
data class Entrance(
    val stationId: String,
    val lat: Double,
    val lon: Double,
    val ref: String = "",
    val name: String = "",
    /** Доступность для колясок по OSM: yes / limited / no / "" (не отмечено). */
    val wheelchair: String = "",
) {
    val accessible: Boolean get() = wheelchair == "yes" || wheelchair == "limited"
}

@Serializable
data class EntrancesDoc(val source: String = "", val entrances: List<Entrance> = emptyList())

/** Входы из assets/entrances.json; загружаются один раз. */
object EntranceRepository {
    @Volatile
    private var cached: List<Entrance>? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun get(context: Context): List<Entrance> =
        cached ?: synchronized(this) {
            cached ?: load(context).also { cached = it }
        }

    fun fromJson(text: String): List<Entrance> =
        json.decodeFromString(EntrancesDoc.serializer(), text).entrances

    private fun load(context: Context): List<Entrance> = try {
        fromJson(context.assets.open("entrances.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
    } catch (_: Exception) {
        emptyList()
    }
}
