package com.worobeyko.metroekb

import com.worobeyko.metroekb.data.Directions
import com.worobeyko.metroekb.data.ScheduleRepository
import com.worobeyko.metroekb.domain.ApproxEta
import com.worobeyko.metroekb.domain.DayType
import com.worobeyko.metroekb.domain.GhostTrain
import com.worobeyko.metroekb.domain.LineGeometry
import com.worobeyko.metroekb.domain.MetroClock
import com.worobeyko.metroekb.domain.TrainRef
import com.worobeyko.metroekb.domain.TrainSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalTime

/**
 * JVM-тесты ядра на РЕАЛЬНОМ bundled-расписании (assets/schedule.json).
 * Ключевая проверка - пример пользователя: Уралмаш, выходные 12:00 → 5 и 6 минут.
 */
class TrainSimulatorTest {

    private lateinit var repo: ScheduleRepository
    private lateinit var sim: TrainSimulator

    private fun min(h: Int, m: Int): Double = MetroClock.serviceMinute(LocalTime.of(h, m))

    @Before
    fun setUp() {
        // Рабочая директория unit-тестов Gradle = каталог модуля (app).
        val candidates = listOf(
            "src/main/assets/schedule.json",
            "app/src/main/assets/schedule.json",
        )
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("schedule.json не найден. Запустите tools/scrape_schedule.py")
        val geometry = File(file.parentFile, "geometry.json").takeIf { it.exists() }?.readText(Charsets.UTF_8)
        repo = ScheduleRepository.fromJson(file.readText(Charsets.UTF_8), geometry)
        sim = TrainSimulator(repo)
    }

    @Test
    fun weekend_uralmash_noon_matches_user_example() {
        val arr = sim.stationArrivals("uralmash", DayType.WEEKEND, min(12, 0))
        val south = arr.directions.first { it.direction == Directions.SOUTH }
        val north = arr.directions.first { it.direction == Directions.NORTH }

        assertEquals("12:05", south.next[0].hhmm)
        assertEquals(5, south.next[0].minutes)
        assertEquals("12:06", north.next[0].hhmm)
        assertEquals(6, north.next[0].minutes)

        // По 2 поезда в каждом направлении.
        assertTrue(south.next.size >= 2)
        assertTrue(north.next.size >= 2)

        // «Последний прошёл»: 11:55 (5 мин) на юг, 11:57 (3 мин) на север.
        assertEquals(5, south.sinceLastMin)
        assertEquals(3, north.sinceLastMin)
    }

    @Test
    fun after_midnight_ordering_is_chronological() {
        // 23:59 -> ближайший на юг с Уралмаша это 00:04 (через 5 минут), не утренний рейс.
        val arr = sim.stationArrivals("uralmash", DayType.WEEKEND, min(23, 59))
        val south = arr.directions.first { it.direction == Directions.SOUTH }
        assertEquals("00:04", south.next[0].hhmm)
        assertEquals(5, south.next[0].minutes)
    }

    @Test
    fun terminals_have_single_direction() {
        val prospekt = sim.stationArrivals("prospekt_kosmonavtov", DayType.WEEKEND, min(12, 0))
        val botanic = sim.stationArrivals("botanicheskaya", DayType.WEEKEND, min(12, 0))
        assertEquals(1, prospekt.directions.size)
        assertEquals(Directions.SOUTH, prospekt.directions[0].direction)
        assertEquals(1, botanic.directions.size)
        assertEquals(Directions.NORTH, botanic.directions[0].direction)
    }

    @Test
    fun inflight_trains_are_on_segments() {
        val trains = sim.inflightTrains(DayType.WEEKEND, min(12, 0))
        assertTrue("должны быть поезда в пути днём", trains.isNotEmpty())
        trains.forEach { t ->
            assertTrue(t.fraction in 0.0..1.0)
            assertTrue(t.etaMinutes >= 0)
            assertTrue(kotlin.math.abs(t.fromIndex - t.toIndex) == 1)
            // Координаты в пределах bbox Екатеринбурга.
            assertTrue(t.lat in 56.7..56.95)
            assertTrue(t.lon in 60.5..60.7)
        }
    }

    @Test
    fun no_trains_deep_night() {
        // 03:30 - метро закрыто, служебная минута = (3+24? нет: 3>2 -> 3)*60. Проверяем пустоту.
        val trains = sim.inflightTrains(DayType.WEEKEND, min(3, 30))
        assertTrue(trains.isEmpty())
        val arr = sim.stationArrivals("uralmash", DayType.WEEKEND, min(3, 30))
        // Утренние поезда ещё впереди - «следующий» есть, «последний» может отсутствовать.
        assertNotNull(arr)
    }

    @Test
    fun train_within_tolerance_is_still_arriving() {
        // Уралмаш, выходные: на юг поезд в 12:05. Через 20 с после графика он ещё «прибывает»,
        // через 40 с - уже ушёл и считается предыдущим.
        val at20 = sim.stationArrivals("uralmash", DayType.WEEKEND, min(12, 5) + 20 / 60.0)
        val south20 = at20.directions.first { it.direction == Directions.SOUTH }
        assertEquals("12:05", south20.next[0].hhmm)
        assertEquals(-20, south20.next[0].secondsUntil)
        assertTrue(ApproxEta.of(south20.next[0].secondsUntil).arriving)

        val at40 = sim.stationArrivals("uralmash", DayType.WEEKEND, min(12, 5) + 40 / 60.0)
        val south40 = at40.directions.first { it.direction == Directions.SOUTH }
        assertTrue(south40.next[0].hhmm != "12:05")
        assertEquals(0, south40.sinceLastMin)
    }

    @Test
    fun approx_eta_uses_half_minute_steps() {
        assertEquals("прибывает", ApproxEta.of(-25).text)
        assertEquals("прибывает", ApproxEta.of(30).text)
        assertEquals("~1 мин", ApproxEta.of(40).text)
        assertEquals("~1 мин", ApproxEta.of(74).text)
        assertEquals("~1½ мин", ApproxEta.of(80).text)
        assertEquals("~2½ мин", ApproxEta.of(150).text)
        assertEquals("~3 мин", ApproxEta.of(170).text)
        assertEquals("~10 мин", ApproxEta.of(599).text)
        assertEquals("~12 мин", ApproxEta.of(12 * 60 + 20).text)
    }

    @Test
    fun locate_is_consistent_with_inflight() {
        // Любой поезд на карте находится по своей «личности» сейчас и через 3.5 мин - на одном
        // из перегонов, которые показывает inflightTrains в тот момент.
        for (day in listOf(DayType.WEEKEND, DayType.WEEKDAY)) {
            if (!repo.hasData(day)) continue
            for (h in listOf(7, 12, 18, 23)) {
                val t0 = min(h, 10)
                for (t in sim.inflightTrains(day, t0)) {
                    val same = sim.locate(t.ref, day, t0)
                    assertNotNull(same)
                    assertEquals(t.fromIndex, same!!.fromIndex)
                    assertEquals(t.departMin, same.departMin)
                    val later = sim.locate(t.ref, day, t0 + 3.5) ?: continue
                    assertTrue(
                        "поезд ${t.ref} через 3.5 мин должен быть среди inflight",
                        sim.inflightTrains(day, t0 + 3.5).any {
                            it.direction == later.direction && it.fromIndex == later.fromIndex && it.departMin == later.departMin
                        },
                    )
                }
            }
        }
    }

    @Test
    fun arrivalAt_matches_located_leg() {
        // Уралмаш (1), выходные, на юг в 12:05 -> прибытие на Машиностроителей (2).
        val ref = TrainRef(Directions.SOUTH, 1, MetroClock.serviceMinute("12:05"))
        val at = sim.arrivalAt(ref, 2, DayType.WEEKEND)
        assertNotNull(at)
        val leg = sim.locate(ref, DayType.WEEKEND, ref.minute + 0.1)
        assertEquals(leg!!.arriveMin, at)
        // Станция позади - null.
        assertNull(sim.arrivalAt(ref, 0, DayType.WEEKEND))
    }

    @Test
    fun trains_reach_both_terminals() {
        // Последний перегон к конечной достраивается - поезда доезжают до Ботанической и Проспекта.
        val last = repo.stations.lastIndex
        var south = false
        var north = false
        var m = min(12, 0)
        while (m < min(12, 30)) {
            val trains = sim.inflightTrains(DayType.WEEKEND, m)
            south = south || trains.any { it.direction == Directions.SOUTH && it.toIndex == last }
            north = north || trains.any { it.direction == Directions.NORTH && it.toIndex == 0 }
            m += 0.25
        }
        assertTrue("на юг до Ботанической", south)
        assertTrue("на север до Проспекта Космонавтов", north)
    }

    @Test
    fun recent_arrivals_see_scheduled_train() {
        // Уралмаш, выходные, на юг 12:05: через 1 с после графика - свежее прибытие.
        val events = sim.recentArrivals(DayType.WEEKEND, MetroClock.serviceMinute("12:05") + 1 / 60.0, 2.5)
        assertTrue(events.any { it.stationIndex == 1 && it.direction == Directions.SOUTH && it.ageSec in 0.5..1.5 })
    }

    @Test
    fun ghost_rides_between_terminals() {
        val first = repo.stations.first()
        val last = repo.stations.last()
        val start = GhostTrain.pose(repo.geometry, 0)!!
        assertEquals(first.lat, start.lat, 1e-6)
        val end = GhostTrain.pose(repo.geometry, 6 * 60_000L)!!
        assertEquals(last.lat, end.lat, 1e-6)
        assertTrue(GhostTrain.isGhostHour(LocalTime.of(3, 0)))
        assertTrue(!GhostTrain.isGhostHour(LocalTime.of(12, 0)))
    }

    @Test
    fun geometry_is_loaded_and_trains_stay_on_it() {
        // Настоящая форма линии длиннее прямых между станциями (изгибы), но ненамного.
        val straight = repo.stations.zipWithNext { a, b -> LineGeometry.distanceM(a.lat, a.lon, b.lat, b.lon) }.sum()
        assertTrue(repo.geometry.segmentCount == repo.stations.size - 1)
        assertTrue(repo.geometry.totalLength >= straight - 1)
        assertTrue(repo.geometry.totalLength < straight * 1.2)
        // Концы перегонов - ровно в станциях.
        for (i in repo.stations.indices) {
            val p = repo.geometry.pointAt(repo.geometry.stationGlobal(i))
            assertTrue(LineGeometry.distanceM(p.lat, p.lon, repo.stations[i].lat, repo.stations[i].lon) < 1.0)
        }
    }

    @Test
    fun travel_times_are_sane() {
        // Уралмаш -> Геологическая днём в будни: несколько минут, и туда примерно как обратно.
        assumeTrue(repo.hasData(DayType.WEEKDAY))
        val there = sim.travelMinutes(1, 6, DayType.WEEKDAY)!!
        val back = sim.travelMinutes(6, 1, DayType.WEEKDAY)!!
        assertTrue(there in 8..20)
        assertTrue(kotlin.math.abs(there - back) <= 2)
        assertNull(sim.travelMinutes(3, 3, DayType.WEEKDAY))
    }

    @Test
    fun trains_per_hour_peaks_in_rush_hour() {
        assumeTrue(repo.hasData(DayType.WEEKDAY))
        val (rush, max) = sim.trainsPerHour("dinamo", DayType.WEEKDAY, min(8, 0))
        val (noon, _) = sim.trainsPerHour("dinamo", DayType.WEEKDAY, min(13, 0))
        assertTrue(rush > noon)
        assertTrue(rush <= max)
    }

    @Test
    fun weekday_example_if_data_present() {
        // Пример пользователя: Уралмаш, вторник 19:45 -> с Проспекта 4 мин, с Машиностроителей 3 мин.
        assumeTrue("будни ещё не загружены", repo.hasData(DayType.WEEKDAY))
        val arr = sim.stationArrivals("uralmash", DayType.WEEKDAY, min(19, 45))
        val south = arr.directions.first { it.direction == Directions.SOUTH }
        val north = arr.directions.first { it.direction == Directions.NORTH }
        assertEquals(4, south.next[0].minutes)
        assertEquals(3, north.next[0].minutes)
    }
}
