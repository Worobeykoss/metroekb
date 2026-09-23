package com.worobeyko.metroekb.domain

import java.time.LocalTime

/**
 * Пасхалка «поезд-призрак»: глубокой ночью, когда метро закрыто, по линии медленно катается
 * служебный поезд — туда и обратно, по кругу, по настоящей форме пути. Вызвать днём: 5 тапов
 * по плашке со временем.
 */
object GhostTrain {
    /** Время в пути от конечной до конечной, мс. */
    private const val ONE_WAY_MS = 6 * 60_000L

    /** Ночное окно, когда метро закрыто: 01:00–05:30. */
    fun isGhostHour(time: LocalTime): Boolean =
        time.isAfter(LocalTime.of(1, 0)) && time.isBefore(LocalTime.of(5, 30))

    /** Положение призрака; [ageMs] — положение на ageMs раньше (для «хвоста»). */
    fun pose(geometry: LineGeometry, epochMs: Long, ageMs: Long = 0): LineGeometry.Pose? {
        if (geometry.totalLength <= 0) return null
        val t = Math.floorMod(epochMs - ageMs, 2 * ONE_WAY_MS)
        val forward = t < ONE_WAY_MS
        val p = (if (forward) t else 2 * ONE_WAY_MS - t).toDouble() / ONE_WAY_MS // 0..1 по линии
        val pose = geometry.pointAt(p * geometry.totalLength)
        return if (forward) pose else pose.copy(bearing = (pose.bearing + 180.0) % 360.0)
    }
}
