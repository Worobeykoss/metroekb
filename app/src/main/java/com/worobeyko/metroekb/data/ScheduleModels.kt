package com.worobeyko.metroekb.data

import kotlinx.serialization.Serializable

/** Направления движения по единственной линии. Значения совпадают с ключами в schedule.json. */
object Directions {
    const val SOUTH = "to_botanicheskaya"        // на юг, со стороны Проспекта Космонавтов
    const val NORTH = "to_prospekt_kosmonavtov"  // на север, со стороны Машиностроителей
    val ALL = listOf(SOUTH, NORTH)

    /** Человекочитаемая подпись направления (куда едет поезд). */
    fun title(direction: String): String = when (direction) {
        SOUTH -> "К «Ботанической»"
        NORTH -> "К «Проспекту Космонавтов»"
        else -> direction
    }

    /** Конечная, куда идёт поезд. */
    fun terminal(direction: String): String = when (direction) {
        SOUTH -> "Ботаническая"
        NORTH -> "Проспект Космонавтов"
        else -> direction
    }

    /** Короткое имя конечной - для узких плашек. */
    fun terminalShort(direction: String): String = when (direction) {
        SOUTH -> "Ботаническая"
        NORTH -> "Космонавтов"
        else -> direction
    }
}

@Serializable
data class LineInfo(val name: String, val color: String)

@Serializable
data class Station(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
)

@Serializable
data class Schedule(
    val weekend: Map<String, Map<String, List<String>>> = emptyMap(),
    val weekday: Map<String, Map<String, List<String>>> = emptyMap(),
)

@Serializable
data class ScheduleDoc(
    val line: LineInfo,
    val stations: List<Station>,
    val schedule: Schedule,
)
