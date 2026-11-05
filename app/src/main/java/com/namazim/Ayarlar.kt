package com.namazim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
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
    Text(title, Modifier.padding(start = Space.xl, end = Space.xl, top = Space.xl, bottom = Space.s), color = Pal.Gold, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
    Column(Modifier.padding(horizontal = Space.xl).fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(Pal.Card).padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m), content = content)
}

@Composable
private fun Chips(options: List<Int>, isOn: (Int) -> Boolean, label: (Int) -> String, onClick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        options.forEach { n ->
            val on = isOn(n)
            Box(Modifier.heightIn(min = 36.dp).clip(CircleShape).background(if (on) Pal.Gold else Color.Transparent)
                .border(1.dp, if (on) Pal.Gold else Pal.Border, CircleShape).clickable { onClick(n) }.padding(horizontal = Space.m, vertical = Space.s),
                contentAlignment = Alignment.Center) {
                Text(label(n), color = if (on) Pal.Ground else Pal.Text, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), color = Pal.Text, fontSize = Type.body)
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Pal.Gold, checkedThumbColor = Pal.Ground,
            uncheckedTrackColor = Pal.Track, uncheckedThumbColor = Pal.Muted, uncheckedBorderColor = Pal.Border))
    }
}

@Composable
private fun StatusRow(text: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), color = Pal.Text, fontSize = Type.body)
        if (ok) Text("✓ Açık", color = Pal.Gold, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
        else TextButton(onClick = onClick) { Text(action, color = Pal.Gold, fontWeight = FontWeight.SemiBold) }
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

    // yedek: dosyayı kullanıcı seçtiği yere yazar (Drive, İndirilenler…); geri yüklemeden önce içeriği gösterilir
    var backupMsg by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Store.Backup?>(null) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupMsg = try {
            ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(store.exportBackup().toByteArray()) }
            store.markBackedUp(); "Yedek kaydedildi."
        } catch (e: Exception) { "Yedek yazılamadı: ${e.message}" }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            require(bytes.size < 5_000_000) { "Dosya çok büyük; bu bir Namazım yedeği değil." }
            pending = store.readBackup(String(bytes))
        } catch (e: IllegalArgumentException) { backupMsg = e.message ?: "Yedek okunamadı." }
        catch (e: Exception) { backupMsg = "Yedek okunamadı: ${e.message}" }
    }
    rememberNow() // re-check permission states after coming back from system settings

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Ayarlar", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Pal.Text) }
        })
        // key(v): chip'lerin seçimi her dokunuşta yeniden çizilsin (lambda'lar Compose'da hatırlanıyor)
        key(v) {
        Section("Görünüm") {
            // Which sky the Seher header draws: the live shader, layed photo plates, or a looping clip.
            Text("Gökyüzü", color = Pal.Text, fontSize = Type.body)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf("harem" to "Harem", "shader" to "Canlı", "photo" to "Fotoğraf", "video" to "Video").forEach { (id, label) ->
                    val on = store.skyMode == id
                    Box(Modifier.heightIn(min = 36.dp).clip(CircleShape).background(if (on) Pal.Gold else Color.Transparent)
                        .border(1.dp, if (on) Pal.Gold else Pal.Border, CircleShape).clickable { store.skyMode = id; v++ }
                        .padding(horizontal = Space.m, vertical = Space.s), contentAlignment = Alignment.Center) {
                        Text(label, color = if (on) Pal.Ground else Pal.Text, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
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

        Section("Bildirim sesi") {
            var soundOpen by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ses", color = Pal.Text, fontSize = Type.body)
                    Spacer(Modifier.height(2.dp))
                    Text(Reminders.soundLabel(store.notifSound), color = Pal.Gold, fontSize = Type.body, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { playTone(ctx, store.notifSound) }) { Text("Dinle", color = Pal.Muted, fontWeight = FontWeight.SemiBold) }
                Box {
                    OutlinedButton(
                        onClick = { soundOpen = true },
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Pal.Gold.copy(alpha = .5f)),
                    ) { Text("Değiştir", color = Pal.Gold, fontSize = Type.body2, fontWeight = FontWeight.SemiBold) }
                    DropdownMenu(expanded = soundOpen, onDismissRequest = { soundOpen = false }) {
                        Reminders.SOUNDS.forEach { (id, spec) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        spec.first,
                                        color = if (store.notifSound == id) Pal.Gold else Pal.Text,
                                        fontWeight = if (store.notifSound == id) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = Type.body,
                                    )
                                },
                                onClick = {
                                    store.notifSound = id
                                    Reminders.createChannels(ctx)
                                    soundOpen = false
                                    v++
                                    playTone(ctx, id)
                                },
                            )
                        }
                    }
                }
            }
        }



        Section("Okuma ve kaza") {
            ValueRow("Günlük okuma hedefi", "${store.readGoal} sayfa") { openDialog("goal", "${store.readGoal}") }
            ValueRow("Günde kılınacak kaza", "${store.kazaRate} kaza") { openDialog("kaza", "${store.kazaRate}") }
        }

        Section("Konum") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (locating) "Konum alınıyor…" else store.city.ifEmpty { "Konum belirlenmedi" }, Modifier.weight(1f), color = Pal.Text, fontSize = Type.body)
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
                    Text("Otomatik başlatma (Xiaomi)", Modifier.weight(1f), color = Pal.Text, fontSize = Type.body)
                    TextButton(onClick = { openAutostart(ctx) }) { Text("Aç", color = Pal.Gold, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        Section("Yedekleme") {
            v.let { }
            val last = store.lastBackup
            Text(
                if (last == 0L) "Henüz yedek alınmadı. Kayıtların yalnızca bu telefonda."
                else "Son yedek: ${java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("tr")))}",
                color = Pal.Muted, fontSize = Type.body2,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Yedek al", color = Pal.Text, fontSize = Type.body)
                    Text("Tek dosya: kaza, namazlar, okuma, zikir, ayarlar", color = Pal.Muted, fontSize = Type.label)
                }
                TextButton(onClick = { saveBackup.launch("namazim-yedek-${java.time.LocalDate.now()}.json") }) {
                    Text("Kaydet", color = Pal.Gold, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Yedekten yükle", color = Pal.Text, fontSize = Type.body)
                    Text("Önce ne geleceğini gösterir", color = Pal.Muted, fontSize = Type.label)
                }
                TextButton(onClick = { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                    Text("Seç", color = Pal.Gold, fontWeight = FontWeight.SemiBold)
                }
            }
            if (backupMsg.isNotEmpty()) Text(backupMsg, color = Pal.Gold, fontSize = Type.label)
        }

        Text("Namazım · vakitler Diyanet yöntemiyle hesaplanır", Modifier.padding(Space.xl), color = Pal.Muted, fontSize = Type.label)
        }
    }

    pending?.let { b ->
        val tr = java.util.Locale("tr")
        val at = if (b.at > 0) java.time.Instant.ofEpochMilli(b.at).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", tr)) else "tarihsiz"
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = Pal.Card,
            title = { Text("Yedek yüklensin mi?", color = Pal.Text) },
            text = {
                Text(
                    "Yedek: $at\n${b.prayedCount} işaretli namaz · ${b.kazaTotal} kaza" +
                        (if (b.city.isNotEmpty()) " · ${b.city}" else "") +
                        "\n\nBu telefondaki bütün kayıtların yerine geçecek.",
                    color = Pal.Muted, fontSize = Type.body2, lineHeight = 19.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    backupMsg = if (store.restore(b)) "Yedek yüklendi." else "Yedek yüklenemedi; kayıtların değişmedi."
                    pending = null; changed()
                }) { Text("Yükle", color = Pal.Gold) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Vazgeç", color = Pal.Muted) } },
        )
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
                        color = Pal.Muted, fontSize = Type.label, lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(Space.s))
                    // sayısal ayarlarda − / + ile hızlı ayar; yazmaya da devam edilebilir
                    val range = when (dialog) { "nag" -> Triple(5, 10, 180); "snooze" -> Triple(5, 5, 180); "goal" -> Triple(5, 5, 300); "kaza" -> Triple(1, 1, 50); else -> null }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        if (range != null) {
                            val (step, lo, _) = range
                            val cur = draft.trim().toIntOrNull() ?: lo
                            StepButton("−", cur > lo) { draft = (cur - step).coerceAtLeast(lo).toString() }
                        }
                        OutlinedTextField(
                            draft, { draft = it }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pal.Gold, unfocusedBorderColor = Pal.Border),
                        )
                        if (range != null) {
                            val (step, _, hi) = range
                            val cur = draft.trim().toIntOrNull() ?: hi
                            StepButton("+", cur < hi) { draft = (cur + step).coerceAtMost(hi).toString() }
                        }
                    }
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.s)).pressable(onClick = onEdit).padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Pal.Text, fontSize = Type.body, lineHeight = 18.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, color = Pal.Gold, fontSize = Type.body, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(Space.m))
        Text("Değiştir", color = Pal.Muted, fontSize = Type.label)
    }
}
@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(TouchMin).clip(CircleShape)
            .border(1.dp, if (enabled) Pal.Gold.copy(alpha = .45f) else Pal.Border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) Pal.Gold else Pal.Muted, fontSize = Type.h2) }
}

/** Seçili bildirim sesini bir kez çalar. */
fun playTone(ctx: Context, sound: String) {
    runCatching {
        val res = Reminders.soundRes(sound)
        if (res != 0) {
            val mp = android.media.MediaPlayer.create(ctx, res) ?: return@runCatching
            mp.setOnCompletionListener { it.release() }
            mp.start()
        } else {
            // "Sistem": telefonun varsayılan bildirim sesini bir kez çal
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            android.media.RingtoneManager.getRingtone(ctx, uri)?.play()
        }
    }
}





