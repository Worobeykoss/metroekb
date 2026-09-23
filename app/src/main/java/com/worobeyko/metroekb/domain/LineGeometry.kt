package com.worobeyko.metroekb.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Настоящая форма линии: segments[i] - ломаная (lat, lon) от станции i к станции i+1
 * (из OpenStreetMap, см. tools/fetch_geometry.py). Нет данных - прямые между станциями.
 *
 * Положение на линии задаётся «глобальной» координатой - метрами от первой станции
 * (Проспект Космонавтов) вдоль пути.
 */
class LineGeometry(segments: List<List<Pair<Double, Double>>>) {

    data class Pose(val lat: Double, val lon: Double, val bearing: Double)

    private class Seg(val lat: DoubleArray, val lon: DoubleArray, val cum: DoubleArray) {
        val length: Double get() = cum.last()
    }

    private val segs: List<Seg> = segments.map { pts ->
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) {
            cum[i] = cum[i - 1] + distanceM(pts[i - 1].first, pts[i - 1].second, pts[i].first, pts[i].second)
        }
        Seg(DoubleArray(pts.size) { pts[it].first }, DoubleArray(pts.size) { pts[it].second }, cum)
    }

    /** Метры от первой станции до станции i. */
    private val stationAt: DoubleArray = DoubleArray(segs.size + 1).also { a ->
        for (i in segs.indices) a[i + 1] = a[i] + segs[i].length
    }

    val totalLength: Double get() = stationAt.last()

    val segmentCount: Int get() = segs.size

    /** Длина перегона между соседними станциями a и b (в любом порядке), метры. */
    fun legLength(a: Int, b: Int): Double = segs.getOrNull(minOf(a, b))?.length ?: 0.0

    /** Глобальная координата точки перегона a->b на доле пути fraction. */
    fun globalAt(a: Int, b: Int, fraction: Double): Double {
        val lo = minOf(a, b)
        val f = if (a < b) fraction else 1 - fraction
        return stationAt[lo] + (segs.getOrNull(lo)?.length ?: 0.0) * f.coerceIn(0.0, 1.0)
    }

    fun stationGlobal(i: Int): Double = stationAt[i.coerceIn(0, stationAt.lastIndex)]

    /** Точка на линии по глобальной координате; bearing - по ходу увеличения координаты (на юг). */
    fun pointAt(global: Double): Pose {
        if (segs.isEmpty()) return Pose(0.0, 0.0, 0.0)
        val g = global.coerceIn(0.0, totalLength)
        var s = 0
        while (s < segs.lastIndex && g > stationAt[s + 1]) s++
        val seg = segs[s]
        val local = g - stationAt[s]
        var k = 1
        while (k < seg.cum.lastIndex && seg.cum[k] < local) k++
        val span = (seg.cum[k] - seg.cum[k - 1]).takeIf { it > 0 } ?: 1.0
        val t = ((local - seg.cum[k - 1]) / span).coerceIn(0.0, 1.0)
        val lat = seg.lat[k - 1] + (seg.lat[k] - seg.lat[k - 1]) * t
        val lon = seg.lon[k - 1] + (seg.lon[k] - seg.lon[k - 1]) * t
        return Pose(lat, lon, bearing(seg.lat[k - 1], seg.lon[k - 1], seg.lat[k], seg.lon[k]))
    }

    /** Поза поезда на перегоне a->b: положение и курс по ходу движения. */
    fun pose(a: Int, b: Int, fraction: Double): Pose {
        val p = pointAt(globalAt(a, b, fraction))
        return if (a < b) p else p.copy(bearing = (p.bearing + 180.0) % 360.0)
    }

    /** Точки линии между двумя глобальными координатами, в порядке from -> to. */
    fun slice(from: Double, to: Double): List<Pair<Double, Double>> {
        val lo = minOf(from, to).coerceIn(0.0, totalLength)
        val hi = maxOf(from, to).coerceIn(0.0, totalLength)
        val out = ArrayList<Pair<Double, Double>>()
        pointAt(lo).let { out += it.lat to it.lon }
        for (s in segs.indices) {
            val seg = segs[s]
            for (k in seg.cum.indices) {
                val g = stationAt[s] + seg.cum[k]
                if (g > lo && g < hi) out += seg.lat[k] to seg.lon[k]
            }
        }
        pointAt(hi).let { out += it.lat to it.lon }
        return if (from <= to) out else out.asReversed()
    }

    /** Вся линия одной ломаной - для слоя маршрута на карте. */
    fun polyline(): List<Pair<Double, Double>> = slice(0.0, totalLength)

    /** Касательная (курс, градусы) в станции i - вдоль линии на юг. */
    fun bearingAtStation(i: Int): Double {
        val g = stationGlobal(i)
        val a = pointAt((g - 60).coerceAtLeast(0.0))
        val b = pointAt((g + 60).coerceAtMost(totalLength))
        return bearing(a.lat, a.lon, b.lat, b.lon)
    }

    companion object {
        /** Прямые между станциями - когда геометрии нет (или для тестов). */
        fun straight(points: List<Pair<Double, Double>>): LineGeometry =
            LineGeometry(points.zipWithNext { a, b -> listOf(a, b) })

        fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return r * 2 * atan2(sqrt(a), sqrt(1 - a))
        }

        fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLon = Math.toRadians(lon2 - lon1)
            val la1 = Math.toRadians(lat1)
            val la2 = Math.toRadians(lat2)
            val y = sin(dLon) * cos(la2)
            val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
            return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        }
    }
}
