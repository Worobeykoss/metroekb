package com.worobeyko.metroekb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worobeyko.metroekb.data.ScheduleUpdater
import com.worobeyko.metroekb.map.MapTheme
import com.worobeyko.metroekb.map.OfflineMaps
import com.worobeyko.metroekb.map.TrainPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: Settings, onBack: () -> Unit, onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offline by OfflineMaps.status
    val updateStatus by ScheduleUpdater.status
    val updating by ScheduleUpdater.running
    LaunchedEffect(settings.theme) { OfflineMaps.refresh(context, settings.theme) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Назад",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(26.dp).clickable { onBack() },
            )
            Spacer(Modifier.size(10.dp))
            Text("Настройки", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.size(18.dp))

        SectionTitle("График")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("Авто", settings.dayMode == DayMode.AUTO) { settings.dayMode = DayMode.AUTO }
            ModeChip("Будни", settings.dayMode == DayMode.WEEKDAY) { settings.dayMode = DayMode.WEEKDAY }
            ModeChip("Выходные", settings.dayMode == DayMode.WEEKEND) { settings.dayMode = DayMode.WEEKEND }
        }
        Spacer(Modifier.size(8.dp))
        Hint(updateStatus ?: "Раз в сутки приложение сверяет график с metro-ektb.ru.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(if (updating) "Проверяю…" else "Проверить сейчас", false) {
                scope.launch { ScheduleUpdater.checkIfDue(context, force = true) }
            }
            ModeChip("История изменений", false) { onOpenHistory() }
        }
        Spacer(Modifier.size(18.dp))

        SectionTitle("Карта")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MapTheme.entries.forEach { t -> ModeChip(t.title, settings.theme == t) { settings.theme = t } }
        }
        Spacer(Modifier.size(6.dp))
        ToggleRow("Показывать поезда", settings.showTrains) { settings.showTrains = it }
        ToggleRow("Таймер над поездами", settings.showTrainTimers) { settings.showTrainTimers = it }
        ToggleRow("Тепловой след за поездами", settings.showTrails) { settings.showTrails = it }
        ToggleRow("Вспышка станции при прибытии", settings.showPulses) { settings.showPulses = it }
        ToggleRow("«Успею?» — подсветка поездов", settings.showCatch) { settings.showCatch = it }
        ToggleRow("Подписи станций", settings.showLabels) { settings.showLabels = it }
        ToggleRow("Пеший маршрут и «вы здесь»", settings.showWalkRoute) { settings.showWalkRoute = it }
        Spacer(Modifier.size(18.dp))

        SectionTitle("Цвета поездов")
        ColorRow("→ Ботаническая", settings.southColor) { settings.southColor = it }
        Spacer(Modifier.size(8.dp))
        ColorRow("→ Проспект Космонавтов", settings.northColor) { settings.northColor = it }
        Spacer(Modifier.size(6.dp))
        ModeChip("Сбросить цвета", false) {
            settings.southColor = TrainPalette.DEFAULT_SOUTH
            settings.northColor = TrainPalette.DEFAULT_NORTH
        }
        Spacer(Modifier.size(18.dp))

        SectionTitle("Пешком")
        Text(
            "Скорость: ${"%.1f".format(settings.walkSpeedKmh)} км/ч",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Slider(
            value = settings.walkSpeedKmh,
            onValueChange = { settings.walkSpeedKmh = it },
            valueRange = 3f..7f,
            steps = 7,
        )
        ToggleRow("Учить мой темп по GPS", settings.learnWalkSpeed) { settings.learnWalkSpeed = it }
        Hint(
            if (settings.walkSamples > 0) "Учтено замеров: ${settings.walkSamples}. Бег и транспорт не считаются."
            else "Пока не было замеров: пройдитесь с включённым GPS."
        )
        Spacer(Modifier.size(18.dp))

        SectionTitle("Доступность")
        ToggleRow("Компас — только входы с пандусом или лифтом", settings.accessibleOnly) { settings.accessibleOnly = it }
        Hint("С TalkBack на карте появится кнопка «Все станции» — список со временем, который озвучивается.")
        Spacer(Modifier.size(18.dp))

        SectionTitle("Офлайн-карта")
        val o = offline
        Hint(
            when {
                o == null -> "Карта города для темы «${settings.theme.title}» не скачана. Около 20–40 МБ."
                o.error != null -> "Ошибка: ${o.error}"
                o.done -> "Скачана: ${o.bytes / 1_000_000} МБ. Без сети карта останется на месте."
                else -> "Скачиваю: ${o.percent}% (${o.bytes / 1_000_000} МБ)…"
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (o == null || o.error != null) {
                ModeChip("Скачать", false) { OfflineMaps.download(context, settings.theme) }
            } else {
                ModeChip("Удалить", false) { OfflineMaps.delete(context, settings.theme) }
            }
        }
        Spacer(Modifier.size(18.dp))

        SectionTitle("Иконка приложения")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon.entries.forEach { icon ->
                ModeChip(icon.title, settings.appIcon == icon) {
                    if (settings.appIcon != icon) {
                        settings.appIcon = icon
                        AppIconSwitcher.apply(context, icon)
                    }
                }
            }
        }
        Hint("Лаунчер обновит иконку через пару секунд; ярлык на рабочем столе, возможно, придётся добавить заново.")

        Spacer(Modifier.size(24.dp))
        Text(
            "Метро Екатеринбург · расписание metro-ektb.ru\n" +
                "Карта © OpenStreetMap, CARTO · маршрут © OSRM · поиск © Nominatim\n" +
                "Входы и форма линии © OpenStreetMap · погода © Open-Meteo\n" +
                "Фото станций — Wikimedia Commons, авторы и лицензии указаны у каждого фото",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ColorRow(label: String, selected: Int, onPick: (Int) -> Unit) {
    Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.size(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TrainPalette.CHOICES.forEach { c ->
            val on = c == selected
            Box(
                Modifier
                    .size(30.dp)
                    .border(if (on) 3.dp else 0.dp, if (on) Color.White else Color.Transparent, CircleShape)
                    .padding(if (on) 4.dp else 0.dp)
                    .background(Color(c), CircleShape)
                    .semantics { role = Role.RadioButton; this.selected = on; contentDescription = "цвет" }
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
