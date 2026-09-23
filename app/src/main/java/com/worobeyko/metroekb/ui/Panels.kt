package com.worobeyko.metroekb.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worobeyko.metroekb.data.Directions
import com.worobeyko.metroekb.data.Geocoder
import com.worobeyko.metroekb.data.Place
import com.worobeyko.metroekb.data.Station
import com.worobeyko.metroekb.domain.ApproxEta
import com.worobeyko.metroekb.domain.StationArrivals
import kotlinx.coroutines.launch

/** Включён ли TalkBack (исследование касанием) - следим за изменением на лету. */
@Composable
fun rememberTalkBackEnabled(): Boolean {
    val context = LocalContext.current
    val am = remember { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember { mutableStateOf(am?.isTouchExplorationEnabled == true) }
    DisposableEffect(am) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        am?.addTouchExplorationStateChangeListener(listener)
        onDispose { am?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/** Общая «карточка-панель» в комикс-стиле с заголовком и ✕. */
@Composable
internal fun ComicPanel(
    kicker: String,
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .width(300.dp)
            .background(BubbleBg, RoundedCornerShape(20.dp))
            .border(BorderStroke(3.dp, BubbleInk), RoundedCornerShape(20.dp))
            .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text(kicker, fontSize = 10.sp, fontWeight = FontWeight.Black, color = BubbleInkSoft)
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = BubbleInk)
            }
            CloseButton(onClose)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(end = 8.dp)) {
            content()
        }
    }
}

/** Строка станции со временем по направлениям - для избранного и списка станций. */
@Composable
private fun StationRow(station: Station, arrivals: StationArrivals?, star: Boolean, onOpen: () -> Unit) {
    val dirs = arrivals?.directions.orEmpty()
    val spoken = buildString {
        append(station.name).append(". ")
        dirs.forEach { d ->
            append("На ${Directions.terminal(d.direction)}: ")
            append(d.next.firstOrNull()?.let { ApproxEta.of(it.secondsUntil).spoken } ?: "поездов больше нет")
            append(". ")
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = spoken; role = Role.Button }
            .clickable { onOpen() }
            .padding(vertical = 7.dp),
    ) {
        Text((if (star) "★ " else "") + station.name, fontSize = 15.sp, fontWeight = FontWeight.Black, color = BubbleInk)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            dirs.forEach { d ->
                val first = d.next.firstOrNull()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(accentOf(d.direction), CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${Directions.terminalShort(d.direction)} ${first?.let { ApproxEta.of(it.secondsUntil).text } ?: "-"}",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentOf(d.direction),
                    )
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(BubbleRule))
}

/** Избранные станции: время по ним сразу, тап - открыть станцию. */
@Composable
fun FavoritesPanel(
    favorites: List<Station>,
    arrivalsOf: (String) -> StationArrivals?,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ComicPanel("★ ИЗБРАННОЕ", "Мои станции", onClose, modifier) {
        if (favorites.isEmpty()) {
            Text("Пока пусто. Откройте станцию и нажмите ☆ рядом с названием.", fontSize = 13.sp, color = BubbleInkSoft)
        }
        favorites.forEach { s -> StationRow(s, arrivalsOf(s.id), star = true) { onOpen(s.id) } }
    }
}

/** Список всех станций текстом - удобен с TalkBack (карта сама по себе не озвучивается). */
@Composable
fun StationListPanel(
    stations: List<Station>,
    favorites: Set<String>,
    arrivalsOf: (String) -> StationArrivals?,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ComicPanel("ЛИНИЯ 1", "Все станции", onClose, modifier) {
        stations.forEach { s -> StationRow(s, arrivalsOf(s.id), star = s.id in favorites) { onOpen(s.id) } }
    }
}

/**
 * Поиск: по мере ввода - станции по названию (без сети), по кнопке «Найти» - адреса
 * через OpenStreetMap. Выбор станции открывает её, выбор адреса - показывает на карте.
 */
@Composable
fun SearchPanel(
    stations: List<Station>,
    onStation: (String) -> Unit,
    onPlace: (Place) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var places by remember { mutableStateOf<List<Place>?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun runSearch() {
        if (query.isBlank() || loading) return
        loading = true
        scope.launch {
            places = Geocoder.search(query.trim())
            loading = false
        }
    }

    ComicPanel("🔍 ПОИСК", "Станция или адрес", onClose, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = { query = it; places = null },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = BubbleInk),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .border(BorderStroke(1.5.dp, BubbleInk), RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .semantics { contentDescription = "Название станции или адрес" },
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("например: Малышева 5", fontSize = 15.sp, color = BubbleInkSoft)
                    inner()
                },
            )
            Spacer(Modifier.width(6.dp))
            PillButton(if (loading) "…" else "Найти", filled = true, onClick = { runSearch() })
        }
        Spacer(Modifier.height(8.dp))
        val q = query.trim().lowercase()
        val matches = if (q.isEmpty()) emptyList() else stations.filter { it.name.lowercase().contains(q) }
        matches.forEach { s ->
            Text(
                "🚇 ${s.name}",
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BubbleInk,
                modifier = Modifier.fillMaxWidth().clickable { onStation(s.id) }.padding(vertical = 8.dp),
            )
        }
        when {
            loading -> Text("ищу адрес…", fontSize = 13.sp, color = BubbleInkSoft)
            places?.isEmpty() == true -> Text("Адрес не найден в Екатеринбурге.", fontSize = 13.sp, color = BubbleInkSoft)
            else -> places?.forEach { p ->
                Column(Modifier.fillMaxWidth().clickable { onPlace(p) }.padding(vertical = 7.dp)) {
                    Text("📍 ${p.title}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BubbleInk)
                    if (p.subtitle.isNotEmpty()) Text(p.subtitle, fontSize = 11.sp, color = BubbleInkSoft)
                }
            }
        }
        if (matches.isEmpty() && places == null && q.isNotEmpty() && !loading) {
            Text("Нажмите «Найти», чтобы искать адрес.", fontSize = 12.sp, color = BubbleInkSoft)
        }
    }
}

/** Итог поиска адреса: ближайшая станция и сколько идти. */
@Composable
fun PlaceResultCard(
    place: Place,
    station: Station,
    meters: Double,
    walkSpeedKmh: Float,
    onOpenStation: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val minutes = (meters / (walkSpeedKmh * 1000.0 / 3600.0) / 60.0).toInt().coerceAtLeast(1)
    val dist = if (meters < 1000) "${(meters / 10).toInt() * 10} м" else "%.1f км".format(meters / 1000).replace('.', ',')
    ComicPanel("📍 АДРЕС", place.title, onClose, modifier) {
        Text("ближайшая станция:", fontSize = 12.sp, color = BubbleInkSoft)
        Text(station.name, fontSize = 17.sp, fontWeight = FontWeight.Black, color = BubbleInk)
        Text("$dist по прямой · ~$minutes мин пешком", fontSize = 13.sp, color = BubbleInk)
        Spacer(Modifier.height(10.dp))
        PillButton("Открыть станцию", filled = true, onClick = onOpenStation)
    }
}
