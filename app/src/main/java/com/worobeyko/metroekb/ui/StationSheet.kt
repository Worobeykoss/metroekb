package com.worobeyko.metroekb.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worobeyko.metroekb.data.BestCar
import com.worobeyko.metroekb.data.Directions
import com.worobeyko.metroekb.data.Station
import com.worobeyko.metroekb.data.StationNote
import com.worobeyko.metroekb.data.StationPhoto
import com.worobeyko.metroekb.data.Weather
import com.worobeyko.metroekb.data.rememberRemoteImage
import com.worobeyko.metroekb.domain.ApproxEta
import com.worobeyko.metroekb.domain.Crowd
import com.worobeyko.metroekb.domain.DayType
import com.worobeyko.metroekb.domain.DirectionArrivals
import com.worobeyko.metroekb.domain.SchemeExit
import com.worobeyko.metroekb.domain.StationArrivals
import com.worobeyko.metroekb.domain.StationScheme
import com.worobeyko.metroekb.map.TrainPalette
import kotlin.math.abs

internal val BubbleBg = Color(0xFFF7F3E7)
internal val BubbleInk = Color(0xFF16181F)
internal val BubbleInkSoft = Color(0xFF565A66)
internal val BubbleRule = Color(0x2616181F)
internal val CatchGreen = Color(0xFF0B8F45)
internal val WarnRed = Color(0xFFC62828)
internal val AccessibleBlue = Color(0xFF1565C0)
private val DefaultSouthAccent = Color(0xFFC77A00)
private val DefaultNorthAccent = Color(0xFF0277BD)

/**
 * Цвет направления для светлых панелей - тот же оттенок, что у поездов на карте, но темнее,
 * чтобы читался на кремовом фоне. Стандартные цвета - подобранные вручную.
 */
internal fun accentOf(direction: String): Color {
    val south = direction == Directions.SOUTH
    val raw = if (south) TrainPalette.south else TrainPalette.north
    if (raw == TrainPalette.DEFAULT_SOUTH && south) return DefaultSouthAccent
    if (raw == TrainPalette.DEFAULT_NORTH && !south) return DefaultNorthAccent
    val base = Color(raw)
    val k = if (base.luminance() > 0.5f) 0.55f else 0.85f
    return Color(base.red * k, base.green * k, base.blue * k)
}

/** Цвет направления как на карте (для тёмного фона). */
internal fun mapColorOf(direction: String): Color =
    Color(if (direction == Directions.SOUTH) TrainPalette.south else TrainPalette.north)

/** Следующая станция по ходу поезда (станции в списке идут с севера на юг). */
internal fun nextStationOf(stations: List<Station>, stationId: String, direction: String): Station? {
    val i = stations.indexOfFirst { it.id == stationId }
    if (i < 0) return null
    return stations.getOrNull(if (direction == Directions.SOUTH) i + 1 else i - 1)
}

/** Последний поезд дня по направлению: подпись «00:04» и сколько до него минут (<0 - ушёл). */
data class LastTrainInfo(val hhmm: String, val minutesLeft: Int)

/** «Последний поезд скоро» - предупреждаем за полчаса. */
private const val LAST_TRAIN_WARN_MIN = 30

private fun previousTrainText(sinceLastMin: Int?): String? = when {
    sinceLastMin == null -> null
    sinceLastMin <= 0 -> "предыдущий был только что"
    else -> "предыдущий был $sinceLastMin мин назад"
}

/** Крупное приблизительное время: «~2½ мин» или «прибывает». */
@Composable
internal fun EtaLabel(secondsUntil: Int, color: Color, size: TextUnit, modifier: Modifier = Modifier) {
    val eta = ApproxEta.of(secondsUntil)
    val a11y = modifier.semantics { contentDescription = eta.spoken }
    if (eta.arriving) {
        Text("прибывает", modifier = a11y, fontSize = size * 0.62f, fontWeight = FontWeight.Black, color = color)
    } else {
        Row(a11y, verticalAlignment = Alignment.Bottom) {
            Text("~${eta.value}", fontSize = size, fontWeight = FontWeight.Black, color = color, lineHeight = size)
            Spacer(Modifier.width(4.dp))
            Text(
                "мин",
                fontSize = size * 0.45f,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

/** Кнопка ✕ с нормальной зоной нажатия. */
@Composable
internal fun CloseButton(onClose: () -> Unit, color: Color = BubbleInk) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = "Закрыть"; role = Role.Button }
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", fontSize = 18.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
internal fun WeatherLine(weather: Weather) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(weather.icon, fontSize = 14.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            buildString {
                append("наверху ${weather.tempText}")
                if (weather.description.isNotEmpty()) append(", ${weather.description}")
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = BubbleInk,
        )
    }
    weather.hint?.let {
        Text(it, fontSize = 12.sp, fontWeight = FontWeight.Black, color = DefaultNorthAccent)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1) Маленькое GPS-окошко: ближайшая станция, время пешком и по каждому направлению -
//    ближайший поезд и на какой успеваешь дойти. Тап - компас ко входу.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GpsMiniPanel(
    station: Station,
    walkMinutes: Int?,
    walkSeconds: Int?,
    arrivals: StationArrivals?,
    lastTrains: Map<String, LastTrainInfo>,
    onOpenCompass: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dirs = arrivals?.directions ?: emptyList()
    val spoken = buildString {
        append("Ближайшая станция ${station.name}. ")
        if (walkMinutes != null) append("Пешком примерно $walkMinutes ${ApproxEta.minutesWord(walkMinutes)}. ")
        dirs.forEach { d ->
            val first = d.next.firstOrNull()
            append("На ${Directions.terminal(d.direction)}: ${first?.let { ApproxEta.of(it.secondsUntil).spoken } ?: "поездов больше нет"}. ")
        }
        append("Нажмите, чтобы открыть компас ко входу.")
    }
    Column(
        modifier = modifier
            .width(172.dp)
            .background(BubbleBg, RoundedCornerShape(14.dp))
            .border(BorderStroke(2.5.dp, BubbleInk), RoundedCornerShape(14.dp))
            .semantics(mergeDescendants = true) { contentDescription = spoken; role = Role.Button }
            .clickable { onOpenCompass() }
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Text("📍 БЛИЖАЙШАЯ", fontSize = 9.sp, fontWeight = FontWeight.Black, color = DefaultSouthAccent)
        Text(station.name, fontSize = 15.sp, fontWeight = FontWeight.Black, color = BubbleInk, lineHeight = 17.sp)
        if (walkMinutes != null) {
            Text("🚶 ~$walkMinutes мин пешком", fontSize = 11.sp, color = BubbleInkSoft)
        }

        if (dirs.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("поезда не ходят", fontSize = 12.sp, color = BubbleInkSoft)
        }
        dirs.forEach { dir ->
            val accent = accentOf(dir.direction)
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(accent, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    "→ ${Directions.terminalShort(dir.direction)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BubbleInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val first = dir.next.firstOrNull()
            // «Успею?»: первый поезд, до прихода которого хватает времени дойти.
            val catchable = walkSeconds?.let { w -> dir.next.firstOrNull { it.secondsUntil >= w } }
            Column(Modifier.padding(start = 14.dp)) {
                when {
                    first == null -> Text("больше нет", fontSize = 14.sp, fontWeight = FontWeight.Black, color = BubbleInkSoft)
                    walkSeconds == null || catchable == first -> Row(verticalAlignment = Alignment.Bottom) {
                        Text(ApproxEta.of(first.secondsUntil).text, fontSize = 16.sp, fontWeight = FontWeight.Black, color = accent)
                        if (walkSeconds != null) {
                            Spacer(Modifier.width(5.dp))
                            Text("успеете", fontSize = 10.sp, fontWeight = FontWeight.Black, color = CatchGreen, modifier = Modifier.padding(bottom = 2.dp))
                        }
                    }
                    else -> {
                        Text("${ApproxEta.of(first.secondsUntil).text} · не успеть", fontSize = 12.sp, color = BubbleInkSoft)
                        if (catchable != null) {
                            Text("успеете: ${ApproxEta.of(catchable.secondsUntil).text}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = CatchGreen)
                        }
                    }
                }
            }
        }

        // Последний поезд скоро - красная полоска.
        lastTrains.entries
            .filter { it.value.minutesLeft in 0..LAST_TRAIN_WARN_MIN }
            .forEach { (direction, info) ->
                Spacer(Modifier.height(7.dp))
                Text(
                    "⚠ последний → ${Directions.terminalShort(direction)} через ~${info.minutesLeft.coerceAtLeast(1)} мин",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    lineHeight = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WarnRed, RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                )
            }

        Spacer(Modifier.height(7.dp))
        Text("🧭 компас ко входу", fontSize = 10.sp, fontWeight = FontWeight.Black, color = DefaultNorthAccent)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2) Комикс-диалог станции (по тапу) с вкладками: «Поезда» (время, погода, загруженность,
//    последний поезд), «Схема» (где какие выходы, доступные входы), «Фото», «Заметка».
// ─────────────────────────────────────────────────────────────────────────────

/** Всё, что знаем о станции для диалога. */
class StationInfo(
    val station: Station,
    val stations: List<Station>,
    val dayType: DayType,
    val arrivals: StationArrivals?,
    val weather: Weather?,
    val crowd: Crowd?,
    val lastTrains: Map<String, LastTrainInfo>,
    val exits: List<SchemeExit>,
    val photos: List<StationPhoto>,
    val note: StationNote,
    val favorite: Boolean,
)

private enum class StationTab(val title: String) { TRAINS("Поезда"), SCHEME("Схема"), PHOTOS("Фото"), NOTE("Заметка") }

@Composable
fun StationComicDialog(
    info: StationInfo,
    onToggleFavorite: () -> Unit,
    onNoteChange: (StationNote) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val station = info.station
    val bubble = SpeechBubbleShape(corner = 20.dp, tailWidth = 26.dp, tailHeight = 14.dp)
    val isTerminal = info.stations.firstOrNull()?.id == station.id || info.stations.lastOrNull()?.id == station.id
    var tab by remember(station.id) { mutableStateOf(StationTab.TRAINS) }
    Column(
        modifier = modifier
            .width(304.dp)
            .background(BubbleBg, bubble)
            .border(BorderStroke(3.dp, BubbleInk), bubble)
            .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text(station.name, fontSize = 22.sp, fontWeight = FontWeight.Black, color = BubbleInk, lineHeight = 25.sp)
                Text(
                    text = buildString {
                        append(if (info.dayType == DayType.WEEKEND) "выходные" else "будни")
                        if (isTerminal) append(" · конечная")
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BubbleInkSoft,
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .semantics {
                        contentDescription = if (info.favorite) "Убрать из избранного" else "Добавить в избранное"
                        role = Role.Button
                    }
                    .clickable { onToggleFavorite() },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (info.favorite) "★" else "☆", fontSize = 24.sp, color = if (info.favorite) Color(0xFFE0A000) else BubbleInk)
            }
            CloseButton(onClose)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            StationTab.entries.forEach { t ->
                SmallTab(t.title, t == tab) { tab = t }
            }
        }
        Spacer(Modifier.height(10.dp))

        Column(
            Modifier
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState())
                .padding(end = 8.dp),
        ) {
            when (tab) {
                StationTab.TRAINS -> TrainsTab(info)
                StationTab.SCHEME -> SchemeTab(info)
                StationTab.PHOTOS -> PhotosTab(info.photos)
                StationTab.NOTE -> NoteTab(info, onNoteChange)
            }
        }
    }
}

@Composable
private fun SmallTab(title: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        color = if (selected) BubbleBg else BubbleInk,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) BubbleInk else Color.Transparent)
            .border(BorderStroke(1.5.dp, BubbleInk), RoundedCornerShape(50))
            .semantics { role = Role.Tab }
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun TrainsTab(info: StationInfo) {
    info.weather?.let {
        WeatherLine(it)
        Spacer(Modifier.height(2.dp))
    }
    info.crowd?.let {
        Text(
            "${it.emoji} в вагонах ${it.title}",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BubbleInk,
        )
        Text("оценка по частоте поездов", fontSize = 10.5.sp, color = BubbleInkSoft)
    }
    Spacer(Modifier.height(10.dp))
    val dirs = info.arrivals?.directions ?: emptyList()
    if (dirs.isEmpty()) {
        Text("Сейчас поезда не ходят.", fontSize = 14.sp, color = BubbleInkSoft)
        return
    }
    dirs.forEachIndexed { index, dir ->
        if (index > 0) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(BubbleRule))
            Spacer(Modifier.height(12.dp))
        }
        DirectionBlock(
            dir,
            nextStationOf(info.stations, info.station.id, dir.direction),
            info.lastTrains[dir.direction],
            info.note.bestCar[dir.direction],
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Время примерное: поезда приходят ±${ApproxEta.TOLERANCE_SEC} с от графика.",
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        color = BubbleInkSoft,
    )
}

@Composable
private fun DirectionBlock(dir: DirectionArrivals, nextStation: Station?, last: LastTrainInfo?, bestCar: BestCar?) {
    val accent = accentOf(dir.direction)
    val first = dir.next.getOrNull(0)
    val second = dir.next.getOrNull(1)

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Цветная полоса = цвет поездов этого направления на карте.
        Box(Modifier.width(5.dp).fillMaxHeight().background(accent, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                "→ ${Directions.terminal(dir.direction)}",
                fontSize = 16.sp, fontWeight = FontWeight.Black, color = BubbleInk,
            )
            if (nextStation != null) {
                Text("следующая - ${nextStation.name}", fontSize = 12.sp, color = BubbleInkSoft)
            }
            Spacer(Modifier.height(4.dp))

            if (first == null) {
                Text("Поездов сегодня больше нет", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BubbleInkSoft)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    EtaLabel(first.secondsUntil, accent, 32.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "по графику ${first.hhmm}",
                        fontSize = 12.sp,
                        color = BubbleInkSoft,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (second != null) {
                    Text(
                        text = "затем ${ApproxEta.of(second.secondsUntil).text} · ${second.hhmm}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BubbleInk,
                    )
                }
            }
            previousTrainText(dir.sinceLastMin)?.let {
                Text(it, fontSize = 12.sp, color = BubbleInkSoft)
            }
            if (bestCar != null) {
                Text("🚃 садитесь: ${bestCar.title} вагон", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CatchGreen)
            }
            if (last != null && last.minutesLeft >= 0) {
                if (last.minutesLeft <= LAST_TRAIN_WARN_MIN) {
                    Text(
                        "⚠ последний поезд через ~${last.minutesLeft.coerceAtLeast(1)} мин (${last.hhmm})",
                        fontSize = 12.sp, fontWeight = FontWeight.Black, color = WarnRed,
                    )
                } else {
                    Text("последний поезд - ${last.hhmm}", fontSize = 12.sp, color = BubbleInkSoft)
                }
            }
        }
    }
}

private fun refsText(exits: List<SchemeExit>): String =
    exits.map { it.ref.ifEmpty { "без номера" } }.distinct().joinToString(", ")

@Composable
private fun SchemeTab(info: StationInfo) {
    val exits = info.exits
    if (exits.isEmpty()) {
        Text("Про входы этой станции данных нет.", fontSize = 13.sp, color = BubbleInkSoft)
        return
    }
    StationSchemeView(exits)
    Spacer(Modifier.height(10.dp))
    val dirs = info.arrivals?.directions?.map { it.direction } ?: Directions.ALL
    dirs.forEach { dir ->
        val (head, tail) = StationScheme.headAndTail(exits, dir == Directions.SOUTH)
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 4.dp).size(8.dp).background(accentOf(dir), CircleShape))
            Spacer(Modifier.width(6.dp))
            Column {
                Text("→ ${Directions.terminal(dir)}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = BubbleInk)
                Text(
                    buildString {
                        append("первый вагон - ")
                        append(if (head.isEmpty()) "у этого конца выходов нет" else "к выходам ${refsText(head)}")
                    },
                    fontSize = 12.sp, color = BubbleInk,
                )
                Text(
                    buildString {
                        append("последний - ")
                        append(if (tail.isEmpty()) "у этого конца выходов нет" else "к выходам ${refsText(tail)}")
                    },
                    fontSize = 12.sp, color = BubbleInk,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    val accessible = exits.filter { it.accessible }
    Text(
        if (accessible.isEmpty()) "♿ входы с пандусом или лифтом в OpenStreetMap не отмечены"
        else "♿ с пандусом или лифтом: ${refsText(accessible)}",
        fontSize = 12.sp, fontWeight = FontWeight.Bold,
        color = if (accessible.isEmpty()) BubbleInkSoft else AccessibleBlue,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "Схема примерная: по координатам входов из OpenStreetMap.",
        fontSize = 10.5.sp, lineHeight = 13.sp, color = BubbleInkSoft,
    )
}

/** Платформа сбоку: слева - сторона Проспекта Космонавтов, справа - Ботанической. */
@Composable
private fun StationSchemeView(exits: List<SchemeExit>) {
    val measurer = rememberTextMeasurer()
    val span = maxOf(90.0, (exits.maxOfOrNull { abs(it.alongM) } ?: 0.0) + 15.0)
    val south = accentOf(Directions.SOUTH)
    val north = accentOf(Directions.NORTH)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = "Схема станции: выходы ${refsText(exits)} вдоль платформы" },
    ) {
        val cy = size.height / 2f
        val left = 16.dp.toPx()
        val right = size.width - 16.dp.toPx()
        val cx = (left + right) / 2f
        fun x(along: Double) = (cx + along / span * (right - left) / 2f).toFloat()

        // Пути и платформа.
        drawLine(north.copy(alpha = 0.6f), Offset(left - 8f, cy - 13.dp.toPx()), Offset(right + 8f, cy - 13.dp.toPx()), strokeWidth = 3f)
        drawLine(south.copy(alpha = 0.6f), Offset(left - 8f, cy + 13.dp.toPx()), Offset(right + 8f, cy + 13.dp.toPx()), strokeWidth = 3f)
        drawRoundRect(
            color = Color(0xFFD9D2B8),
            topLeft = Offset(x(-60.0), cy - 8.dp.toPx()),
            size = Size(x(60.0) - x(-60.0), 16.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
        val ends = TextStyle(fontSize = 9.5.sp, color = BubbleInkSoft, fontWeight = FontWeight.Bold)
        drawText(measurer, "← Космонавтов", Offset(left - 8f, 0f), ends)
        val r = measurer.measure("Ботаническая →", ends)
        drawText(r, topLeft = Offset(right + 8f - r.size.width, 0f))

        exits.forEach { e ->
            val ex = x(e.alongM)
            val ey = cy + e.side * 36.dp.toPx()
            drawLine(BubbleInkSoft, Offset(ex, cy + e.side * 8.dp.toPx()), Offset(ex, ey - e.side * 9.dp.toPx()), strokeWidth = 2f)
            drawCircle(if (e.accessible) AccessibleBlue else BubbleInk, radius = 10.dp.toPx(), center = Offset(ex, ey))
            val label = measurer.measure(e.ref.ifEmpty { "•" }, TextStyle(fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Black))
            drawText(label, topLeft = Offset(ex - label.size.width / 2f, ey - label.size.height / 2f))
        }
    }
}

@Composable
private fun PhotosTab(photos: List<StationPhoto>) {
    if (photos.isEmpty()) {
        Text("Фото этой станции пока нет.", fontSize = 13.sp, color = BubbleInkSoft)
        return
    }
    val pager = rememberPagerState { photos.size }
    HorizontalPager(pager, Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(10.dp))) { page ->
        val photo = photos[page]
        val img by rememberRemoteImage(photo.thumb)
        Box(Modifier.fillMaxSize().background(Color(0xFF2C3140)), contentAlignment = Alignment.Center) {
            val bmp = img
            if (bmp != null) {
                Image(bmp, contentDescription = "Фото станции", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("загружаю…", fontSize = 12.sp, color = Color(0xFFC9CCD4))
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(photos.size) { i ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (i == pager.currentPage) 8.dp else 6.dp)
                    .background(if (i == pager.currentPage) BubbleInk else BubbleRule, CircleShape),
            )
        }
    }
    val p = photos[pager.currentPage]
    Spacer(Modifier.height(4.dp))
    Text(
        "фото: ${p.author.ifEmpty { "автор не указан" }} · ${p.license} · Wikimedia Commons",
        fontSize = 10.5.sp, lineHeight = 13.sp, color = BubbleInkSoft,
    )
}

@Composable
private fun NoteTab(info: StationInfo, onNoteChange: (StationNote) -> Unit) {
    val note = info.note
    Text("Заметка", fontSize = 13.sp, fontWeight = FontWeight.Black, color = BubbleInk)
    Spacer(Modifier.height(4.dp))
    BasicTextField(
        value = note.text,
        onValueChange = { onNoteChange(note.copy(text = it.take(300))) },
        textStyle = TextStyle(fontSize = 14.sp, color = BubbleInk),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .border(BorderStroke(1.5.dp, BubbleInk), RoundedCornerShape(10.dp))
            .padding(10.dp),
        decorationBox = { inner ->
            if (note.text.isEmpty()) Text("например: выход 3 - к офису", fontSize = 14.sp, color = BubbleInkSoft)
            inner()
        },
    )
    Spacer(Modifier.height(12.dp))
    Text("Какой вагон удобнее к вашему выходу", fontSize = 13.sp, fontWeight = FontWeight.Black, color = BubbleInk)
    Text("Подсказка во вкладке «Схема».", fontSize = 11.sp, color = BubbleInkSoft)
    val dirs = info.arrivals?.directions?.map { it.direction } ?: Directions.ALL
    dirs.forEach { dir ->
        Spacer(Modifier.height(8.dp))
        Text("→ ${Directions.terminal(dir)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentOf(dir))
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            BestCar.entries.forEach { car ->
                val selected = note.bestCar[dir] == car
                SmallTab(car.title, selected) {
                    val map = if (selected) note.bestCar - dir else note.bestCar + (dir to car)
                    onNoteChange(note.copy(bestCar = map))
                }
            }
        }
    }
}

/** Речевой «пузырь»: скруглённый прямоугольник + треугольный хвостик снизу. */
internal data class SpeechBubbleShape(
    val corner: Dp,
    val tailWidth: Dp,
    val tailHeight: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { corner.toPx() }
        val tw = with(density) { tailWidth.toPx() }
        val th = with(density) { tailHeight.toPx() }
        val bodyBottom = size.height - th
        val path = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, bodyBottom, c, c))
            val cx = size.width * 0.30f
            moveTo(cx - tw / 2f, bodyBottom)
            lineTo(cx, size.height)
            lineTo(cx + tw / 2f, bodyBottom)
            close()
        }
        return Outline.Generic(path)
    }
}
