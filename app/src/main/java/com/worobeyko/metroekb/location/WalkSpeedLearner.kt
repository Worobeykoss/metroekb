package com.worobeyko.metroekb.location

/**
 * Учится, как быстро ходит пользователь: по двум соседним GPS-точкам считает скорость и
 * берёт только правдоподобную ходьбу (0.7-2.3 м/с, не меньше 15 м и 8-90 с между точками) -
 * стояние, бег за автобусом и поездки в транспорте отбрасываются.
 */
class WalkSpeedLearner {
    private var last: UserLocation? = null

    /** Новая точка; возвращает измеренную скорость ходьбы (м/с) или null. */
    fun onFix(loc: UserLocation): Double? {
        val prev = last
        last = loc
        prev ?: return null
        val dt = (loc.timeMs - prev.timeMs) / 1000.0
        if (dt < 8 || dt > 90) return null
        val d = distanceMeters(prev.lat, prev.lon, loc.lat, loc.lon)
        if (d < 15) return null
        return (d / dt).takeIf { it in 0.7..2.3 }
    }

    companion object {
        /** Сколько весит один замер в скользящем среднем. */
        const val ALPHA = 0.15f

        /** Новая скорость, км/ч: плавное среднее со старой, в разумных пределах. */
        fun blend(currentKmh: Float, measuredMps: Double): Float =
            (currentKmh * (1 - ALPHA) + (measuredMps * 3.6).toFloat() * ALPHA).coerceIn(3f, 7f)
    }
}
