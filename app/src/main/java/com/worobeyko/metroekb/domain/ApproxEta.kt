package com.worobeyko.metroekb.domain

import kotlin.math.roundToInt

/**
 * Время до поезда «по-человечески». Поезда приходят с разбросом около ±30 с от графика,
 * поэтому посекундный таймер создаёт ложную точность - показываем с шагом в полминуты:
 * «прибывает» (в пределах ±30 с), «~1 мин», «~2½ мин», … и целыми минутами от 10 мин.
 */
data class ApproxEta(val arriving: Boolean, val value: String) {

    /** Одной строкой: «прибывает» или «~2½ мин». */
    val text: String get() = if (arriving) "прибывает" else "~$value мин"

    /** Для озвучки TalkBack: «примерно через 2 с половиной минуты». */
    val spoken: String
        get() {
            if (arriving) return "поезд прибывает"
            val half = value.endsWith("½")
            val n = value.removeSuffix("½").toIntOrNull() ?: return "примерно через $value минут"
            return if (half) "примерно через $n с половиной минуты" else "примерно через $n ${minutesWord(n)}"
        }

    companion object {
        /** Разброс прибытия относительно графика, секунды. */
        const val TOLERANCE_SEC = 30

        /** «минуту / минуты / минут» по числу. */
        fun minutesWord(n: Int): String {
            val m100 = n % 100
            val m10 = n % 10
            return when {
                m100 in 11..14 -> "минут"
                m10 == 1 -> "минуту"
                m10 in 2..4 -> "минуты"
                else -> "минут"
            }
        }

        fun of(secondsUntil: Int): ApproxEta {
            if (secondsUntil <= TOLERANCE_SEC) return ApproxEta(true, "")
            if (secondsUntil >= 10 * 60) {
                return ApproxEta(false, (secondsUntil / 60.0).roundToInt().toString())
            }
            // Округление до ближайших 30 с; меньше минуты не показываем - это уже почти «прибывает».
            val halves = (secondsUntil / 30.0).roundToInt().coerceAtLeast(2)
            val whole = halves / 2
            return ApproxEta(false, if (halves % 2 == 1) "$whole½" else "$whole")
        }
    }
}
