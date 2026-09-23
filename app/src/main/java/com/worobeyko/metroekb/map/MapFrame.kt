package com.worobeyko.metroekb.map

import com.worobeyko.metroekb.data.Directions
import com.worobeyko.metroekb.data.ScheduleRepository
import com.worobeyko.metroekb.domain.ApproxEta
import com.worobeyko.metroekb.domain.DayType
import com.worobeyko.metroekb.domain.GhostTrain
import com.worobeyko.metroekb.domain.TrainPosition
import com.worobeyko.metroekb.domain.TrainRef
import com.worobeyko.metroekb.domain.TrainSimulator
import kotlin.math.roundToInt
import kotlin.math.sin

/** Вид плашки над поездом. */
enum class LabelStyle { NORMAL, CATCH, GHOST }

/** Поезд на карте в текущем кадре. */
data class TrainMarker(
    val lat: Double,
    val lon: Double,
    val bearing: Double,
    /** Directions.SOUTH / Directions.NORTH / [MapFrame.KIND_GHOST]. */
    val kind: String,
    val label: String,
    val labelStyle: LabelStyle,
    val opacity: Float,
    val ref: TrainRef?,
)

/** Круг-эффект (вспышка станции, ореол поезда, хвост призрака); радиусы в dp. */
data class EffectCircle(
    val lat: Double,
    val lon: Double,
    val radius: Float,
    val strokeWidth: Float,
    val strokeColor: Int,
    val strokeOpacity: Float,
    val fillColor: Int = 0,
    val fillOpacity: Float = 0f,
)

/** «Тепловой след» поезда: точки линии от хвоста следа к поезду (lat, lon). */
data class Trail(val direction: String, val points: List<Pair<Double, Double>>)

/** Всё, что меняется на карте покадрово. follow — куда вести камеру (lat, lon) или null. */
data class MapFrame(
    val trains: List<TrainMarker>,
    val effects: List<EffectCircle>,
    val follow: Pair<Double, Double>?,
    val trails: List<Trail> = emptyList(),
) {
    companion object {
        const val KIND_GHOST = "ghost"
    }
}

/** Станция пользователя и сколько до неё идти — для подсветки «успею?». */
data class CatchTarget(val stationIndex: Int, val walkSeconds: Int)

data class FrameOptions(
    val showTrains: Boolean,
    val pulses: Boolean,
    val catchTarget: CatchTarget?,
    val selected: TrainRef?,
    val following: Boolean,
    val ghost: Boolean,
    val trails: Boolean = false,
)

private const val PULSE_SEC = 2.5
/** След — сколько секунд пути позади поезда и не длиннее скольких метров. */
private const val TRAIL_SEC = 40.0
private const val TRAIL_MAX_M = 900.0
private const val GREEN = 0xFF00E676.toInt()
private const val GHOST_COLOR = 0xFFB9F6CA.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

fun directionColor(direction: String): Int =
    if (direction == Directions.NORTH) TrainPalette.north else TrainPalette.south

/**
 * Собирает кадр карты: поезда с плашками, вспышки прибытий, «успею?» (какой поезд догоняем
 * пешком, какие уже нет), выделенный/отслеживаемый поезд и ночного призрака.
 */
fun buildMapFrame(
    sim: TrainSimulator,
    repo: ScheduleRepository,
    dayType: DayType,
    nowMin: Double,
    epochMs: Long,
    opts: FrameOptions,
): MapFrame {
    val trains = if (opts.showTrains) sim.inflightTrains(dayType, nowMin) else emptyList()
    val effects = ArrayList<EffectCircle>()
    val stations = repo.stations

    // «Успею?»: для каждого направления поезда, идущие к станции пользователя, по времени
    // прихода на неё. Раньше, чем дойдёшь, — бледные; первый, на который успеваешь, — подсвечен.
    val missed = HashSet<TrainPosition>()
    val caught = HashMap<TrainPosition, Int>()
    opts.catchTarget?.let { ct ->
        for (direction in Directions.ALL) {
            // На конечную «по ходу» не сядешь — там поезд заканчивает рейс.
            val finalIndex = if (direction == Directions.SOUTH) stations.lastIndex else 0
            if (ct.stationIndex == finalIndex) continue
            val candidates = trains.filter { it.direction == direction }.mapNotNull { t ->
                sim.arrivalAt(TrainRef(direction, t.toIndex, t.arriveMin), ct.stationIndex, dayType)
                    ?.let { t to ((it - nowMin) * 60.0).roundToInt() }
            }.sortedBy { it.second }
            var found = false
            for ((t, secs) in candidates) {
                when {
                    secs < ct.walkSeconds -> missed += t
                    !found -> { caught[t] = secs; found = true }
                }
            }
        }
    }

    val selectedPos = opts.selected?.let { sim.locate(it, dayType, nowMin) }
    val pulse = (0.5 + 0.5 * sin(epochMs / 260.0)).toFloat()

    val markers = ArrayList<TrainMarker>(trains.size + 1)
    for (t in trains) {
        val catchSecs = caught[t]
        markers += TrainMarker(
            lat = t.lat,
            lon = t.lon,
            bearing = t.bearing,
            kind = t.direction,
            label = if (catchSecs != null) "успеете · ${ApproxEta.of(catchSecs).text}" else ApproxEta.of(t.etaSeconds).text,
            labelStyle = if (catchSecs != null) LabelStyle.CATCH else LabelStyle.NORMAL,
            opacity = if (t in missed) 0.4f else 1f,
            ref = t.ref,
        )
        if (catchSecs != null) {
            effects += EffectCircle(t.lat, t.lon, 17f + pulse * 3f, 2f, GREEN, 0.9f, GREEN, 0.18f)
        }
    }

    if (selectedPos != null) {
        effects += EffectCircle(selectedPos.lat, selectedPos.lon, 21f, 2.5f, WHITE, 0.95f)
    }

    // Вспышки: поезд прибыл на станцию — от неё расходятся два кольца цвета направления.
    if (opts.pulses && opts.showTrains) {
        for (e in sim.recentArrivals(dayType, nowMin, PULSE_SEC)) {
            val s = stations.getOrNull(e.stationIndex) ?: continue
            val k = (e.ageSec / PULSE_SEC).toFloat().coerceIn(0f, 1f)
            val color = directionColor(e.direction)
            effects += EffectCircle(s.lat, s.lon, 8f + k * 30f, 0.5f + 3.5f * (1 - k), color, 1 - k)
            effects += EffectCircle(s.lat, s.lon, 8f + k * 16f, 2f, color, 0.6f * (1 - k), color, 0.25f * (1 - k))
        }
    }

    if (opts.ghost) {
        GhostTrain.pose(repo.geometry, epochMs)?.let { g ->
            for (i in 6 downTo 1) {
                val tail = GhostTrain.pose(repo.geometry, epochMs, ageMs = i * 450L) ?: continue
                effects += EffectCircle(tail.lat, tail.lon, 7f - i * 0.8f, 0f, GHOST_COLOR, 0f, GHOST_COLOR, 0.35f - i * 0.05f)
            }
            val flicker = (0.55 + 0.35 * sin(epochMs / 90.0) * sin(epochMs / 37.0)).toFloat()
            markers += TrainMarker(g.lat, g.lon, g.bearing, MapFrame.KIND_GHOST, "служебный", LabelStyle.GHOST, flicker, null)
        }
    }

    // Тепловой след: кусок пути позади каждого поезда — сколько он проехал за TRAIL_SEC.
    val trails = if (!opts.trails) emptyList() else trains.map { t ->
        val g = repo.geometry
        val head = g.globalAt(t.fromIndex, t.toIndex, t.fraction)
        val legSec = ((t.arriveMin - t.departMin) * 60).coerceAtLeast(1)
        val back = (g.legLength(t.fromIndex, t.toIndex) / legSec * TRAIL_SEC).coerceAtMost(TRAIL_MAX_M)
        val tail = if (t.direction == Directions.SOUTH) head - back else head + back
        Trail(t.direction, g.slice(tail, head))
    }

    val follow = if (opts.following && selectedPos != null) selectedPos.lat to selectedPos.lon else null
    return MapFrame(markers, effects, follow, trails)
}
