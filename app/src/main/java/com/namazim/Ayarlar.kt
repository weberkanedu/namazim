package com.namazim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 8.dp), color = Pal.Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Column(Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Pal.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun Chips(options: List<Int>, isOn: (Int) -> Boolean, label: (Int) -> String, onClick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { n ->
            val on = isOn(n)
            Box(Modifier.heightIn(min = 36.dp).clip(CircleShape).background(if (on) Pal.Gold else Color.Transparent)
                .border(1.dp, if (on) Pal.Gold else Pal.Border, CircleShape).clickable { onClick(n) }.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center) {
                Text(label(n), color = if (on) Pal.Ground else Pal.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), color = Pal.Text, fontSize = 14.sp)
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Pal.Gold, checkedThumbColor = Pal.Ground,
            uncheckedTrackColor = Pal.Track, uncheckedThumbColor = Pal.Muted, uncheckedBorderColor = Pal.Border))
    }
}

@Composable
private fun StatusRow(text: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), color = Pal.Text, fontSize = 14.sp)
        if (ok) Text("✓ Açık", color = Pal.Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        else TextButton(onClick = onClick) { Text(action, color = Pal.Gold, fontWeight = FontWeight.SemiBold) }
    }
}

/** Small theme preview: a strip of the theme's look. */
@Composable
private fun ThemeCard(p: Palette, selected: Boolean, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).border(2.dp, if (selected) Pal.Gold else Pal.Border, RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(90.dp).background(
            if (p.id == SeherPalette.id) Brush.verticalGradient(listOf(Color(0xFF2E5C99), Color(0xFFE7B07F), p.ground))
            else Brush.verticalGradient(listOf(p.ground, p.ground))), contentAlignment = Alignment.Center) {
            if (p.id == OdakPalette.id) {
                Canvas(Modifier.size(64.dp)) {
                    val w = 7.dp.toPx(); val tl = Offset(w / 2, w / 2); val sz = Size(size.width - w, size.height - w)
                    for (i in 0 until 5) drawArc(if (i < 2) p.accent else p.track, -90f + i * 72 + 5, 62f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                }
            } else {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFFFE9B8)))
            }
        }
        Column(Modifier.background(p.card).fillMaxWidth().padding(10.dp)) {
            Text(p.name, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = p.display)
            Text(subtitle, color = p.muted, fontSize = Type.label)
        }
    }
}

@Composable
fun AyarlarScreen(onBack: () -> Unit) {
    StoreRev.n.let { } // keep the chips in sync with what was just written
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var v by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    fun changed() { v++; Reminders.scheduleNext(ctx) }
    var dialog by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    fun openDialog(kind: String, initial: String) { dialog = kind; draft = initial }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            locating = true
            scope.launch { fetchLocation(ctx, store); locating = false; changed() }
        }
    }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { v++ }
    rememberNow() // re-check permission states after coming back from system settings

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Ayarlar", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Pal.Text) }
        })
        // key(v): chip'lerin seçimi her dokunuşta yeniden çizilsin (lambda'lar Compose'da hatırlanıyor)
        key(v) {
        Section("Görünüm") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(SeherPalette, OdakPalette).forEach { p ->
                    ThemeCard(
                        p, Pal.current.id == p.id,
                        if (p.id == SeherPalette.id) when (store.skyMode) { "shader" -> "Canlı gökyüzü"; "video" -> "Video gökyüzü"; else -> "Fotoğraf gökyüzü" }
                        else "Halka, sade ve sportif",
                        Modifier.weight(1f),
                    ) {
                        store.theme = p.id; Pal.current = p; NamazimWidget.refresh(ctx); v++
                    }
                }
            }
            // Which sky the Seher header draws: the live shader, layed photo plates, or a looping clip.
            if (store.theme != OdakPalette.id) {
            Text("Gökyüzü", color = Pal.Text, fontSize = Type.body)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("shader" to "Canlı", "photo" to "Fotoğraf", "video" to "Video").forEach { (id, label) ->
                    val on = store.skyMode == id
                    Box(Modifier.heightIn(min = 36.dp).clip(CircleShape).background(if (on) Pal.Gold else Color.Transparent)
                        .border(1.dp, if (on) Pal.Gold else Pal.Border, CircleShape).clickable { store.skyMode = id; v++ }
                        .padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = if (on) Pal.Ground else Pal.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            }
        }

        Section("Hatırlatmalar") {
            ValueRow(
                "Vakitten önce hazırlan bildirimi",
                if (store.prepOffsets.isEmpty()) "kapalı" else store.prepOffsets.sortedDescending().joinToString(", ") { "$it dk" },
            ) { openDialog("prep", store.prepOffsets.sortedDescending().joinToString(", ")) }
            ValueRow("\"Kıldın mı?\" sıklığı", "${store.nagEvery} dk") { openDialog("nag", "${store.nagEvery}") }
            SwitchRow("Vaktin son yarım saatinde 5 dakikada bir", v.let { store.nagBurst }) { store.nagBurst = it; changed() }
            ValueRow("Ertele süresi", "${store.snoozeMin} dk") { openDialog("snooze", "${store.snoozeMin}") }
            GhostButton("Deneme bildirimi gönder", Modifier.fillMaxWidth(), color = Pal.Gold) { Reminders.test(ctx) }
        }

        Section("Okuma ve kaza") {
            ValueRow("Günlük okuma hedefi", "${store.readGoal} sayfa") { openDialog("goal", "${store.readGoal}") }
            ValueRow("Günde kılınacak kaza", "${store.kazaRate} vakit") { openDialog("kaza", "${store.kazaRate}") }
        }

        Section("Konum") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (locating) "Konum alınıyor…" else store.city.ifEmpty { "Konum belirlenmedi" }, Modifier.weight(1f), color = Pal.Text, fontSize = 14.sp)
                TextButton(onClick = { askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                    Text("Güncelle", color = Pal.Gold, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Section("Telefon izinleri") {
            val notifOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val batteryOk = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
            StatusRow("Bildirimler", notifOk, "İzin ver") { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
            StatusRow("Pil kısıtlaması yok", batteryOk, "Kaldır") {
                @SuppressLint("BatteryLife")
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                ctx.startActivity(i)
            }
            if (Build.MANUFACTURER.equals("xiaomi", true)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Otomatik başlatma (Xiaomi)", Modifier.weight(1f), color = Pal.Text, fontSize = 14.sp)
                    TextButton(onClick = { openAutostart(ctx) }) { Text("Aç", color = Pal.Gold, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        Text("Namazım · vakitler Diyanet yöntemiyle hesaplanır", Modifier.padding(24.dp), color = Pal.Muted, fontSize = 12.sp)
        }
    }

    if (dialog.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            containerColor = Pal.Card,
            title = {
                Text(
                    when (dialog) {
                        "prep" -> "Hazırlan bildirimi"
                        "nag" -> "Hatırlatma sıklığı"
                        "snooze" -> "Ertele süresi"
                        "goal" -> "Günlük okuma hedefi"
                        else -> "Günde kılınacak kaza"
                    },
                    color = Pal.Text,
                )
            },
            text = {
                Column {
                    Text(
                        when (dialog) {
                            "prep" -> "Vakitten kaç dakika önce bildirim gelsin? Birden fazlası için virgülle yaz (ör. 30, 15). Boş bırakırsan kapanır."
                            "nag" -> "Vakit girdikten sonra kaç dakikada bir \"kıldın mı?\" diye soralım?"
                            "snooze" -> "Hatırlatmayı kaç dakika erteleyelim?"
                            "goal" -> "Günde kaç sayfa okumayı hedefliyorsun?"
                            else -> "Günde kaç kaza namazı kılacaksın?"
                        },
                        color = Pal.Muted, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        draft, { draft = it }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pal.Gold, unfocusedBorderColor = Pal.Border),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (dialog) {
                        "prep" -> store.prepOffsets = draft.split(',', ' ').mapNotNull { it.trim().toIntOrNull() }
                            .filter { it in 1..180 }.toSet()
                        "nag" -> draft.trim().toIntOrNull()?.let { if (it in 1..180) store.nagEvery = it }
                        "snooze" -> draft.trim().toIntOrNull()?.let { if (it in 1..180) store.snoozeMin = it }
                        "goal" -> draft.trim().toIntOrNull()?.let { if (it in 1..300) store.readGoal = it }
                        "kaza" -> draft.trim().toIntOrNull()?.let { if (it in 1..50) store.kazaRate = it }
                    }
                    dialog = ""; changed()
                }) { Text("Kaydet", color = Pal.Gold) }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Vazgeç", color = Pal.Muted) } },
        )
    }
}
/** Ayarlarda hazır seçenek yerine kendi değerini yazman için tek satır. */
@Composable
private fun ValueRow(label: String, value: String, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable(onClick = onEdit).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Pal.Text, fontSize = 14.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, color = Pal.Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Text("Değiştir", color = Pal.Muted, fontSize = 12.sp)
    }
}




