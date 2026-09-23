package com.worobeyko.metroekb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worobeyko.metroekb.data.Directions
import com.worobeyko.metroekb.data.EntranceRepository
import com.worobeyko.metroekb.data.PhotoRepository
import com.worobeyko.metroekb.data.Place
import com.worobeyko.metroekb.data.ScheduleRepository
import com.worobeyko.metroekb.data.UserStore
import com.worobeyko.metroekb.data.rememberWeather
import com.worobeyko.metroekb.domain.Crowd
import com.worobeyko.metroekb.domain.DayType
import com.worobeyko.metroekb.domain.GhostTrain
import com.worobeyko.metroekb.domain.MetroClock
import com.worobeyko.metroekb.domain.StationScheme
import com.worobeyko.metroekb.domain.TrainRef
import com.worobeyko.metroekb.domain.TrainSimulator
import com.worobeyko.metroekb.location.NearestStation
import com.worobeyko.metroekb.location.UserLocation
import com.worobeyko.metroekb.location.WalkSpeedLearner
import com.worobeyko.metroekb.location.nearestStation
import com.worobeyko.metroekb.location.rememberUserLocation
import com.worobeyko.metroekb.location.rememberWalkRoute
import com.worobeyko.metroekb.map.CatchTarget
import com.worobeyko.metroekb.map.EffectCircle
import com.worobeyko.metroekb.map.FrameOptions
import com.worobeyko.metroekb.map.MapTap
import com.worobeyko.metroekb.map.MetroMap
import com.worobeyko.metroekb.map.MetroMapController
import com.worobeyko.metroekb.map.buildMapFrame
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

/** Сколько тапов по плашке времени вызывают призрака и за какое время. */
private const val GHOST_TAPS = 5
private const val GHOST_TAPS_WINDOW_MS = 3000L
private const val GHOST_SUMMON_MS = 90_000L

/** С какого момента показывать «до закрытия», минут. */
private const val CLOSING_WARN_MIN = 180

private fun dayTypeFor(mode: DayMode, auto: DayType) = when (mode) {
    DayMode.AUTO -> auto
    DayMode.WEEKDAY -> DayType.WEEKDAY
    DayMode.WEEKEND -> DayType.WEEKEND
}

private fun durationText(min: Int): String = if (min >= 60) "${min / 60} ч ${min % 60} мин" else "$min мин"

@Composable
fun MetroScreen(
    settings: Settings,
    onOpenSchedule: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    // График может обновиться с сайта на лету — тогда пересоздаём репозиторий и симулятор.
    val repoVersion by ScheduleRepository.version
    val repo = remember(repoVersion) { ScheduleRepository.get(context) }
    val simulator = remember(repo) { TrainSimulator(repo) }
    val entrances = remember { EntranceRepository.get(context) }
    val photos = remember { PhotoRepository.get(context) }
    val store = remember { UserStore.get(context) }
    val userLoc by rememberUserLocation()
    val talkBack = rememberTalkBackEnabled()

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(500)
        }
    }
    val clock = MetroClock.resolve(now)
    val dayType = dayTypeFor(settings.dayMode, clock.dayType)
    val hasData = repo.hasData(dayType)

    var controller by remember { mutableStateOf<MetroMapController?>(null) }
    var selectedStationId by remember { mutableStateOf<String?>(null) }
    var selectedTrain by remember { mutableStateOf<TrainRef?>(null) }
    var following by remember { mutableStateOf(false) }
    var cabinRef by remember { mutableStateOf<TrainRef?>(null) }
    var compassOpen by remember { mutableStateOf(false) }
    var ghostOpen by remember { mutableStateOf(false) }
    var favoritesOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var stationListOpen by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf<Place?>(null) }
    var ghostUntil by remember { mutableLongStateOf(0L) }
    val plaqueTaps = remember { ArrayDeque<Long>() }

    fun closeAll() {
        selectedStationId = null
        selectedTrain = null
        following = false
        compassOpen = false
        ghostOpen = false
        favoritesOpen = false
        searchOpen = false
        stationListOpen = false
        place = null
    }

    fun openStation(id: String) {
        closeAll()
        selectedStationId = id
        repo.station(id)?.let { controller?.flyTo(it.lat, it.lon, 13.5) }
    }

    val nearest: NearestStation? = remember(userLoc, repo) { userLoc?.let { nearestStation(it, repo.stations) } }
    val selectedStation = selectedStationId?.let { repo.station(it) }
    val walkRoute by rememberWalkRoute(userLoc, nearest?.station)

    // Умный темп: по GPS учимся, как быстро пользователь ходит.
    val learner = remember { WalkSpeedLearner() }
    LaunchedEffect(userLoc) {
        val loc = userLoc ?: return@LaunchedEffect
        if (!settings.learnWalkSpeed) return@LaunchedEffect
        learner.onFix(loc)?.let { mps ->
            settings.walkSpeedKmh = WalkSpeedLearner.blend(settings.walkSpeedKmh, mps)
            settings.walkSamples++
        }
    }

    // Время пешком: по маршруту (если есть), иначе по прямой; со скоростью из настроек.
    val walkMeters: Double? = walkRoute?.distanceMeters ?: nearest?.meters
    val walkSecondsExact: Double? = walkMeters?.let { it / (settings.walkSpeedKmh * 1000.0 / 3600.0) }
    val walkMinutes: Int? = walkSecondsExact?.let { (it / 60.0).roundToInt().coerceAtLeast(1) }
    val walkSeconds: Int? = walkSecondsExact?.roundToInt()

    val catchTarget = if (settings.showCatch && nearest != null && walkSeconds != null) {
        CatchTarget(repo.idToIndex[nearest.station.id] ?: -1, walkSeconds).takeIf { it.stationIndex >= 0 }
    } else null
    val compassTarget = if (compassOpen) userLoc?.let { nearestEntrance(it, entrances, settings.accessibleOnly) } else null
    val ghostActive = GhostTrain.isGhostHour(now.toLocalTime()) || System.currentTimeMillis() < ghostUntil

    // Кадр карты: свежее время каждый кадр (наносекунды) + учёт настроек и выбранного поезда.
    val frameProvider = {
        val resolved = MetroClock.resolve(LocalDateTime.now())
        val dt = dayTypeFor(settings.dayMode, resolved.dayType)
        val epoch = System.currentTimeMillis()
        val frame = buildMapFrame(
            sim = simulator,
            repo = repo,
            dayType = dt,
            nowMin = resolved.serviceMinute,
            epochMs = epoch,
            opts = FrameOptions(
                showTrains = settings.showTrains && repo.hasData(dt),
                pulses = settings.showPulses,
                catchTarget = catchTarget,
                selected = selectedTrain,
                following = following,
                ghost = ghostActive,
                trails = settings.showTrails,
            ),
        )
        // Подсветки поверх: вход, к которому ведёт компас, и найденный адрес.
        val pulse = (0.5 + 0.5 * sin(epoch / 300.0)).toFloat()
        val extra = listOfNotNull(
            compassTarget?.let {
                EffectCircle(it.entrance.lat, it.entrance.lon, 11f + pulse * 5f, 2.5f, 0xFF00E676.toInt(), 0.95f, 0xFF00E676.toInt(), 0.2f)
            },
            place?.let { EffectCircle(it.lat, it.lon, 9f + pulse * 4f, 3f, 0xFFFFFFFF.toInt(), 1f, 0xFFFF5A6E.toInt(), 0.9f) },
        )
        if (extra.isEmpty()) frame else frame.copy(effects = frame.effects + extra)
    }

    fun lastTrainsFor(stationId: String): Map<String, LastTrainInfo> =
        Directions.ALL.mapNotNull { dir ->
            simulator.lastTrain(stationId, dayType, dir)?.let { (minute, label) ->
                dir to LastTrainInfo(label, floor(minute - clock.serviceMinute).toInt())
            }
        }.toMap()

    fun arrivalsOf(id: String) = if (hasData) simulator.stationArrivals(id, dayType, clock.serviceMinute) else null

    // До закрытия / когда откроется.
    val span = remember(simulator, dayType) { simulator.serviceSpan(dayType) }
    val closingText: String? = span?.let { (first, last) ->
        val m = clock.serviceMinute
        when {
            m > last -> {
                val serviceDate = if (now.hour <= 2) now.toLocalDate().minusDays(1) else now.toLocalDate()
                val nextType = dayTypeFor(settings.dayMode, MetroClock.dayTypeFor(serviceDate.plusDays(1)))
                simulator.serviceSpan(nextType)?.let { "метро закрыто · откроется в ${serviceHhmm(it.first)}" }
            }
            m < first -> "метро откроется в ${serviceHhmm(first)}"
            last - m <= CLOSING_WARN_MIN -> "до закрытия ~${durationText((last - m).toInt().coerceAtLeast(1))}"
            else -> null
        }
    }

    val selectedTrainPos = selectedTrain?.let { simulator.locate(it, dayType, clock.serviceMinute) }
    // Поезд доехал до конечной — слежение заканчивается само.
    LaunchedEffect(selectedTrainPos == null, following) {
        if (following && selectedTrain != null && selectedTrainPos == null) {
            delay(2500)
            following = false
        }
    }

    val anyOpen = selectedStationId != null || selectedTrain != null || compassOpen || ghostOpen ||
        favoritesOpen || searchOpen || stationListOpen || place != null
    BackHandler(enabled = anyOpen) { closeAll() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MetroMap(
            repo = repo,
            entrances = entrances,
            frameProvider = frameProvider,
            userLat = userLoc?.lat,
            userLon = userLoc?.lon,
            walkPoints = if (settings.showWalkRoute) walkRoute?.points else null,
            showLabels = settings.showLabels,
            showTrainTimers = settings.showTrainTimers,
            showWalk = settings.showWalkRoute,
            theme = settings.theme,
            southColor = settings.southColor,
            northColor = settings.northColor,
            favorites = store.favorites,
            onTap = { tap ->
                closeAll()
                when (tap) {
                    is MapTap.StationTap -> selectedStationId = tap.stationId
                    is MapTap.TrainTap -> selectedTrain = tap.ref
                    MapTap.GhostTap -> ghostOpen = true
                }
            },
            onUserGesture = { following = false },
            onControllerReady = { controller = it },
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Карта метро с поездами. Список станций — кнопка «Все станции»." },
        )

        // Верхняя плашка: день, время и «до закрытия». 5 быстрых тапов — поезд-призрак.
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                    val t = System.currentTimeMillis()
                    plaqueTaps.addLast(t)
                    while (plaqueTaps.isNotEmpty() && t - plaqueTaps.first() > GHOST_TAPS_WINDOW_MS) plaqueTaps.removeFirst()
                    if (plaqueTaps.size >= GHOST_TAPS) {
                        plaqueTaps.clear()
                        ghostUntil = t + GHOST_SUMMON_MS
                    }
                },
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(10.dp).clip(CircleShape).background(if (ghostActive) Color(0xFFB9F6CA) else Color(0xFFE8452A)))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "${if (dayType == DayType.WEEKEND) "Выходные" else "Будни"}  ·  ${now.format(TIME_FMT)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                closingText?.let {
                    Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB388FF))
                }
            }
        }

        // Маленькое GPS-окошко (справа сверху); пока открыт диалог — прячем, чтобы не наезжали.
        if (nearest != null && !anyOpen) {
            val arrivals = simulator.stationArrivals(nearest.station.id, dayType, clock.serviceMinute, count = 4)
            GpsMiniPanel(
                station = nearest.station,
                walkMinutes = walkMinutes,
                walkSeconds = if (settings.showCatch) walkSeconds else null,
                arrivals = if (hasData) arrivals else null,
                lastTrains = if (hasData) lastTrainsFor(nearest.station.id) else emptyMap(),
                onOpenCompass = { closeAll(); compassOpen = true },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            )
        }

        // Комикс-диалог станции (слева) по тапу.
        if (selectedStation != null) {
            val weather by rememberWeather(selectedStation)
            val index = repo.idToIndex[selectedStation.id] ?: 0
            val (trainsNow, trainsMax) = simulator.trainsPerHour(selectedStation.id, dayType, clock.serviceMinute)
            val exits = remember(selectedStation.id, repo) {
                StationScheme.exits(
                    selectedStation.lat, selectedStation.lon, repo.geometry.bearingAtStation(index),
                    entrances.filter { it.stationId == selectedStation.id }.map { Triple(it.ref, it.lat to it.lon, it.accessible) },
                )
            }
            StationComicDialog(
                info = StationInfo(
                    station = selectedStation,
                    stations = repo.stations,
                    dayType = dayType,
                    arrivals = arrivalsOf(selectedStation.id),
                    weather = weather,
                    crowd = Crowd.of(trainsNow, trainsMax),
                    lastTrains = if (hasData) lastTrainsFor(selectedStation.id) else emptyMap(),
                    exits = exits,
                    photos = photos[selectedStation.id].orEmpty(),
                    note = store.note(selectedStation.id),
                    favorite = store.isFavorite(selectedStation.id),
                ),
                onToggleFavorite = { store.toggleFavorite(selectedStation.id) },
                onNoteChange = { store.setNote(selectedStation.id, it) },
                onClose = { selectedStationId = null },
                modifier = Modifier.align(Alignment.CenterStart).statusBarsPadding().padding(start = 10.dp),
            )
        }

        // Поезд: большое окно, а во время слежения — компактная плашка сверху.
        selectedTrain?.let { ref ->
            if (following) {
                FollowBar(
                    train = selectedTrainPos,
                    direction = ref.direction,
                    stations = repo.stations,
                    onStop = { following = false; selectedTrain = null },
                    onCabin = { cabinRef = ref },
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp),
                )
            } else {
                TrainDialog(
                    train = selectedTrainPos,
                    direction = ref.direction,
                    stations = repo.stations,
                    following = false,
                    onToggleFollow = { following = true },
                    onCabin = { cabinRef = ref },
                    onClose = { selectedTrain = null },
                    modifier = Modifier.align(Alignment.CenterStart).statusBarsPadding().padding(start = 12.dp),
                )
            }
        }

        if (ghostOpen) {
            GhostDialog(
                onClose = { ghostOpen = false },
                modifier = Modifier.align(Alignment.CenterStart).statusBarsPadding().padding(start = 12.dp),
            )
        }

        if (compassOpen) {
            CompassPanel(
                user = userLoc,
                entrances = entrances,
                stations = repo.stations,
                walkSpeedKmh = settings.walkSpeedKmh,
                accessibleOnly = settings.accessibleOnly,
                onClose = { compassOpen = false },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (favoritesOpen) {
            FavoritesPanel(
                favorites = repo.stations.filter { store.isFavorite(it.id) },
                arrivalsOf = ::arrivalsOf,
                onOpen = ::openStation,
                onClose = { favoritesOpen = false },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (stationListOpen) {
            StationListPanel(
                stations = repo.stations,
                favorites = store.favorites,
                arrivalsOf = ::arrivalsOf,
                onOpen = ::openStation,
                onClose = { stationListOpen = false },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (searchOpen) {
            SearchPanel(
                stations = repo.stations,
                onStation = ::openStation,
                onPlace = { p ->
                    closeAll()
                    place = p
                    controller?.flyTo(p.lat, p.lon, 14.5)
                },
                onClose = { searchOpen = false },
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
            )
        }

        place?.let { p ->
            val near = nearestStation(UserLocation(p.lat, p.lon), repo.stations)
            if (near != null) {
                PlaceResultCard(
                    place = p,
                    station = near.station,
                    meters = near.meters,
                    walkSpeedKmh = settings.walkSpeedKmh,
                    onOpenStation = { openStation(near.station.id) },
                    onClose = { place = null },
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
                )
            }
        }

        // Кнопки действий (справа снизу).
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (talkBack) {
                RoundButton(Icons.Filled.Accessibility, "Все станции списком") { closeAll(); stationListOpen = true }
            }
            RoundButton(Icons.Filled.Search, "Поиск станции или адреса") { closeAll(); searchOpen = true }
            RoundButton(Icons.Filled.Star, "Избранные станции") { closeAll(); favoritesOpen = true }
            RoundButton(Icons.Filled.MyLocation, "Моё местоположение", enabled = userLoc != null) {
                following = false
                userLoc?.let { controller?.recenter(it.lat, it.lon) }
            }
            RoundButton(Icons.Filled.Explore, "На север") { controller?.resetNorth() }
            RoundButton(Icons.AutoMirrored.Filled.List, "График") { onOpenSchedule() }
            RoundButton(Icons.Filled.Settings, "Настройки") { onOpenSettings() }
        }

        // Легенда.
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(12.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                LegendRow(mapColorOf(Directions.SOUTH), "→ Ботаническая")
                Spacer(Modifier.size(6.dp))
                LegendRow(mapColorOf(Directions.NORTH), "→ Проспект Космонавтов")
            }
        }

        // Вид из кабины — поверх всего.
        cabinRef?.let { ref ->
            CabinView(
                ref = ref,
                stations = repo.stations,
                geometry = repo.geometry,
                locate = {
                    val resolved = MetroClock.resolve(LocalDateTime.now())
                    simulator.locate(ref, dayTypeFor(settings.dayMode, resolved.dayType), resolved.serviceMinute)
                },
                onClose = { cabinRef = null },
            )
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.9f else 0.5f),
        shape = CircleShape,
        modifier = Modifier.size(46.dp),
    ) {
        Box(
            Modifier.fillMaxSize().clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    // Кружок цвета поезда, а не стрелка: карта поворачивается, «вверх/вниз» ничего не значат.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        Spacer(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
