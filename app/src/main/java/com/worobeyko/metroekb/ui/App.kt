package com.worobeyko.metroekb.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.worobeyko.metroekb.data.ScheduleUpdater
import com.worobeyko.metroekb.map.TrainPalette

private enum class Screen { MAP, SCHEDULE, SETTINGS, TRAVEL, HISTORY }

@Composable
fun App() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("metro_settings", Context.MODE_PRIVATE) }
    val settings = remember { Settings.load(prefs) }
    var screen by remember { mutableStateOf(Screen.MAP) }

    // Любое изменение настроек — сразу в SharedPreferences.
    LaunchedEffect(Unit) {
        snapshotFlow { settings.snapshot() }.collect { settings.save(prefs) }
    }
    // Цвета поездов — общие для карты и панелей.
    SideEffect {
        TrainPalette.south = settings.southColor
        TrainPalette.north = settings.northColor
    }
    // Раз в сутки сверяем график с сайтом (в фоне, без блокировки интерфейса).
    LaunchedEffect(Unit) { ScheduleUpdater.checkIfDue(context) }

    BackHandler(enabled = screen != Screen.MAP) {
        screen = when (screen) {
            Screen.TRAVEL -> Screen.SCHEDULE
            Screen.HISTORY -> Screen.SETTINGS
            else -> Screen.MAP
        }
    }

    when (screen) {
        Screen.MAP -> MetroScreen(
            settings = settings,
            onOpenSchedule = { screen = Screen.SCHEDULE },
            onOpenSettings = { screen = Screen.SETTINGS },
        )
        Screen.SCHEDULE -> ScheduleScreen(onBack = { screen = Screen.MAP }, onOpenTravelTimes = { screen = Screen.TRAVEL })
        Screen.SETTINGS -> SettingsScreen(settings = settings, onBack = { screen = Screen.MAP }, onOpenHistory = { screen = Screen.HISTORY })
        Screen.TRAVEL -> TravelTimesScreen(onBack = { screen = Screen.SCHEDULE })
        Screen.HISTORY -> HistoryScreen(onBack = { screen = Screen.SETTINGS })
    }
}
