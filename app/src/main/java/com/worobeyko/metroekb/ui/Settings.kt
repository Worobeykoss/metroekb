package com.worobeyko.metroekb.ui

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.worobeyko.metroekb.map.MapTheme
import com.worobeyko.metroekb.map.TrainPalette

/** Какой график показывать: авто по дню недели, либо принудительно. */
enum class DayMode { AUTO, WEEKDAY, WEEKEND }

/** Иконка приложения: activity-alias в манифесте. */
enum class AppIcon(val title: String, val alias: String) {
    DEFAULT("Классика", "LauncherDefault"),
    BLUEPRINT("Чертёж", "LauncherBlueprint"),
    RETRO("Ретро", "LauncherRetro"),
    NIGHT("Ночь", "LauncherNight"),
}

/** Настройки приложения; сохраняются в SharedPreferences (см. [save]/[load]). */
class Settings {
    var dayMode by mutableStateOf(DayMode.AUTO)
    var showTrains by mutableStateOf(true)
    var showTrainTimers by mutableStateOf(true)
    var showPulses by mutableStateOf(true)
    var showCatch by mutableStateOf(true)
    var showTrails by mutableStateOf(true)
    var showLabels by mutableStateOf(true)
    var showWalkRoute by mutableStateOf(true)
    var walkSpeedKmh by mutableFloatStateOf(4.9f)
    /** Подстраивать скорость ходьбы по GPS и сколько замеров уже учтено. */
    var learnWalkSpeed by mutableStateOf(true)
    var walkSamples by mutableIntStateOf(0)
    var theme by mutableStateOf(MapTheme.DARK)
    var southColor by mutableIntStateOf(TrainPalette.DEFAULT_SOUTH)
    var northColor by mutableIntStateOf(TrainPalette.DEFAULT_NORTH)
    /** Компас ведёт только к входам с пандусом/лифтом. */
    var accessibleOnly by mutableStateOf(false)
    var appIcon by mutableStateOf(AppIcon.DEFAULT)

    /** Снимок всех полей - чтение регистрирует зависимости для snapshotFlow. */
    fun snapshot(): List<Any> = listOf(
        dayMode, showTrains, showTrainTimers, showPulses, showCatch, showTrails, showLabels,
        showWalkRoute, walkSpeedKmh, learnWalkSpeed, walkSamples, theme, southColor, northColor,
        accessibleOnly, appIcon,
    )

    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putString("dayMode", dayMode.name)
            .putBoolean("showTrains", showTrains)
            .putBoolean("showTrainTimers", showTrainTimers)
            .putBoolean("showPulses", showPulses)
            .putBoolean("showCatch", showCatch)
            .putBoolean("showTrails", showTrails)
            .putBoolean("showLabels", showLabels)
            .putBoolean("showWalkRoute", showWalkRoute)
            .putFloat("walkSpeedKmh", walkSpeedKmh)
            .putBoolean("learnWalkSpeed", learnWalkSpeed)
            .putInt("walkSamples", walkSamples)
            .putString("theme", theme.name)
            .putInt("southColor", southColor)
            .putInt("northColor", northColor)
            .putBoolean("accessibleOnly", accessibleOnly)
            .putString("appIcon", appIcon.name)
            .apply()
    }

    companion object {
        private inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, default: T): T =
            getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

        fun load(prefs: SharedPreferences): Settings = Settings().apply {
            dayMode = prefs.enum("dayMode", DayMode.AUTO)
            showTrains = prefs.getBoolean("showTrains", true)
            showTrainTimers = prefs.getBoolean("showTrainTimers", true)
            showPulses = prefs.getBoolean("showPulses", true)
            showCatch = prefs.getBoolean("showCatch", true)
            showTrails = prefs.getBoolean("showTrails", true)
            showLabels = prefs.getBoolean("showLabels", true)
            showWalkRoute = prefs.getBoolean("showWalkRoute", true)
            walkSpeedKmh = prefs.getFloat("walkSpeedKmh", 4.9f)
            learnWalkSpeed = prefs.getBoolean("learnWalkSpeed", true)
            walkSamples = prefs.getInt("walkSamples", 0)
            theme = prefs.enum("theme", MapTheme.DARK)
            southColor = prefs.getInt("southColor", TrainPalette.DEFAULT_SOUTH)
            northColor = prefs.getInt("northColor", TrainPalette.DEFAULT_NORTH)
            accessibleOnly = prefs.getBoolean("accessibleOnly", false)
            appIcon = prefs.enum("appIcon", AppIcon.DEFAULT)
        }
    }
}
