package com.worobeyko.metroekb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worobeyko.metroekb.data.Directions
import com.worobeyko.metroekb.data.ScheduleRepository
import com.worobeyko.metroekb.data.ScheduleUpdater
import com.worobeyko.metroekb.domain.DayType
import com.worobeyko.metroekb.domain.TrainSimulator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Короткие подписи станций для таблицы. */
private val SHORT = mapOf(
    "prospekt_kosmonavtov" to "ПК", "uralmash" to "Урм", "mashinostroiteley" to "Маш",
    "uralskaya" to "Урс", "dinamo" to "Дин", "ploschad_1905" to "1905",
    "geologicheskaya" to "Гео", "chkalovskaya" to "Чкл", "botanicheskaya" to "Бот",
)

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack, "Назад",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(26.dp).clickable { onBack() },
        )
        Spacer(Modifier.size(10.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Таблица «сколько ехать от станции до станции»: медиана по дневным поездам графика. */
@Composable
fun TravelTimesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ScheduleRepository.get(context) }
    val sim = remember(repo) { TrainSimulator(repo) }
    var day by remember { mutableStateOf(DayType.WEEKDAY) }
    val n = repo.stations.size
    val table = remember(day, repo) { Array(n) { i -> IntArray(n) { j -> sim.travelMinutes(i, j, day) ?: -1 } } }
    val maxMin = table.maxOf { row -> row.max() }.coerceAtLeast(1)
    var picked by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        ScreenHeader("Время в пути", onBack)
        Spacer(Modifier.size(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(DayType.WEEKDAY to "Будни", DayType.WEEKEND to "Выходные").forEach { (d, t) ->
                Text(
                    t, fontSize = 14.sp,
                    fontWeight = if (d == day) FontWeight.Bold else FontWeight.Normal,
                    color = if (d == day) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { day = d }
                        .background(if (d == day) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text("откуда ↓   куда →", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(4.dp))

        val cell = 34.dp
        Row {
            Spacer(Modifier.width(40.dp))
            repo.stations.forEach { s ->
                Text(SHORT[s.id] ?: s.name.take(3), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.width(cell))
            }
        }
        repo.stations.forEachIndexed { i, from ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(SHORT[from.id] ?: from.name.take(3), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                repo.stations.forEachIndexed { j, to ->
                    val v = table[i][j]
                    val sel = picked == (i to j)
                    Box(
                        Modifier
                            .size(cell)
                            .padding(1.5.dp)
                            .background(
                                when {
                                    i == j -> Color(0xFF1B2030)
                                    sel -> Color(0xFFFFB300)
                                    v < 0 -> Color(0xFF262B38)
                                    else -> lerp(Color(0xFF1F3A5A), Color(0xFF2B6CB0), v.toFloat() / maxMin)
                                },
                                RoundedCornerShape(5.dp),
                            )
                            .semantics { contentDescription = if (v >= 0) "${from.name} - ${to.name}: $v мин" else "${from.name}" }
                            .clickable(enabled = v >= 0) { picked = i to j },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (i == j || v < 0) "·" else "$v", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (sel) Color(0xFF16181F) else Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.size(14.dp))
        picked?.let { (i, j) ->
            val dir = if (j > i) Directions.SOUTH else Directions.NORTH
            Text("${repo.stations[i].name} → ${repo.stations[j].name}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text("в пути ~${table[i][j]} мин, в сторону «${Directions.terminal(dir)}», ${kotlin.math.abs(j - i)} перегон(а/ов)",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } ?: Text("Нажмите на клетку - покажу подробнее.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(6.dp))
        Text("Время по графику, днём; без ожидания поезда.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** История изменений графика: что нашла автопроверка сайта. */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ScheduleRepository.get(context) }
    val scope = rememberCoroutineScope()
    val running by ScheduleUpdater.running
    val status by ScheduleUpdater.status
    val history = remember(running) { ScheduleUpdater.history(context).asReversed() }
    val fmt = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")) }
    val lastCheck = ScheduleUpdater.lastCheckMs(context)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("История графика", onBack)
        Spacer(Modifier.size(12.dp))
        Text(
            if (lastCheck > 0) "Последняя проверка сайта: ${fmt.format(Date(lastCheck))}" else "Сайт ещё не проверялся",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        status?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
        if (ScheduleUpdater.weekdayOutdated(context)) {
            Text("⚠ Будни на сайте менялись после сборки приложения - время по будням может быть неточным.",
                fontSize = 13.sp, color = Color(0xFFFF8A80))
        }
        Spacer(Modifier.size(8.dp))
        Text(
            if (running) "Проверяю…" else "Проверить сейчас",
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier
                .clickable(enabled = !running) { scope.launch { ScheduleUpdater.checkIfDue(context, force = true) } }
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
        Spacer(Modifier.size(18.dp))
        if (history.isEmpty()) {
            Text("Изменений пока не было: график в приложении совпадает с сайтом.", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        history.forEach { h ->
            val station = repo.station(h.stationId)?.name ?: h.stationId
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(fmt.format(Date(h.timeMs)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    buildString {
                        append(station)
                        if (h.direction.isNotEmpty()) append(" → ${Directions.terminal(h.direction)}")
                        append(if (h.kind == "weekend") " · выходные" else " · будни")
                    },
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                )
                if (h.note.isNotEmpty()) Text(h.note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                if (h.removed.isNotEmpty()) Text("убрали: ${h.removed.joinToString(" ")}", fontSize = 12.sp, color = Color(0xFFFF8A80))
                if (h.added.isNotEmpty()) Text("добавили: ${h.added.joinToString(" ")}", fontSize = 12.sp, color = Color(0xFF69F0AE))
            }
        }
        Spacer(Modifier.size(24.dp))
    }
}
