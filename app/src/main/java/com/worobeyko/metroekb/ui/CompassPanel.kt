package com.worobeyko.metroekb.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worobeyko.metroekb.data.Entrance
import com.worobeyko.metroekb.data.Station
import com.worobeyko.metroekb.location.UserLocation
import com.worobeyko.metroekb.location.bearingDegrees
import com.worobeyko.metroekb.location.distanceMeters
import com.worobeyko.metroekb.location.rememberHeading
import kotlin.math.abs
import kotlin.math.roundToInt

/** Ближайший вход в метро к пользователю и расстояние до него по прямой. */
data class NearestEntrance(val entrance: Entrance, val meters: Double)

fun nearestEntrance(user: UserLocation, entrances: List<Entrance>, accessibleOnly: Boolean = false): NearestEntrance? =
    (if (accessibleOnly) entrances.filter { it.accessible }.ifEmpty { entrances } else entrances)
        .map { NearestEntrance(it, distanceMeters(user.lat, user.lon, it.lat, it.lon)) }
        .minByOrNull { it.meters }

/** Угол в диапазон −180..180. */
private fun normalize(deg: Double): Double = ((deg % 360) + 540) % 360 - 180

private fun distanceText(m: Double): String = when {
    m < 1000 -> "${((m / 10).roundToInt() * 10).coerceAtLeast(10)} м"
    else -> "%.1f км".format(m / 1000).replace('.', ',')
}

/**
 * Компас к ближайшему входу: стрелка поворачивается вместе с телефоном и смотрит на вход.
 * Совпало направление (±12°) — стрелка зеленеет. Без датчика компаса стрелка считает
 * «верх экрана» севером и говорит об этом.
 */
@Composable
fun CompassPanel(
    user: UserLocation?,
    entrances: List<Entrance>,
    stations: List<Station>,
    walkSpeedKmh: Float,
    accessibleOnly: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heading by rememberHeading(enabled = true, lat = user?.lat, lon = user?.lon)
    val target = user?.let { nearestEntrance(it, entrances, accessibleOnly) }

    Column(
        modifier = modifier
            .width(280.dp)
            .background(BubbleBg, RoundedCornerShape(20.dp))
            .border(BorderStroke(3.dp, BubbleInk), RoundedCornerShape(20.dp))
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text("🧭 КОМПАС", fontSize = 10.sp, fontWeight = FontWeight.Black, color = AccessibleBlue)
                Text(if (accessibleOnly) "Доступный вход" else "Ближайший вход", fontSize = 20.sp, fontWeight = FontWeight.Black, color = BubbleInk)
            }
            CloseButton(onClose)
        }

        if (user == null || target == null) {
            Spacer(Modifier.height(16.dp))
            Text("Жду геопозицию…", fontSize = 15.sp, color = BubbleInkSoft)
            Spacer(Modifier.height(16.dp))
            return@Column
        }

        val station = stations.firstOrNull { it.id == target.entrance.stationId }
        val toEntrance = bearingDegrees(user.lat, user.lon, target.entrance.lat, target.entrance.lon)
        val h = heading
        val delta = normalize(toEntrance - (h?.toDouble() ?: 0.0))
        val aligned = h != null && abs(delta) < 12
        val arrowColor = if (aligned) CatchGreen else BubbleInk

        Text(
            buildString {
                append("ст. ${station?.name ?: "метро"}")
                if (target.entrance.ref.isNotEmpty()) append(" · вход ${target.entrance.ref}")
                if (target.entrance.accessible) append(" · ♿")
            },
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BubbleInkSoft,
            modifier = Modifier.padding(end = 8.dp),
        )
        Spacer(Modifier.height(10.dp))

        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(150.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension / 2f
                drawCircle(arrowColor.copy(alpha = 0.08f), radius = r, center = c)
                drawCircle(arrowColor.copy(alpha = 0.35f), radius = r - 2f, center = c, style = Stroke(width = 3f))
                rotate(delta.toFloat(), pivot = c) {
                    val path = Path().apply {
                        moveTo(c.x, c.y - r * 0.78f)
                        lineTo(c.x + r * 0.34f, c.y + r * 0.36f)
                        lineTo(c.x, c.y + r * 0.16f)
                        lineTo(c.x - r * 0.34f, c.y + r * 0.36f)
                        close()
                    }
                    drawPath(path, arrowColor)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        val minutes = (target.meters / (walkSpeedKmh * 1000.0 / 3600.0) / 60.0).roundToInt().coerceAtLeast(1)
        Text(
            "${distanceText(target.meters)} · ~$minutes мин",
            fontSize = 20.sp, fontWeight = FontWeight.Black, color = BubbleInk,
        )
        Text(
            when {
                h == null -> "нет датчика компаса: стрелка считает верх экрана севером"
                aligned -> "прямо туда!"
                else -> "поверните телефон по стрелке"
            },
            fontSize = 12.sp,
            fontWeight = if (aligned) FontWeight.Black else FontWeight.Normal,
            color = if (aligned) CatchGreen else BubbleInkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}
