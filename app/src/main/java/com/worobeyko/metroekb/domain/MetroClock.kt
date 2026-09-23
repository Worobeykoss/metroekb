package com.worobeyko.metroekb.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class DayType { WEEKDAY, WEEKEND }

/**
 * Служебное время метро. Ночные рейсы (часы 0..2) относятся к КОНЦУ служебных суток,
 * поэтому по единой «служебной минуте» они идут после 23:xx. Та же шкала применяется и к
 * «сейчас», чтобы сравнения и сортировка были корректны на стыке полуночи.
 */
object MetroClock {
    private const val NIGHT_HOUR_CUTOFF = 2

    data class Now(val dayType: DayType, val serviceMinute: Double)

    fun dayTypeFor(date: LocalDate): DayType =
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY)
            DayType.WEEKEND else DayType.WEEKDAY

    /** "HH:MM" -> служебная минута суток (часы 0..2 -> +24ч). */
    fun serviceMinute(hhmm: String): Int {
        val i = hhmm.indexOf(':')
        val h = hhmm.substring(0, i).toInt()
        val m = hhmm.substring(i + 1).toInt()
        return (if (h <= NIGHT_HOUR_CUTOFF) h + 24 else h) * 60 + m
    }

    /**
     * Текущее время как дробная служебная минута - с наносекундной долей, иначе позиция
     * поезда меняется лишь раз в секунду и анимация идёт рывками.
     */
    fun serviceMinute(time: LocalTime): Double {
        val h = if (time.hour <= NIGHT_HOUR_CUTOFF) time.hour + 24 else time.hour
        val seconds = time.second + time.nano / 1_000_000_000.0
        return h * 60.0 + time.minute + seconds / 60.0
    }

    /**
     * Разрешает «сейчас» в тип дня и служебную минуту. В интервале 00:00-02:59 служебные
     * сутки принадлежат ПРЕДЫДУЩЕМУ календарному дню (тип дня берём от него).
     */
    fun resolve(now: LocalDateTime): Now {
        val serviceDate =
            if (now.hour <= NIGHT_HOUR_CUTOFF) now.toLocalDate().minusDays(1) else now.toLocalDate()
        return Now(dayTypeFor(serviceDate), serviceMinute(now.toLocalTime()))
    }
}
