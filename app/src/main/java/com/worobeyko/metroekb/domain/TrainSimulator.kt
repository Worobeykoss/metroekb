package com.worobeyko.metroekb.domain

import com.worobeyko.metroekb.data.Directions
import com.worobeyko.metroekb.data.ScheduleRepository
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Один ближайший поезд: время по графику и сколько до него. secondsUntil бывает отрицательным
 * (до −[ApproxEta.TOLERANCE_SEC]): время по графику уже наступило, но поезд ещё может подходить.
 */
data class Arrival(val hhmm: String, val minutes: Int, val secondsUntil: Int)

/** Ближайшие поезда и «сколько прошло с последнего» по одному направлению. */
data class DirectionArrivals(
    val direction: String,
    val next: List<Arrival>,
    val sinceLastMin: Int?,
)

data class StationArrivals(
    val stationId: String,
    val directions: List<DirectionArrivals>,
)

/**
 * «Личность» конкретного поезда: направление и служебная минута, в которую он проходит станцию
 * stationIndex. По ней поезд однозначно находится на любом следующем перегоне ([TrainSimulator.locate]).
 */
data class TrainRef(val direction: String, val stationIndex: Int, val minute: Int)

/** Поезд, едущий по перегону from->to; позиция уже переведена в координаты. */
data class TrainPosition(
    val direction: String,
    val fromIndex: Int,
    val toIndex: Int,
    val lat: Double,
    val lon: Double,
    val bearing: Double,
    val fraction: Double,
    val etaMinutes: Int,
    /** Сколько секунд до прибытия на следующую станцию (toIndex). */
    val etaSeconds: Int,
    /** Служебные минуты отправления с fromIndex и прибытия на toIndex. */
    val departMin: Int,
    val arriveMin: Int,
) {
    val ref: TrainRef get() = TrainRef(direction, fromIndex, departMin)
}

/** Поезд только что прибыл на станцию (для «вспышки»): ageSec — сколько секунд назад. */
data class ArrivalEvent(val stationIndex: Int, val direction: String, val ageSec: Double)

/**
 * Ядро: из одного расписания даёт
 *  1) панель прибытий по станции (ближайшие поезда в каждом направлении + «прошло N мин»);
 *  2) поезда «в пути» на перегонах для анимации на карте;
 *  3) слежение за конкретным поездом, прибытия (вспышки), последний поезд.
 * Времена предпосчитываются и кэшируются по типу дня (inflight вызывается покадрово).
 * Чистая логика без Android — тестируется JVM-тестами.
 */
class TrainSimulator(private val repo: ScheduleRepository) {

    /** Отсортированные по возрастанию служебные минуты + их подписи "HH:MM". */
    private class DirTimes(val minutes: IntArray, val labels: Array<String>)

    private class DayData(
        /** stationId -> direction -> времена прохода. */
        val times: Map<String, Map<String, DirTimes>>,
        /** direction -> длительность перегона к конечной, у которой нет своей таблицы. */
        val terminalLegMin: Map<String, Int>,
    )

    private val cache = HashMap<DayType, DayData>()

    private val stations get() = repo.stations

    private fun day(dayType: DayType): DayData = cache.getOrPut(dayType) {
        val times = repo.stations.associate { station ->
            station.id to buildMap {
                for (direction in Directions.ALL) {
                    val raw = repo.times(dayType, station.id, direction)
                    if (raw.isEmpty()) continue
                    val sorted = raw.map { it to MetroClock.serviceMinute(it) }.sortedBy { it.second }
                    put(
                        direction,
                        DirTimes(
                            minutes = IntArray(sorted.size) { sorted[it].second },
                            labels = Array(sorted.size) { sorted[it].first },
                        ),
                    )
                }
            }
        }
        DayData(times, terminalLegs(times))
    }

    /**
     * У конечной нет таблицы прибытия (там только отправления в обратную сторону), поэтому
     * время последнего перегона берём из обратного направления: медиана «конечная -> соседняя».
     */
    private fun terminalLegs(times: Map<String, Map<String, DirTimes>>): Map<String, Int> {
        if (stations.size < 2) return emptyMap()
        fun median(from: String, to: String, dir: String): Int? {
            val a = times[from]?.get(dir)?.minutes ?: return null
            val b = times[to]?.get(dir)?.minutes ?: return null
            val diffs = a.asList().mapNotNull { t -> b.firstOrNull { it > t }?.minus(t)?.takeIf { it in 1..10 } }
            return diffs.sorted().getOrNull(diffs.size / 2)
        }
        val first = stations.first().id
        val second = stations[1].id
        val last = stations.last().id
        val beforeLast = stations[stations.size - 2].id
        return buildMap {
            // На юг последний перегон -> Ботаническая: длительность как Ботаническая -> Чкаловская на север.
            median(last, beforeLast, Directions.NORTH)?.let { put(Directions.SOUTH, it) }
            median(first, second, Directions.SOUTH)?.let { put(Directions.NORTH, it) }
        }
    }

    private fun step(direction: String) = if (direction == Directions.SOUTH) 1 else -1

    private fun dirTimes(d: DayData, index: Int, direction: String): IntArray? =
        stations.getOrNull(index)?.let { d.times[it.id]?.get(direction)?.minutes }

    /** Первый индекс с times[i] >= value. */
    private fun lowerBound(times: IntArray, value: Double): Int {
        var lo = 0
        var hi = times.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] < value) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * Время прибытия на bIdx поезда, прошедшего aIdx в tA. Берём ближайший проход по B строго
     * между tA и следующим проходом по A — так поезд приходит на B ровно в момент из таблицы B.
     */
    private fun legArrival(d: DayData, direction: String, aIdx: Int, tA: Int, bIdx: Int): Int? {
        val aTimes = dirTimes(d, aIdx, direction) ?: return null
        val bTimes = dirTimes(d, bIdx, direction)
        if (bTimes == null) {
            // Конечная без таблицы: достраиваем по длительности перегона.
            val isTerminal = bIdx == 0 || bIdx == stations.lastIndex
            val leg = d.terminalLegMin[direction]
            return if (isTerminal && leg != null) tA + leg else null
        }
        val k = lowerBound(aTimes, tA.toDouble() + 1)
        val nextTA = if (k < aTimes.size) aTimes[k] else Int.MAX_VALUE
        val j = lowerBound(bTimes, tA.toDouble() + 1)
        return if (j < bTimes.size && bTimes[j] < nextTA) bTimes[j] else null
    }

    private fun position(direction: String, aIdx: Int, bIdx: Int, tA: Int, tB: Int, nowMin: Double): TrainPosition {
        val frac = ((nowMin - tA) / (tB - tA)).coerceIn(0.0, 1.0)
        // Поезд идёт по настоящей форме пути (изгибы тоннеля), а не по прямой между станциями.
        val pose = repo.geometry.pose(aIdx, bIdx, frac)
        return TrainPosition(
            direction = direction,
            fromIndex = aIdx,
            toIndex = bIdx,
            lat = pose.lat,
            lon = pose.lon,
            bearing = pose.bearing,
            fraction = frac,
            etaMinutes = ceil(tB - nowMin).toInt(),
            etaSeconds = ((tB - nowMin) * 60.0).roundToInt(),
            departMin = tA,
            arriveMin = tB,
        )
    }

    /** Панель станции: для каждого направления ближайшие поезда (count штук) и время с последнего. */
    fun stationArrivals(stationId: String, dayType: DayType, nowMin: Double, count: Int = 2): StationArrivals {
        val perStation = day(dayType).times[stationId] ?: return StationArrivals(stationId, emptyList())
        val result = ArrayList<DirectionArrivals>(2)
        for (direction in Directions.ALL) {
            val dt = perStation[direction] ?: continue
            val mins = dt.minutes
            // mins отсортированы; всё до idx — прошедшие, с idx — предстоящие. Поезд, чьё время
            // по графику наступило меньше TOLERANCE_SEC назад, ещё может подходить — он остаётся
            // в ближайших («прибывает»), а не пропадает раньше, чем реально придёт.
            val cutoff = nowMin - ApproxEta.TOLERANCE_SEC / 60.0
            val idx = lowerBound(mins, cutoff)
            val since = if (idx > 0) floor(nowMin - mins[idx - 1]).toInt() else null
            val next = ArrayList<Arrival>(count)
            var j = idx
            while (j < mins.size && next.size < count) {
                val secs = ((mins[j] - nowMin) * 60.0).roundToInt()
                next.add(Arrival(dt.labels[j], ceil(mins[j] - nowMin).toInt().coerceAtLeast(0), secs))
                j++
            }
            result.add(DirectionArrivals(direction, next, since))
        }
        return StationArrivals(stationId, result)
    }

    /** Последний поезд дня со станции в направлении: служебная минута и подпись "HH:MM". */
    fun lastTrain(stationId: String, dayType: DayType, direction: String): Pair<Int, String>? {
        val dt = day(dayType).times[stationId]?.get(direction) ?: return null
        if (dt.minutes.isEmpty()) return null
        return dt.minutes.last() to dt.labels.last()
    }

    /**
     * Поезда на перегонах. Для соседней пары A->B по направлению берём последнее отправление
     * tA <= now из таблицы A и его прибытие tB на B ([legArrival]). Если now в [tA, tB), поезд на
     * перегоне; доля пути = (now - tA)/(tB - tA).
     */
    fun inflightTrains(dayType: DayType, nowMin: Double): List<TrainPosition> {
        val d = day(dayType)
        val n = stations.size
        val out = ArrayList<TrainPosition>()
        for (direction in Directions.ALL) {
            val s = step(direction)
            val range = if (s > 0) 0 until n - 1 else (n - 1 downTo 1)
            for (aIdx in range) {
                val bIdx = aIdx + s
                val aTimes = dirTimes(d, aIdx, direction) ?: continue
                val k = lowerBound(aTimes, nowMin + 1e-9) - 1 // последнее отправление <= now
                if (k < 0) continue
                val tA = aTimes[k]
                val tB = legArrival(d, direction, aIdx, tA, bIdx) ?: continue
                if (nowMin >= tB) continue
                out += position(direction, aIdx, bIdx, tA, tB, nowMin)
            }
        }
        return out
    }

    /**
     * Где сейчас поезд [ref]: идём от его станции по ходу движения, перегон за перегоном, пока не
     * найдём тот, на котором он едет. null — ещё не отправился или уже прибыл на конечную.
     */
    fun locate(ref: TrainRef, dayType: DayType, nowMin: Double): TrainPosition? {
        if (nowMin < ref.minute) return null
        val d = day(dayType)
        var idx = ref.stationIndex
        var t = ref.minute
        while (true) {
            val next = idx + step(ref.direction)
            if (next !in stations.indices) return null
            val tNext = legArrival(d, ref.direction, idx, t, next) ?: return null
            if (nowMin < tNext) return position(ref.direction, idx, next, t, tNext, nowMin)
            idx = next
            t = tNext
        }
    }

    /** Служебная минута, когда поезд [ref] будет на станции target; null — станция позади или не по пути. */
    fun arrivalAt(ref: TrainRef, target: Int, dayType: DayType): Int? {
        val s = step(ref.direction)
        if ((target - ref.stationIndex) * s < 0) return null
        val d = day(dayType)
        var idx = ref.stationIndex
        var t = ref.minute
        while (idx != target) {
            val next = idx + s
            t = legArrival(d, ref.direction, idx, t, next) ?: return null
            idx = next
        }
        return t
    }

    /** Прибытия за последние windowSec секунд — для «вспышек» станций. */
    fun recentArrivals(dayType: DayType, nowMin: Double, windowSec: Double): List<ArrivalEvent> {
        val d = day(dayType)
        val from = nowMin - windowSec / 60.0
        val out = ArrayList<ArrivalEvent>()
        for (direction in Directions.ALL) {
            val s = step(direction)
            val range = if (s > 0) 1 until stations.size else (stations.size - 2 downTo 0)
            for (idx in range) {
                val times = dirTimes(d, idx, direction)
                if (times != null) {
                    var i = lowerBound(times, from)
                    while (i < times.size && times[i] <= nowMin) {
                        if (times[i] > from) out += ArrivalEvent(idx, direction, (nowMin - times[i]) * 60.0)
                        i++
                    }
                } else {
                    // Конечная без таблицы: прибытия = отправления с соседней + длительность перегона.
                    val leg = d.terminalLegMin[direction] ?: continue
                    val prev = dirTimes(d, idx - s, direction) ?: continue
                    var i = lowerBound(prev, from - leg)
                    while (i < prev.size && prev[i] + leg <= nowMin) {
                        val t = prev[i] + leg
                        if (t > from) out += ArrivalEvent(idx, direction, (nowMin - t) * 60.0)
                        i++
                    }
                }
            }
        }
        return out
    }

    /**
     * Типичное время в пути между станциями, минуты: медиана по поездам, отправляющимся
     * с [from] днём (10:00–16:00). null — на одной станции или нет поездов.
     */
    fun travelMinutes(from: Int, to: Int, dayType: DayType): Int? {
        if (from == to) return null
        val direction = if (to > from) Directions.SOUTH else Directions.NORTH
        val times = dirTimes(day(dayType), from, direction) ?: return null
        val durations = times.filter { it in 600..960 }.mapNotNull { t ->
            arrivalAt(TrainRef(direction, from, t), to, dayType)?.minus(t)
        }.sorted()
        return durations.getOrNull(durations.size / 2)
    }

    /**
     * Сколько поездов проходит станцию за час вокруг [nowMin] (оба направления) и максимум
     * такого за день — для грубой оценки загруженности: поездов пускают больше, когда людей больше.
     */
    fun trainsPerHour(stationId: String, dayType: DayType, nowMin: Double): Pair<Int, Int> {
        val all = Directions.ALL.flatMap { d -> day(dayType).times[stationId]?.get(d)?.minutes?.asList().orEmpty() }
        if (all.isEmpty()) return 0 to 0
        fun around(m: Double) = all.count { it >= m - 30 && it < m + 30 }
        val max = (all.min()..all.max() step 15).maxOf { around(it.toDouble()) }
        return around(nowMin) to max
    }

    /** Первое и последнее отправление за служебные сутки по всей линии (служебные минуты). */
    fun serviceSpan(dayType: DayType): Pair<Int, Int>? {
        val all = day(dayType).times.values.flatMap { m -> m.values.flatMap { it.minutes.asList() } }
        if (all.isEmpty()) return null
        return all.min() to all.max()
    }
}
