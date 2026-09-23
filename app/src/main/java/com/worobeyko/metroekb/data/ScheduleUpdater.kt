package com.worobeyko.metroekb.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Запись истории изменений графика. */
@Serializable
data class ScheduleChange(
    val timeMs: Long,
    /** "weekend" — изменились выходные (данные обновлены), "weekday" — сменилась картинка будней. */
    val kind: String,
    val stationId: String,
    val direction: String = "",
    val removed: List<String> = emptyList(),
    val added: List<String> = emptyList(),
    val note: String = "",
)

@Serializable
private data class MetaStation(val page: String, val weekdayImage: String, val lastModified: String? = null)

@Serializable
private data class ScheduleMeta(val checkedAt: String = "", val stations: Map<String, MetaStation> = emptyMap())

/**
 * Раз в сутки сверяет график с сайтом metro-ektb.ru:
 *  - выходные публикуются HTML-таблицами — разбираем, при отличиях сохраняем новый график
 *    (filesDir/schedule_override.json) и пишем, что поменялось, в историю;
 *  - будни — картинками: сравниваем Last-Modified; если картинку заменили, предупреждаем,
 *    что будни в приложении могут быть устаревшими (распознать картинку приложение не может).
 */
object ScheduleUpdater {
    private const val PREFS = "schedule_updater"
    private const val HISTORY_FILE = "schedule_history.json"
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val UA = "Mozilla/5.0 (Linux; Android) MetroEkb/1.0"

    /** Итог последней проверки для экрана настроек; null — ещё не проверяли. */
    val status = mutableStateOf<String?>(null)
    val running = mutableStateOf(false)

    fun lastCheckMs(context: Context): Long = prefs(context).getLong("lastCheck", 0L)
    fun weekdayOutdated(context: Context): Boolean = prefs(context).getBoolean("weekdayOutdated", false)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun history(context: Context): List<ScheduleChange> = try {
        val f = File(context.filesDir, HISTORY_FILE)
        if (f.exists()) ScheduleRepository.json.decodeFromString(ListSerializer(ScheduleChange.serializer()), f.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun appendHistory(context: Context, items: List<ScheduleChange>) {
        if (items.isEmpty()) return
        val all = (history(context) + items).takeLast(300)
        File(context.filesDir, HISTORY_FILE).writeText(
            ScheduleRepository.json.encodeToString(ListSerializer(ScheduleChange.serializer()), all)
        )
    }

    /** Проверить, если с прошлой проверки прошли сутки (или [force]). Сеть — в фоне. */
    suspend fun checkIfDue(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (running.value) return
        if (!force && System.currentTimeMillis() - lastCheckMs(app) < DAY_MS) return
        running.value = true
        try {
            status.value = withContext(Dispatchers.IO) { check(app) }
        } catch (e: Exception) {
            status.value = "Не удалось проверить: нет связи с сайтом"
        } finally {
            running.value = false
        }
    }

    private fun get(url: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", UA)
        }

    private fun check(context: Context): String {
        val meta = context.assets.open("schedule_meta.json").bufferedReader().use {
            ScheduleRepository.json.decodeFromString(ScheduleMeta.serializer(), it.readText())
        }
        val repo = ScheduleRepository.get(context)
        val now = System.currentTimeMillis()
        val changes = ArrayList<ScheduleChange>()

        // 1) Выходные: разбираем все станции; применяем, только если данные целые.
        val parsed = LinkedHashMap<String, Map<String, List<String>>>()
        repo.stations.forEachIndexed { index, st ->
            val page = meta.stations[st.id]?.page ?: return@forEachIndexed
            val html = get(page).inputStream.bufferedReader().use { it.readText() }
            parsed[st.id] = ScheduleParser.parseStation(html, index)
        }
        val sane = parsed.size == repo.stations.size &&
            parsed.values.all { dirs -> dirs.isNotEmpty() && dirs.values.all { it.size >= 50 } }
        var weekendChanged = false
        if (sane) {
            for ((sid, dirs) in parsed) {
                for (dir in Directions.ALL) {
                    val old = repo.doc.schedule.weekend[sid]?.get(dir).orEmpty()
                    val new = dirs[dir].orEmpty()
                    if (old != new) {
                        weekendChanged = true
                        changes += ScheduleChange(now, "weekend", sid, dir, removed = old - new.toSet(), added = new - old.toSet())
                    }
                }
            }
            if (weekendChanged) {
                val newDoc = repo.doc.copy(schedule = repo.doc.schedule.copy(weekend = parsed))
                File(context.filesDir, ScheduleRepository.OVERRIDE_FILE).writeText(
                    ScheduleRepository.json.encodeToString(ScheduleDoc.serializer(), newDoc)
                )
            }
        }

        // 2) Будни: картинки на сайте — следим за датой изменения.
        val p = prefs(context)
        var weekdayChanged = false
        for (st in repo.stations) {
            val m = meta.stations[st.id] ?: continue
            val conn = get(m.weekdayImage, "HEAD")
            val lm = conn.getHeaderField("Last-Modified") ?: continue
            conn.disconnect()
            val known = p.getString("lm_${st.id}", null) ?: m.lastModified
            if (known != null && lm != known) {
                weekdayChanged = true
                changes += ScheduleChange(now, "weekday", st.id, note = "Картинка графика будней на сайте обновлена ($lm)")
            }
            p.edit().putString("lm_${st.id}", lm).apply()
        }

        appendHistory(context, changes)
        p.edit()
            .putLong("lastCheck", now)
            .putBoolean("weekdayOutdated", p.getBoolean("weekdayOutdated", false) || weekdayChanged)
            .apply()
        if (weekendChanged) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { ScheduleRepository.reload(context) }
        }
        return when {
            !sane -> "Сайт ответил, но таблицы выходных не разобрались — оставил прежний график"
            weekendChanged && weekdayChanged -> "Выходные обновлены; будни на сайте тоже поменялись"
            weekendChanged -> "График выходных обновлён"
            weekdayChanged -> "Будни на сайте поменялись — в приложении могут быть старые данные"
            else -> "График совпадает с сайтом"
        }
    }
}
