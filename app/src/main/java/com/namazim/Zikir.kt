package com.namazim

import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Beads on one turn of the ring; bigger targets fill it in turns of 33. */
private const val TURN = 33

/** Zikir tab: the user's own list as chips, a tesbih ring in the middle, the whole body counts. */
@Composable
fun ZikirScreen() {
    StoreRev.n.let { } // recompose on every count / list change
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val vibrator = remember { ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator }
    val scope = rememberCoroutineScope()
    val glow = remember { Animatable(0f) }
    val zikirs = store.zikirs
    val counts = store.zikirCounts()
    var sel by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    val i = sel.coerceIn(0, (zikirs.size - 1).coerceAtLeast(0))

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Zikir", rule = false) {
            IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Zikirleri düzenle", tint = Pal.Muted) }
        }
        if (zikirs.isEmpty()) {
            EmptyState("Listen boş", "Bir zikir ekle; sayacı her gün imsakta sıfırlanır.", "Zikir ekle", { editing = true },
                Modifier.padding(horizontal = Space.xl, vertical = Space.xxl))
        } else {
            val z = zikirs[i]
            val c = counts[z.name] ?: 0
            LazyRow(
                contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                itemsIndexed(zikirs) { idx, it -> ZikirChip(it, counts[it.name] ?: 0, idx == i) { sel = idx } }
            }
            // the whole body is the tap target, so counting works without looking
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .semantics { contentDescription = "${z.name}, ${c} / ${z.target}. Saymak için dokun." }
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        store.addZikir(z.name, 1)
                        if (c + 1 == z.target) {
                            vibrator?.vibrate(VibrationEffect.createOneShot(320, VibrationEffect.DEFAULT_AMPLITUDE))
                            scope.launch {
                                glow.snapTo(1f)
                                delay(450)
                                sel = nextOpenZikir(zikirs, counts + (z.name to c + 1), i)
                                glow.animateTo(0f, tween(700))
                            }
                        } else {
                            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(z.name, style = NumStyle, fontSize = Type.h2, color = Pal.Text)
                    Spacer(Modifier.height(Space.l))
                    Box(contentAlignment = Alignment.Center) {
                        TesbihRing(c, z.target, glow.value, Modifier.size(280.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$c", style = NumStyle, fontSize = 84.sp, lineHeight = 84.sp, color = Pal.Text)
                            Text("/ ${z.target}", color = Pal.Muted, fontSize = Type.body)
                            if (z.target > TURN) Text("${(c / TURN + 1).coerceAtMost((z.target + TURN - 1) / TURN)}. tur", color = Pal.Gold, fontSize = Type.label)
                        }
                    }
                    Spacer(Modifier.height(Space.s))
                    Text(if (c == 0) "her yere dokun" else " ", color = Pal.Muted.copy(alpha = .6f), fontSize = Type.label)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = Space.xl, end = Space.xl, bottom = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.heightIn(min = TouchMin).clip(CircleShape).clickable(enabled = c > 0) { store.addZikir(z.name, -1) }
                        .semantics { contentDescription = "Bir geri al" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("−1", Modifier.clip(CircleShape).background(Pal.Track).padding(horizontal = Space.l, vertical = Space.s),
                        color = if (c > 0) Pal.Gold else Pal.Muted.copy(alpha = .4f), fontSize = Type.body, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                Text("Bugün ", color = Pal.Muted, fontSize = Type.body2)
                Text("%${(zikirPetal(zikirs, counts).progress * 100).toInt()}", color = Pal.Gold, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (editing) ZikirSheet(store, zikirs) { editing = false }
}

@Composable
private fun ZikirChip(z: Zikir, count: Int, on: Boolean, onClick: () -> Unit) {
    val done = count >= z.target
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(Radius.s))
            .background(if (on) Pal.Track else Color.Transparent)
            .border(1.dp, if (on) Color.Transparent else Pal.Border, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick).padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) { Icon(Icons.Default.Check, null, tint = Pal.Gold, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(Space.xs)) }
        Text("${z.name} · ${count.coerceAtMost(z.target)}/${z.target}", color = if (on) Pal.Gold else Pal.Muted,
            fontSize = Type.body2, fontWeight = FontWeight.Medium)
    }
}

/** Shaded beads on a circle: counted ones gold, the rest dark; [glow] flashes the ring when a target is reached. */
@Composable
private fun TesbihRing(count: Int, target: Int, glow: Float, modifier: Modifier) {
    val beads = target.coerceAtMost(TURN)
    val filled = when {
        count >= target -> beads
        target <= TURN -> count
        else -> count % TURN
    }
    Canvas(modifier) {
        val r = 7.5.dp.toPx()
        val ring = size.minDimension / 2 - r - 4.dp.toPx()
        if (glow > 0f) drawCircle(Pal.Gold.copy(alpha = .16f * glow), ring + r * 2.4f, center)
        for (k in 0 until beads) {
            val a = 2 * Math.PI * k / beads - Math.PI / 2
            val p = Offset(center.x + ring * cos(a).toFloat(), center.y + ring * sin(a).toFloat())
            val lit = k < filled
            // light from the upper left, like the rose's scene
            val brush = Brush.radialGradient(
                if (lit) listOf(Color(0xFFFFF1CF), Pal.Gold, Color(0xFF8A6420)) else listOf(Color(0xFF3A4A68), Pal.Track),
                center = p + Offset(-r * .35f, -r * .45f), radius = r * 1.6f,
            )
            drawCircle(brush, if (lit) r else r * .92f, p)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZikirSheet(store: Store, zikirs: List<Zikir>, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("33") }
    val n = name.trim()
    val t = target.trim().toIntOrNull()
    val error = when {
        n.isNotEmpty() && zikirs.any { it.name.equals(n, ignoreCase = true) } -> "Bu zikir listede var"
        target.isNotBlank() && (t == null || t !in 1..10_000) -> "Hedef 1 ile 10.000 arası olmalı"
        else -> null
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Pal.Gold, unfocusedBorderColor = Pal.Border, focusedLabelColor = Pal.Gold,
        cursorColor = Pal.Gold, focusedTextColor = Pal.Text, unfocusedTextColor = Pal.Text,
    )
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Pal.Card) {
        Column(Modifier.padding(horizontal = Space.xl).navigationBarsPadding().padding(bottom = Space.l)) {
            Text("Zikirlerim", style = NumStyle, fontSize = Type.h2, color = Pal.Text)
            Text("Her gün imsakta sıfırlanır", color = Pal.Muted, fontSize = Type.body2)
            Spacer(Modifier.height(Space.s))
            zikirs.forEachIndexed { idx, z ->
                if (idx > 0) HorizontalDivider(color = Pal.Line)
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(z.name, Modifier.weight(1f), color = Pal.Text, fontSize = Type.body)
                    Text("${z.target}", color = Pal.Muted, fontSize = Type.body2)
                    IconButton(onClick = { store.zikirs = zikirs - z }) { Icon(Icons.Default.Delete, "${z.name} sil", tint = Pal.Muted, modifier = Modifier.size(20.dp)) }
                }
            }
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("Zikir adı") }, singleLine = true, colors = fieldColors)
                OutlinedTextField(target, { target = it.filter(Char::isDigit).take(5) }, Modifier.width(96.dp), label = { Text("Hedef") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = fieldColors)
            }
            if (error != null) Text(error, Modifier.padding(top = Space.xs), color = Pal.Warn, fontSize = Type.label)
            Spacer(Modifier.height(Space.l))
            val ok = n.isNotEmpty() && t != null && error == null
            GoldButton("Zikir ekle", Modifier.fillMaxWidth().alpha(if (ok) 1f else .4f), Icons.Default.Add) {
                if (ok) { store.zikirs = zikirs + Zikir(n, t!!); name = ""; target = "33" }
            }
        }
    }
}
