package com.namazim

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Odak theme's home header: the day as a ring of five prayers, countdown in the middle. */
@Composable
fun OdakHeader(
    city: String, today: LocalDate, holy: Pair<HolyDay, String>, slots: List<Slot>, now: Long, store: Store,
    label: String, time: String, sub: String,
) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse",
    )
    // Each segment fills with an animation the moment its prayer is marked.
    val fills = slots.map { s -> animateFloatAsState(if (store.isPrayed(s.key)) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "seg").value }

    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Bugün", style = NumStyle, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Pal.Text)
                Text("${today.format(DateTimeFormatter.ofPattern("d MMMM", Locale("tr")))} · ${hijri(today)}", color = Pal.Muted, fontSize = 13.sp)
                Text("✦ ${holy.first.name} · ${holy.second}", color = Pal.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.clip(CircleShape).background(Pal.Card).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, null, tint = Pal.Gold, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(city.ifEmpty { "Konumum" }, color = Pal.Text, fontSize = 13.sp)
            }
        }
        Box(Modifier.padding(top = 16.dp).size(232.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 14.dp.toPx()
                val tl = Offset(w / 2, w / 2); val sz = Size(size.width - w, size.height - w)
                slots.forEachIndexed { i, s ->
                    val start = -90f + i * 72f + 5f
                    val owed = now in s.start until s.end && !store.isPrayed(s.key) && store.tracked(s)
                    val missed = now >= s.end && !store.isPrayed(s.key) && store.tracked(s)
                    drawArc(if (missed) Pal.Warn.copy(alpha = .55f) else Pal.Track, start, 62f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                    if (owed) drawArc(Pal.Text.copy(alpha = pulse), start, 62f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                    if (fills[i] > 0f) drawArc(Pal.Gold, start, 62f * fills[i], false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = Pal.Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(time, style = NumStyle, fontSize = 44.sp, color = Pal.Text)
                Text(sub, color = Pal.Muted, fontSize = 13.sp)
            }
        }
    }
}
