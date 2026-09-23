package com.worobeyko.metroekb.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worobeyko.metroekb.data.Directions
import com.worobeyko.metroekb.data.Station
import com.worobeyko.metroekb.domain.TrainPosition

/** Служебная минута -> "HH:MM". */
internal fun serviceHhmm(minute: Int): String {
    val m = Math.floorMod(minute, 24 * 60)
    return "%02d:%02d".format(m / 60, m % 60)
}

/** Кнопка-«пилюля» в комикс-стиле. filled — залитая (включённое состояние). */
@Composable
internal fun PillButton(text: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        color = if (filled) BubbleBg else BubbleInk,
        modifier = modifier
            .background(if (filled) BubbleInk else Color.Transparent, RoundedCornerShape(50))
            .border(BorderStroke(2.dp, BubbleInk), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * Окно поезда (по тапу на поезд): куда едет, перегон, примерно когда будет на следующей
 * станции, прогресс перегона; «Следить» ведёт камеру за поездом, «Из кабины» — вид из кабины.
 * train == null — поезд уже прибыл на конечную.
 */
@Composable
fun TrainDialog(
    train: TrainPosition?,
    direction: String,
    stations: List<Station>,
    following: Boolean,
    onToggleFollow: () -> Unit,
    onCabin: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bubble = SpeechBubbleShape(corner = 20.dp, tailWidth = 26.dp, tailHeight = 14.dp)
    val accent = accentOf(direction)
    Column(
        modifier = modifier
            .width(296.dp)
            .background(BubbleBg, bubble)
            .border(BorderStroke(3.dp, BubbleInk), bubble)
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 26.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 4.dp).width(5.dp).height(40.dp).background(accent, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text("ПОЕЗД", fontSize = 10.sp, fontWeight = FontWeight.Black, color = accent)
                Text(
                    "→ ${Directions.terminal(direction)}",
                    fontSize = 20.sp, fontWeight = FontWeight.Black, color = BubbleInk, lineHeight = 23.sp,
                )
            }
            CloseButton(onClose)
        }
        Spacer(Modifier.height(8.dp))

        Column(Modifier.padding(end = 8.dp)) {
            if (train == null) {
                Text("Поезд прибыл на конечную.", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BubbleInkSoft)
                return@Column
            }
            val from = stations.getOrNull(train.fromIndex)?.name.orEmpty()
            val to = stations.getOrNull(train.toIndex)?.name.orEmpty()
            Text("едет: $from → $to", fontSize = 12.sp, color = BubbleInkSoft)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                EtaLabel(train.etaSeconds, accent, 32.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.padding(bottom = 3.dp)) {
                    Text("до ст. $to", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BubbleInk)
                    Text("по графику ${serviceHhmm(train.arriveMin)}", fontSize = 11.sp, color = BubbleInkSoft)
                }
            }
            Spacer(Modifier.height(8.dp))
            // Прогресс перегона.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(BubbleRule, RoundedCornerShape(4.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(train.fraction.toFloat().coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .background(accent, RoundedCornerShape(4.dp)),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (following) "✓ Следим" else "👁 Следить", following, onToggleFollow)
                PillButton("🚇 Из кабины", false, onCabin)
            }
        }
    }
}

/**
 * Компактная плашка на время слежения: камера держит поезд в центре экрана, поэтому
 * большое окно прячем, чтобы оно не закрывало сам поезд.
 */
@Composable
fun FollowBar(
    train: TrainPosition?,
    direction: String,
    stations: List<Station>,
    onStop: () -> Unit,
    onCabin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = accentOf(direction)
    Column(
        modifier = modifier
            .width(300.dp)
            .background(BubbleBg, RoundedCornerShape(16.dp))
            .border(BorderStroke(2.5.dp, BubbleInk), RoundedCornerShape(16.dp))
            .padding(start = 12.dp, top = 8.dp, end = 6.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(34.dp).background(accent, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("👁 СЛЕДИМ → ${Directions.terminalShort(direction).uppercase()}", fontSize = 10.sp, fontWeight = FontWeight.Black, color = accent)
                val to = train?.let { stations.getOrNull(it.toIndex)?.name }
                Text(
                    if (train != null && to != null) "до ст. $to" else "прибыл на конечную",
                    fontSize = 14.sp, fontWeight = FontWeight.Black, color = BubbleInk,
                )
            }
            if (train != null) EtaLabel(train.etaSeconds, accent, 22.sp)
            CloseButton(onStop)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 14.dp)) {
            PillButton("Не следить", false, onStop)
            PillButton("🚇 Из кабины", false, onCabin)
        }
    }
}

/** Окно пасхалки: тапнули по поезду-призраку. */
@Composable
fun GhostDialog(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val bubble = SpeechBubbleShape(corner = 20.dp, tailWidth = 26.dp, tailHeight = 14.dp)
    val ghostBg = Color(0xFF14201A)
    val ghostInk = Color(0xFFB9F6CA)
    Column(
        modifier = modifier
            .width(270.dp)
            .background(ghostBg, bubble)
            .border(BorderStroke(3.dp, ghostInk), bubble)
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 26.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text("👻 Тссс…", fontSize = 22.sp, fontWeight = FontWeight.Black, color = ghostInk, modifier = Modifier.weight(1f).padding(top = 4.dp))
            CloseButton(onClose, ghostInk)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Это служебный поезд. Он катается по линии, когда метро спит. Никому не рассказывай.",
            fontSize = 14.sp,
            lineHeight = 19.sp,
            color = ghostInk,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}
