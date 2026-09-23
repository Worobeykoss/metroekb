package com.worobeyko.metroekb

import com.worobeyko.metroekb.data.Directions
import com.worobeyko.metroekb.data.ScheduleParser
import com.worobeyko.metroekb.data.ScheduleRepository
import com.worobeyko.metroekb.domain.Crowd
import com.worobeyko.metroekb.domain.StationScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Парсер страниц metro-ektb.ru (для автообновления) на сохранённых настоящих страницах:
 * результат должен совпасть с графиком выходных в assets/schedule.json.
 */
class ScheduleParserTest {

    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText(Charsets.UTF_8)

    private val repo by lazy {
        val file = listOf("src/main/assets/schedule.json", "app/src/main/assets/schedule.json").map(::File).first { it.exists() }
        ScheduleRepository.fromJson(file.readText(Charsets.UTF_8))
    }

    @Test
    fun parses_two_direction_station_like_bundled_weekend() {
        val parsed = ScheduleParser.parseStation(resource("page_uralmash.html"), stationIndex = 1)
        assertEquals(setOf(Directions.SOUTH, Directions.NORTH), parsed.keys)
        assertEquals(repo.doc.schedule.weekend["uralmash"]!![Directions.SOUTH], parsed[Directions.SOUTH])
        assertEquals(repo.doc.schedule.weekend["uralmash"]!![Directions.NORTH], parsed[Directions.NORTH])
    }

    @Test
    fun terminal_page_has_single_direction() {
        val parsed = ScheduleParser.parseStation(resource("page_prospekt_kosmonavtov.html"), stationIndex = 0)
        assertEquals(setOf(Directions.SOUTH), parsed.keys)
        assertEquals(repo.doc.schedule.weekend["prospekt_kosmonavtov"]!![Directions.SOUTH], parsed[Directions.SOUTH])
    }

    @Test
    fun flatten_puts_night_hours_last() {
        assertEquals(listOf("23:50", "00:04"), ScheduleParser.flatten(mapOf(0 to listOf("4"), 23 to listOf("50"))))
    }

    @Test
    fun scheme_splits_exits_by_platform_end() {
        // Линия идёт на юг (курс 180°): выход севернее станции — у «северного» конца.
        val exits = StationScheme.exits(
            56.85, 60.60, 180.0,
            listOf(
                Triple("1", 56.8510 to 60.6000, false), // ~110 м севернее
                Triple("3", 56.8490 to 60.6000, true), // ~110 м южнее
            ),
        )
        val (headSouth, tailSouth) = StationScheme.headAndTail(exits, south = true)
        assertEquals(listOf("3"), headSouth.map { it.ref })
        assertEquals(listOf("1"), tailSouth.map { it.ref })
        assertTrue(exits.first { it.ref == "3" }.accessible)
    }

    @Test
    fun crowd_levels() {
        assertEquals(Crowd.HIGH, Crowd.of(26, 28))
        assertEquals(Crowd.LOW, Crowd.of(10, 28))
        assertEquals(null, Crowd.of(0, 28))
    }
}
