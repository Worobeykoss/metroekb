package com.worobeyko.metroekb.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Тема карты: базовый стиль и цвета наших слоёв. «Чертёж» - тёмная подложка под синим
 * скримом с сеткой и «полой» линией; «Ретро» - светлая подложка под бумажным скримом.
 */
enum class MapTheme(
    val title: String,
    val styleUrl: String,
    val scrimColor: String,
    val scrimOpacity: Float,
    /** Широкая подложка линии и сама линия поверх неё. */
    val casingColor: String,
    val casingWidth: Float,
    val lineColor: String,
    val lineWidth: Float,
    val stationFill: String,
    val stationStroke: String,
    val labelColor: String,
    val labelHalo: String,
    val gridColor: String?,
) {
    DARK(
        "Тёмная", "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json",
        "#0B0E14", 0.45f, "#0B0E14", 9f, "#E8452A", 5f,
        "#FFFFFF", "#E8452A", "#FFFFFF", "#0B0E14", null,
    ),
    BLUEPRINT(
        "Чертёж", "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json",
        "#0B2545", 0.82f, "#CFE3FF", 8f, "#0B2545", 3.5f,
        "#0B2545", "#CFE3FF", "#CFE3FF", "#0B2545", "#1F4A7A",
    ),
    RETRO(
        "Ретро", "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json",
        "#E9DCC0", 0.55f, "#F2EBDA", 9f, "#8B3A2A", 5f,
        "#F2EBDA", "#3A2A1A", "#3A2A1A", "#F2EBDA", null,
    ),
}

/**
 * Цвета поездов по направлениям (ARGB). Compose-состояние: панели и легенда
 * перерисовываются сами, карта перекрашивает иконки через контроллер.
 */
object TrainPalette {
    const val DEFAULT_SOUTH = 0xFFFFB300.toInt() // янтарь
    const val DEFAULT_NORTH = 0xFF40C4FF.toInt() // голубой

    /** Готовые цвета на выбор в настройках. */
    val CHOICES = listOf(
        0xFFFFB300, 0xFF40C4FF, 0xFFFF5A6E, 0xFFB388FF, 0xFF00E676, 0xFFFF9E40, 0xFFFFFFFF, 0xFFFFEB3B,
    ).map { it.toInt() }

    var south by mutableIntStateOf(DEFAULT_SOUTH)
    var north by mutableIntStateOf(DEFAULT_NORTH)
}
