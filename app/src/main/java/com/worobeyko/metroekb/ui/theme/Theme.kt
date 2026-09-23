package com.worobeyko.metroekb.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Акцент = красный цвет линии 1.
val LineRed = Color(0xFFE8452A)
val TrainSouth = Color(0xFFFFB300) // на юг — тёплый янтарь
val TrainNorth = Color(0xFF40C4FF) // на север — холодный голубой
val PanelBg = Color(0xFF11151F)
val ScreenBg = Color(0xFF0B0E14)

private val DarkColors = darkColorScheme(
    primary = LineRed,
    onPrimary = Color.White,
    secondary = TrainNorth,
    background = ScreenBg,
    onBackground = Color(0xFFE7EAF0),
    surface = PanelBg,
    onSurface = Color(0xFFE7EAF0),
    surfaceVariant = Color(0xFF1B2130),
    onSurfaceVariant = Color(0xFFAAB2C2),
)

@Composable
fun MetroEkbTheme(content: @Composable () -> Unit) {
    // Приложение всегда тёмное (по требованию), независимо от системной темы.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content,
    )
}
