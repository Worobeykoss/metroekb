package com.worobeyko.metroekb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worobeyko.metroekb.data.Directions
import com.worobeyko.metroekb.data.ScheduleRepository
import com.worobeyko.metroekb.domain.DayType

private fun serviceHour(h: Int) = if (h <= 2) h + 24 else h

private fun byHour(times: List<String>): Map<Int, String> {
    val m = LinkedHashMap<Int, MutableList<String>>()
    for (t in times) {
        val h = t.substringBefore(':').toInt()
        m.getOrPut(h) { mutableListOf() }.add(t.substringAfter(':'))
    }
    return m.mapValues { it.value.joinToString("  ") }
}

@Composable
fun ScheduleScreen(onBack: () -> Unit, onOpenTravelTimes: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ScheduleRepository.get(context) }
    var stationIndex by remember { mutableStateOf(1) } // Уралмаш по умолчанию
    var day by remember { mutableStateOf(DayType.WEEKDAY) }

    val station = repo.stations[stationIndex]
    val south = byHour(repo.times(day, station.id, Directions.SOUTH))
    val north = byHour(repo.times(day, station.id, Directions.NORTH))
    val hours = (south.keys + north.keys).distinct().sortedBy { serviceHour(it) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.size(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Назад",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(26.dp).clickable { onBack() },
            )
            Spacer(Modifier.size(10.dp))
            Text("График движения", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f))
            Text(
                "⏱ Время в пути",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clickable { onOpenTravelTimes() }
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.size(12.dp))

        // Выбор станции.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(repo.stations) { st ->
                val idx = repo.stations.indexOf(st)
                val selected = idx == stationIndex
                Text(
                    text = st.name,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { stationIndex = idx }
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        Spacer(Modifier.size(10.dp))

        // Будни / выходные.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayChip("Будни", day == DayType.WEEKDAY) { day = DayType.WEEKDAY }
            DayChip("Выходные", day == DayType.WEEKEND) { day = DayType.WEEKEND }
        }
        Spacer(Modifier.size(12.dp))

        // Заголовок таблицы.
        Row(Modifier.fillMaxWidth()) {
            HeaderCell("Час", 42.dp)
            HeaderCell("→ Ботаническая", null, Modifier.weight(1f))
            HeaderCell("→ Пр. Космонавтов", null, Modifier.weight(1f))
        }
        Spacer(Modifier.size(4.dp))

        Column(Modifier.verticalScroll(rememberScrollState())) {
            hours.forEach { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        "%02d".format(h),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(42.dp),
                    )
                    Text(south[h] ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    Text(north[h] ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable { onClick() }
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp?, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = (if (width != null) modifier.width(width) else modifier),
    )
}
