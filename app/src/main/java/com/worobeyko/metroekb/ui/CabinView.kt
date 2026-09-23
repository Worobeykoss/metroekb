package com.worobeyko.metroekb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worobeyko.metroekb.data.Directions
import com.worobeyko.metroekb.data.Station
import com.worobeyko.metroekb.domain.TrainPosition
import com.worobeyko.metroekb.domain.TrainRef
import com.worobeyko.metroekb.domain.LineGeometry
import kotlin.math.min
import kotlin.math.pow

private val TunnelBg = Color(0xFF07090D)
private val RingColor = Color(0xFF39414F)
private val LampColor = Color(0xFFFFB300)
private val SleeperColor = Color(0xFF4A4030)
private val RailColor = Color(0xFF8A93A6)
private val PlatformColor = Color(0xFFD9D2B8)

/** Кольца тоннеля - через каждые RING_SPACING_M метров. */
private const val RING_SPACING_M = 8f
private const val RINGS = 16
/** С какого расстояния до станции видна платформа, м. */
private const val PLATFORM_VISIBLE_M = 160.0

/**
 * Скорость на перегоне по доле пройденного времени: разгон первые 20%, торможение последние 25%.
 * Средняя трапеции = 0.775·vmax, поэтому vmax подбираем так, чтобы средняя совпала с графиком.
 */
private fun speedMps(distanceM: Double, durationMin: Int, fraction: Double): Double {
    if (durationMin <= 0) return 0.0
    val vmax = distanceM / (durationMin * 60.0) / 0.775
    return vmax * min(1.0, min(fraction / 0.2, (1 - fraction) / 0.25)).coerceAtLeast(0.0)
}

/**
 * Вид из кабины: едем по тоннелю вместе с реальным поездом [ref]. Кольца тоннеля летят
 * навстречу со скоростью поезда, перед станцией появляется светлая платформа с названием.
 * [locate] даёт текущее положение поезда (null - прибыл на конечную).
 */
@Composable
fun CabinView(
    ref: TrainRef,
    stations: List<Station>,
    geometry: LineGeometry,
    locate: () -> TrainPosition?,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    val locator by rememberUpdatedState(locate)
    var pos by remember { mutableStateOf<TrainPosition?>(locate()) }
    var everMoved by remember { mutableStateOf(pos != null) }
    var phase by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(ref) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                val p = locator()
                pos = p
                if (p != null) {
                    everMoved = true
                    val d = geometry.legLength(p.fromIndex, p.toIndex)
                    speed = speedMps(d, p.arriveMin - p.departMin, p.fraction).toFloat()
                    phase += speed * dt / RING_SPACING_M
                } else {
                    speed = 0f
                }
            }
        }
    }

    val p = pos
    val next = p?.let { stations.getOrNull(it.toIndex) }
    val remaining = p?.let { geometry.legLength(it.fromIndex, it.toIndex) * (1 - it.fraction) }
    val approach = remaining?.let { (1 - it / PLATFORM_VISIBLE_M).coerceIn(0.0, 1.0).toFloat() } ?: 0f
    val accent = if (ref.direction == Directions.SOUTH) Color(0xFFFFB300) else Color(0xFF40C4FF)
    val measurer = rememberTextMeasurer()

    Box(Modifier.fillMaxSize().background(TunnelBg)) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.46f)

            // Платформа впереди: растёт по мере приближения к станции.
            if (approach > 0f && next != null) {
                val w = 60f + approach * approach * size.width * 1.4f
                val h = w * 0.55f
                drawRoundRect(
                    color = PlatformColor.copy(alpha = 0.15f + 0.6f * approach),
                    topLeft = Offset(c.x - w / 2f, c.y - h / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w * 0.08f),
                )
                val layout = measurer.measure(
                    next.name,
                    TextStyle(
                        color = Color(0xFF16181F).copy(alpha = min(1f, approach * 2f)),
                        fontSize = (13f + approach * 20f).sp,
                        fontWeight = FontWeight.Black,
                    ),
                )
                drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
            }

            // Кольца тоннеля, лампы и шпалы - летят навстречу.
            for (i in 0 until RINGS) {
                val z = ((i + phase) % RINGS) / RINGS
                val k = z.pow(2.3f)
                val w = 40f + k * size.width * 1.7f
                val h = w * 0.62f
                val alpha = min(1f, z * 1.6f)
                drawRoundRect(
                    color = RingColor.copy(alpha = alpha),
                    topLeft = Offset(c.x - w / 2f, c.y - h / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w * 0.3f),
                    style = Stroke(width = 1.5f + k * 10f),
                )
                if (i % 2 == 0) {
                    drawCircle(LampColor.copy(alpha = alpha), radius = 2f + k * 9f, center = Offset(c.x - w * 0.47f, c.y - h * 0.08f))
                }
                val sy = c.y + 14f + k * size.height * 0.55f
                val half = 6f + k * size.width * 0.35f
                drawLine(SleeperColor.copy(alpha = alpha), Offset(c.x - half, sy), Offset(c.x + half, sy), strokeWidth = 1f + k * 7f)
            }
            drawLine(RailColor, Offset(c.x - 4f, c.y + 14f), Offset(size.width * 0.18f, size.height), strokeWidth = 5f)
            drawLine(RailColor, Offset(c.x + 4f, c.y + 14f), Offset(size.width * 0.82f, size.height), strokeWidth = 5f)
        }

        // Приборы поверх тоннеля.
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "→ ${Directions.terminal(ref.direction)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF16181F),
                    modifier = Modifier
                        .background(accent, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                CloseButton(onClose, Color.White)
            }
            Spacer(Modifier.height(10.dp))
            if (p != null && next != null) {
                Text("следующая - ${next.name}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                EtaLabel(p.etaSeconds, accent, 40.sp)
            }
        }

        val speedKmh = (speed * 3.6f).toInt()
        val bottomText = when {
            p == null && everMoved -> "Конечная. Поезд дальше не идёт"
            p == null -> "Поезд ещё не отправился"
            approach > 0.85f && speedKmh < 8 -> "прибываем"
            else -> "$speedKmh км/ч"
        }
        Text(
            bottomText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .background(Color(0xCC1B2030), RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}
