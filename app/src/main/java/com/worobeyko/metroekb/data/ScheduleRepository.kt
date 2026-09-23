package com.worobeyko.metroekb.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.worobeyko.metroekb.domain.DayType
import com.worobeyko.metroekb.domain.LineGeometry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class GeometryDoc(val source: String = "", val segments: List<List<List<Double>>> = emptyList())

/**
 * Загружает и кэширует расписание: assets/schedule.json или, если автообновление нашло на
 * сайте новый график, filesDir/schedule_override.json. Единый источник данных о станциях,
 * координатах, форме линии и временах прохода поездов.
 */
class ScheduleRepository private constructor(val doc: ScheduleDoc, geometry: LineGeometry?) {

    val line: LineInfo get() = doc.line
    val stations: List<Station> get() = doc.stations
    val idToIndex: Map<String, Int> =
        doc.stations.mapIndexed { index, station -> station.id to index }.toMap()

    /** Форма линии; без данных — прямые между станциями. */
    val geometry: LineGeometry = geometry ?: LineGeometry.straight(doc.stations.map { it.lat to it.lon })

    fun station(id: String): Station? = doc.stations.firstOrNull { it.id == id }

    /** Времена ("HH:MM") прохода поезда через станцию в заданном направлении. */
    fun times(dayType: DayType, stationId: String, direction: String): List<String> {
        val table = when (dayType) {
            DayType.WEEKEND -> doc.schedule.weekend
            DayType.WEEKDAY -> doc.schedule.weekday
        }
        return table[stationId]?.get(direction) ?: emptyList()
    }

    /** Есть ли данные для будней (заполняются отдельно из скриншота). */
    fun hasData(dayType: DayType): Boolean = when (dayType) {
        DayType.WEEKEND -> doc.schedule.weekend.isNotEmpty()
        DayType.WEEKDAY -> doc.schedule.weekday.isNotEmpty()
    }

    companion object {
        const val OVERRIDE_FILE = "schedule_override.json"

        @Volatile
        private var instance: ScheduleRepository? = null
        val json = Json { ignoreUnknownKeys = true }

        /** Растёт при каждой подмене графика — экран пересоздаёт симулятор. */
        val version = mutableIntStateOf(0)

        fun get(context: Context): ScheduleRepository =
            instance ?: synchronized(this) {
                instance ?: load(context).also { instance = it }
            }

        /** Перечитать (после автообновления графика). */
        fun reload(context: Context) {
            synchronized(this) { instance = load(context) }
            version.intValue++
        }

        /** Для JVM-тестов: собрать репозиторий из уже разобранного документа. */
        fun fromDoc(doc: ScheduleDoc, geometry: LineGeometry? = null): ScheduleRepository = ScheduleRepository(doc, geometry)

        /** Для JVM-тестов: разобрать JSON-текст расписания (и, если есть, геометрии). */
        fun fromJson(text: String, geometryText: String? = null): ScheduleRepository =
            ScheduleRepository(json.decodeFromString(ScheduleDoc.serializer(), text), geometryText?.let(::parseGeometry))

        fun parseGeometry(text: String): LineGeometry? = try {
            val g = json.decodeFromString(GeometryDoc.serializer(), text)
            g.segments.takeIf { it.isNotEmpty() }?.let { segs ->
                LineGeometry(segs.map { seg -> seg.map { it[0] to it[1] } })
            }
        } catch (_: Exception) {
            null
        }

        private fun load(context: Context): ScheduleRepository {
            val override = File(context.filesDir, OVERRIDE_FILE)
            val doc = try {
                if (override.exists()) json.decodeFromString(ScheduleDoc.serializer(), override.readText()) else null
            } catch (_: Exception) {
                null
            } ?: context.assets.open("schedule.json").bufferedReader(Charsets.UTF_8).use {
                json.decodeFromString(ScheduleDoc.serializer(), it.readText())
            }
            val geometry = try {
                context.assets.open("geometry.json").bufferedReader(Charsets.UTF_8).use { parseGeometry(it.readText()) }
            } catch (_: Exception) {
                null
            }
            // Геометрия валидна, только если перегонов ровно на один меньше, чем станций.
            return ScheduleRepository(doc, geometry?.takeIf { it.segmentCount == doc.stations.size - 1 && it.totalLength > 0 })
        }
    }
}
