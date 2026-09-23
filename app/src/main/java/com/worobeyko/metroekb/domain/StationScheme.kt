package com.worobeyko.metroekb.domain

import kotlin.math.cos
import kotlin.math.sin

/**
 * Выход на схеме станции: где вдоль платформы (метры, «+» — в сторону Ботанической) и с
 * какой стороны линии (+1 — справа, если смотреть на юг).
 */
data class SchemeExit(val ref: String, val alongM: Double, val side: Int, val accessible: Boolean)

/**
 * Схема станции «изнутри» по данным OSM: входы проецируются на ось линии в точке станции.
 * Это приблизительно (координаты входов — уличные, точка станции — не обязательно центр
 * платформы), но показывает главное: какие выходы у какого конца платформы.
 */
object StationScheme {

    /** Метров «за концом платформы», после которых выход считаем крайним. */
    private const val END_ZONE_M = 15.0

    /** [entrances] — (ref, lat, lon, доступен ли) входов этой станции. */
    fun exits(
        stationLat: Double,
        stationLon: Double,
        southBearing: Double,
        entrances: List<Triple<String, Pair<Double, Double>, Boolean>>,
    ): List<SchemeExit> {
        val b = Math.toRadians(southBearing)
        val mPerLat = 110_540.0
        val mPerLon = 111_320.0 * cos(Math.toRadians(stationLat))
        return entrances.map { (ref, ll, accessible) ->
            val dx = (ll.second - stationLon) * mPerLon // на восток
            val dy = (ll.first - stationLat) * mPerLat // на север
            val along = dx * sin(b) + dy * cos(b)
            val cross = dx * cos(b) - dy * sin(b)
            SchemeExit(ref, along, if (cross >= 0) 1 else -1, accessible)
        }.sortedBy { it.alongM }
    }

    /**
     * Какие выходы у головы и у хвоста поезда в направлении [south] (на юг — голова со стороны
     * Ботанической). Пара (у первого вагона, у последнего).
     */
    fun headAndTail(exits: List<SchemeExit>, south: Boolean): Pair<List<SchemeExit>, List<SchemeExit>> {
        val southEnd = exits.filter { it.alongM > END_ZONE_M }
        val northEnd = exits.filter { it.alongM < -END_ZONE_M }
        return if (south) southEnd to northEnd else northEnd to southEnd
    }
}

/** Грубая оценка загруженности по частоте поездов: их пускают чаще, когда людей больше. */
enum class Crowd(val title: String, val emoji: String) {
    LOW("скорее свободно", "🙂"),
    MID("средне", "😐"),
    HIGH("скорее много людей", "😬");

    companion object {
        fun of(trainsNow: Int, trainsMax: Int): Crowd? {
            if (trainsMax <= 0 || trainsNow <= 0) return null
            val r = trainsNow.toDouble() / trainsMax
            return when {
                r >= 0.85 -> HIGH
                r >= 0.6 -> MID
                else -> LOW
            }
        }
    }
}
