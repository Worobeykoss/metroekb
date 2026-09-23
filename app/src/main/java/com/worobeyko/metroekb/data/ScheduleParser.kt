package com.worobeyko.metroekb.data

/**
 * Разбор страницы графика станции с metro-ektb.ru (порт tools/scrape_schedule.py):
 * каждая таблица — час в левой ячейке и минуты через «;» в правой; абзац перед таблицей
 * задаёт направление («в сторону Ботанической» / «в сторону Проспекта Космонавтов»).
 * На сайте HTML-таблицами публикуются только выходные.
 */
object ScheduleParser {
    private val OPTS = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    private val TABLE = Regex("<table.*?</table>", OPTS)
    private val PARA = Regex("<p[^>]*>(.*?)</p>", OPTS)
    private val ROW = Regex("<tr.*?</tr>", OPTS)
    private val CELL = Regex("<t[dh].*?</t[dh]>", OPTS)
    private val TAG = Regex("<[^>]+>")
    private val HOUR = Regex("\\d{1,2}")
    private val CAPTION_WORDS = listOf("сторону", "дни", "будн", "выходн", "рабоч")

    private fun clean(s: String): String =
        TAG.replace(s, "")
            .replace("&nbsp;", " ").replace("&laquo;", "«").replace("&raquo;", "»").replace("&amp;", "&")
            .trim()

    /** Таблицы страницы: (подпись над таблицей, час -> минуты). */
    fun parsePage(html: String): List<Pair<String, Map<Int, List<String>>>> {
        val out = ArrayList<Pair<String, Map<Int, List<String>>>>()
        var pending = ""
        var pos = 0
        for (m in TABLE.findAll(html)) {
            for (p in PARA.findAll(html.substring(pos, m.range.first))) {
                val c = clean(p.groupValues[1])
                if (c.isNotEmpty() && CAPTION_WORDS.any { c.lowercase().contains(it) }) pending = c
            }
            val data = LinkedHashMap<Int, List<String>>()
            for (row in ROW.findAll(m.value)) {
                val cells = CELL.findAll(row.value).map { clean(it.value) }.toList()
                if (cells.size >= 2 && cells[0].matches(HOUR)) {
                    data[cells[0].toInt()] = HOUR.findAll(cells[1].replace(".", " ")).map { it.value }.toList()
                }
            }
            out += pending to data
            pending = ""
            pos = m.range.last + 1
        }
        return out
    }

    /** Направление таблицы по подписи; у конечных одна таблица без явного направления. */
    fun directionOf(caption: String, stationIndex: Int): String {
        val low = caption.lowercase()
        return when {
            "ботаническ" in low -> Directions.SOUTH
            "космонавт" in low -> Directions.NORTH
            stationIndex == 0 -> Directions.SOUTH
            else -> Directions.NORTH
        }
    }

    /** Часы 0..2 — конец служебных суток: {5:[51], 0:[04]} -> [05:51, …, 00:04]. */
    fun flatten(hours: Map<Int, List<String>>): List<String> =
        hours.keys.sortedBy { if (it <= 2) it + 24 else it }.flatMap { h ->
            hours.getValue(h).map { "%02d:%02d".format(h, it.toInt()) }
        }

    /** Страница станции -> направление -> времена. */
    fun parseStation(html: String, stationIndex: Int): Map<String, List<String>> =
        parsePage(html).associate { (caption, hours) -> directionOf(caption, stationIndex) to flatten(hours) }
}
