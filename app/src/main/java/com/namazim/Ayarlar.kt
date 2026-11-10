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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
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

/** An Ayarlar tile: icon, topic, and where it stands right now; [warn] tints it peach when something needs fixing. */
@Composable
private fun SettingTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String, warn: Boolean,
                        modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.l)
    Column(
        modifier.heightIn(min = 124.dp).clip(shape).background(Pal.Card)
            .border(1.dp, if (warn) Pal.Warn.copy(alpha = .5f) else Color.White.copy(alpha = .06f), shape)
            .clickable(onClick = onClick).padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(Radius.s)).background((if (warn) Pal.Warn else Pal.Gold).copy(alpha = .15f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (warn) Pal.Warn else Pal.Gold, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(Space.xs))
        Text(title, color = Pal.Text, fontSize = Type.body, fontWeight = FontWeight.SemiBold)
        Text(summary, color = if (warn) Pal.Warn else Pal.Muted, fontSize = Type.label, lineHeight = 16.sp, maxLines = 3)
    }
}

/** The rows of one Ayarlar topic, in a sheet over the tiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Pal.Card) {
        Column(Modifier.padding(horizontal = Space.xl).navigationBarsPadding().padding(bottom = Space.xl).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Text(title, style = NumStyle, fontSize = Type.h2, color = Pal.Text)
            content()
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
    var confirmReset by remember { mutableStateOf(false) }
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

    // design A3 "Kutucuklar": six tiles that each say where things stand; a tap opens that topic's rows in a sheet
    var open by remember { mutableStateOf<String?>(null) }
    var ozelSheet by remember { mutableStateOf<String?>(null) }
    var ozelSil by remember { mutableStateOf<OzelGun?>(null) }
    val notifOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val batteryOk = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    val missing = listOf(notifOk, batteryOk).count { !it }
    val lastBackup = v.let { store.lastBackup }
    val lastText = if (lastBackup == 0L) "Henüz yedek alınmadı"
        else "Son yedek " + java.time.Instant.ofEpochMilli(lastBackup).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("tr")))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Ayarlar", store.city.ifEmpty { "Konum belirlenmedi" } + " · Diyanet vakitleri", rule = false, trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Pal.Text) }
        })
        key(v) {
            // design C (design/design-system/ayarlar-duzen-secenekler.html): what is about you on a card, the topics as tiles
            ProfilKart(store, locating) { open = "sen" }
            val prep = if (store.prepOffsets.isEmpty()) "Vakit öncesi kapalı" else store.prepOffsets.sortedDescending().joinToString("·") + " dk önce"
            val tiles = listOf(
                Triple("hatirlatma", Icons.Default.Notifications, "Bildirimler" to "$prep · her ${store.nagEvery} dk · ${Reminders.soundLabel(store.nagSound)}"),
                Triple("hedef", Icons.Default.Star, "Hedefler" to "Günde ${store.readGoal} sayfa · ${store.kazaRate} vakit kaza"),
                Triple("ibadet", Icons.Default.Favorite, "İbadet takibi" to
                    "Tesbihat ${if (store.askTesbihat) "açık" else "kapalı"} · nafile ${if (store.nafileTakip) "açık" else "kapalı"}"),
                Triple("izin", if (missing > 0) Icons.Default.Warning else Icons.Default.Lock,
                    "İzinler" to (if (missing > 0) "$missing eksik · düzelt" else "Hepsi açık")),
                Triple("veri", Icons.Default.Share, "Veri" to lastText),
                Triple("yardim", Icons.Default.Info, "Yardım" to "Tanıtım · geri bildirim"),
            )
            Column(Modifier.padding(start = Space.l, end = Space.l, top = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        pair.forEach { (id, icon, text) ->
                            SettingTile(icon, text.first, text.second, warn = id == "izin" && missing > 0, Modifier.weight(1f)) { open = id }
                        }
                    }
                }
            }
        }
        Text("Namazım · vakitler Diyanet yöntemiyle hesaplanır", Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.m), color = Pal.Muted,
            fontSize = Type.label, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }

    ozelSheet?.let { OzelGunSheet(it, store) { ozelSheet = null; v++ } }
    ozelSil?.let { p ->
        AlertDialog(
            onDismissRequest = { ozelSil = null },
            title = { Text("Dönemi sil") },
            text = { Text("${ozelTarih(p.start)} – ${ozelTarih(p.end ?: p.start)} silinsin mi? O günlerde kılınmamış namazlar kazaya yazılır.") },
            confirmButton = { TextButton(onClick = { store.setOzelGunler(store.ozelGunler - p); ozelSil = null; v++ }) { Text("Sil", color = Pal.Warn) } },
            dismissButton = { TextButton(onClick = { ozelSil = null }) { Text("Vazgeç", color = Pal.Muted) } },
            containerColor = Pal.Card, titleContentColor = Pal.Text, textContentColor = Pal.Muted,
        )
    }
    open?.let { id ->
        SettingSheet(
            when (id) { "sen" -> "Sen"; "hatirlatma" -> "Bildirimler"; "hedef" -> "Hedefler"; "ibadet" -> "İbadet takibi"; "izin" -> "Telefon izinleri"; "yardim" -> "Yardım"; else -> "Veri" },
            onClose = { open = null },
        ) {
            key(v) {
                when (id) {
                    "hatirlatma" -> {
                        // design A of design/design-system/bildirim-sesleri-secenekler.html: two fixed signature sounds, two picked ones
                        Text("İMZA SESLER", color = Pal.Gold, fontSize = Type.micro, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                        SesRow("Vakit girdi", "Namazım'ın sesi, değişmez", "done", store.girdiSes) { store.girdiSes = it; changed() }
                        SesRow("En faziletli vakit", "Mezhebine göre; vakit girişiyle aynı andaysa tek ses çalar", "tam", store.faziletSes) { store.faziletSes = it; changed() }
                        Text("HATIRLATMALAR", Modifier.padding(top = Space.s), color = Pal.Gold, fontSize = Type.micro, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                        ValueRow(
                            "Vakitten önce hazırlan bildirimi",
                            if (store.prepOffsets.isEmpty()) "kapalı" else store.prepOffsets.sortedDescending().joinToString(", ") { "$it dk" },
                        ) { openDialog("prep", store.prepOffsets.sortedDescending().joinToString(", ")) }
                        ValueRow("\"Kıldın mı?\" sıklığı", "${store.nagEvery} dk") { openDialog("nag", "${store.nagEvery}") }
                        SwitchRow("Vaktin son yarım saatinde 5 dakikada bir", store.nagBurst) { store.nagBurst = it; changed() }
                        ValueRow("Ertele süresi", "${store.snoozeMin} dk") { openDialog("snooze", "${store.snoozeMin}") }
                        GhostButton("Deneme bildirimi gönder", Modifier.fillMaxWidth(), color = Pal.Gold) { Reminders.test(ctx) }
                        Text("SESLER", Modifier.padding(top = Space.s), color = Pal.Gold, fontSize = Type.micro, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                        SesSecici("Hazırlan sesi", store.prepSound) { store.prepSound = it; Reminders.createChannels(ctx); changed() }
                        SesSecici("\"Kıldın mı?\" sesi", store.nagSound) { store.nagSound = it; Reminders.createChannels(ctx); changed() }
                        Text("CUMA", Modifier.padding(top = Space.s), color = Pal.Gold, fontSize = Type.micro, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                        SwitchRow("Cuma'da uygulama ikonu yeşile dönsün", store.cumaIkon) { store.cumaIkon = it; changed() }
                        Text("Perşembe akşamından Cuma akşamına kadar. Bazı telefonlarda ikon değişirken ana ekrandan bir an kaybolup geri gelir; sorun olursa kapat.",
                            color = Pal.Muted, fontSize = Type.label, lineHeight = 16.sp)
                    }
                    "hedef" -> {
                        ValueRow("Günlük okuma hedefi", "${store.readGoal} sayfa") { openDialog("goal", "${store.readGoal}") }
                        ValueRow("Günde kılınacak kaza", "${store.kazaRate} vakit") { openDialog("kaza", "${store.kazaRate}") }
                    }
                    "sen" -> {
                        ValueRow("Cinsiyet", if (store.kadin) "Kadın" else "Erkek") { store.kadin = !store.kadin; changed() }
                        if (store.kadin) {
                            SwitchRow("Özel gün düğmesi ana sayfada görünsün", !store.ozelGunGizli) { store.ozelGunGizli = !it; changed() }
                            val og = store.ozelGunNow
                            GhostButton(if (og != null) "Özel günü bitir" else "Özel gün başlat", Modifier.fillMaxWidth(), OzelRenk) { ozelSheet = if (og != null) "bitir" else "baslat" }
                            store.ozelGunler.filter { it.end != null }.takeLast(5).reversed().forEach { p ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${ozelTarih(p.start)} – ${ozelTarih(p.end!!)}${if (p.nifas) " · lohusalık" else ""}", Modifier.weight(1f), color = Pal.Text, fontSize = Type.body2)
                                    TextButton(onClick = { ozelSil = p }) { Text("sil", color = Pal.Muted) }
                                }
                            }
                            TextButton(onClick = { ozelSheet = "gecmis" }) { Text("Geçmiş bir dönem ekle", color = Pal.Gold, fontWeight = FontWeight.SemiBold) }
                            Text("Bu bilgi yalnızca telefonunda tutulur; geri bildirimle gönderilmez.", color = Pal.Muted, fontSize = Type.label)
                        }
                        ValueRow("Mezhep (faziletli vakit dilimleri)", if (store.hanefi) "Hanefî" else "Şâfiî") { store.hanefi = !store.hanefi; changed() }
                        Text(
                            if (store.hanefi) "Hanefî: sabah ortalık aydınlanınca, öğle kışın erken / yazın sıcak kırılınca, ikindi güneş sararmadan, akşam hemen, yatsı gecenin ilk üçte birinde faziletli; gece yarısından sonrası mekruh."
                            else "Şâfiî: her vakitte vaktin başı faziletli.",
                            color = Pal.Muted, fontSize = Type.body2,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Konum", color = Pal.Text, fontSize = Type.body)
                                Text(if (locating) "Konum alınıyor…" else store.city.ifEmpty { "Konum belirlenmedi" }, color = Pal.Gold, fontSize = Type.body, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(onClick = { askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                                Text("Güncelle", color = Pal.Gold, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    "ibadet" -> {
                        SwitchRow("Nafileleri takip et (işrak, kuşluk, evvâbin, teheccüd)", store.nafileTakip) { store.nafileTakip = it; changed() }
                        Text("Açıkken ana sayfadaki bu çiplere dokunarak kıldığını işaretlersin. Kazaya girmez, hatırlatma gelmez.", color = Pal.Muted, fontSize = Type.label)
                        Text("Namaz sonrası tesbihat", Modifier.padding(top = Space.s), color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.SemiBold)
                        SwitchRow("Kıldım'dan sonra tesbihatı sor", store.askTesbihat) { store.askTesbihat = it; changed() }
                        SwitchRow("Tesbihat günlük zikrime sayılsın", store.tesbihatCounts) { store.tesbihatCounts = it; changed() }
                        Text(
                            if (store.tesbihatCounts) "Açık: namazdan sonra çektiğin 33·33·33, günlük Sübhânallah / Elhamdülillah / Allâhu ekber hedefini doldurur."
                            else "Kapalı: tesbihat ayrı tutulur; 33·33·33 zikirleri Zikirhane ve Takip listesinden çıkar. Ekstra zikir istersen Takip › Kütüphaneden ekle.",
                            color = Pal.Muted, fontSize = Type.body2,
                        )
                    }
                    "izin" -> {
                        StatusRow("Bildirimler", notifOk, "İzin ver") { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        StatusRow("Pil kısıtlaması yok", batteryOk, "Kaldır") {
                            @SuppressLint("BatteryLife")
                            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                            ctx.startActivity(i)
                        }
                        if (Build.MANUFACTURER.equals("xiaomi", true)) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Otomatik başlatma (Xiaomi)", Modifier.weight(1f), color = Pal.Text, fontSize = Type.body)
                            TextButton(onClick = { openAutostart(ctx) }) { Text("Aç", color = Pal.Gold, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    "yardim" -> {
                        GhostButton("Tanıtımı tekrar göster", Modifier.fillMaxWidth(), color = Pal.Gold) { open = null; Tur.adim = 0; onBack() }
                        Text("Geri bildirim", Modifier.padding(top = Space.s), color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.SemiBold)
                        FeedbackForm()
                    }
                    else -> {
                        Text(if (lastBackup == 0L) "Henüz yedek alınmadı. Kayıtların yalnızca bu telefonda." else "$lastText.",
                            color = Pal.Muted, fontSize = Type.body2)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Kayıtları sıfırla", color = Pal.Text, fontSize = Type.body)
                                Text("Namaz, kaza, okuma ve zikir kayıtları silinir; ayarlar kalır", color = Pal.Muted, fontSize = Type.label)
                            }
                            TextButton(onClick = { confirmReset = true }) { Text("Sıfırla", color = Pal.Warn, fontWeight = FontWeight.SemiBold) }
                        }
                        if (backupMsg.isNotEmpty()) Text(backupMsg, color = Pal.Gold, fontSize = Type.label)
                    }
                }
            }
        }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        containerColor = Pal.Card,
        title = { Text("Kayıtlar sıfırlansın mı?", color = Pal.Text) },
        text = {
            Text("Kılınan namaz işaretleri, kaza sayıları, okuma ve zikir kayıtları silinir; takip şu andan başlar. " +
                "Konum, hatırlatma ayarları, hedefler ve zikir listen kalır. Geri almak için önce yedek alabilirsin.",
                color = Pal.Muted, fontSize = Type.body2, lineHeight = 19.sp)
        },
        confirmButton = {
            TextButton(onClick = {
                backupMsg = if (store.resetEntries()) "Kayıtlar sıfırlandı." else "Sıfırlanamadı; kayıtların değişmedi."
                confirmReset = false; changed()
            }) { Text("Sıfırla", color = Pal.Warn) }
        },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Vazgeç", color = Pal.Muted) } },
    )

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
/** The "Sen" card on top of Ayarlar: gender, mezhep and place at a glance; a tap opens their rows. */
@Composable
private fun ProfilKart(store: Store, locating: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.l)
    Row(
        Modifier.padding(start = Space.l, end = Space.l, top = Space.l).fillMaxWidth().clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1A2745), Pal.Card)))
            .border(1.dp, Pal.Gold.copy(alpha = .25f), shape).clickable(onClick = onClick).padding(Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Pal.Gold.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, tint = Pal.Gold, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f).padding(start = Space.m)) {
            Text("Sen", style = NumStyle, fontSize = Type.h2, lineHeight = 26.sp, color = Pal.Text)
            Text("Sana göre hesaplananlar", color = Pal.Muted, fontSize = Type.label)
            Row(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                listOf(if (store.kadin) "Kadın" else "Erkek", if (store.hanefi) "Hanefî" else "Şâfiî",
                    if (locating) "Konum…" else store.city.ifEmpty { "Konum yok" }).forEach {
                    Text(it, Modifier.border(1.dp, Pal.Gold.copy(alpha = .35f), CircleShape).padding(horizontal = Space.s, vertical = 2.dp),
                        color = Pal.Gold, fontSize = Type.micro, maxLines = 1)
                }
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Pal.Muted)
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

/** A signature sound: name, note, a play button and an on/off switch (the sound itself is fixed). */
@Composable
private fun SesRow(title: String, note: String, sound: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.Text, fontSize = Type.body)
            Text(note, color = Pal.Muted, fontSize = Type.label, lineHeight = 16.sp)
        }
        TextButton(onClick = { playTone(ctx, sound) }) {
            Icon(Icons.Default.PlayArrow, null, tint = Pal.Gold, modifier = Modifier.size(18.dp))
            Text(Reminders.soundLabel(sound), color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.SemiBold)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Pal.Gold, checkedThumbColor = Pal.Ground,
            uncheckedTrackColor = Pal.Track, uncheckedThumbColor = Pal.Muted, uncheckedBorderColor = Pal.Border))
    }
}

/** A picked sound: the row shows the choice; a tap opens the sounds as chips, a chip plays and picks. */
@Composable
private fun SesSecici(title: String, current: String, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    ValueRow(title, Reminders.soundLabel(current)) { open = !open }
    if (open) Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Reminders.SOUNDS.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                pair.forEach { (id, spec) ->
                    val on = id == current
                    Row(
                        Modifier.weight(1f).heightIn(min = TouchMin).clip(CircleShape)
                            .border(1.dp, if (on) Pal.Gold else Pal.Border, CircleShape)
                            .clickable { onPick(id); playTone(ctx, id) }.padding(horizontal = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (spec.second == 0) Icons.Default.Notifications else Icons.Default.PlayArrow, null,
                            tint = if (on) Pal.Gold else Pal.Muted, modifier = Modifier.size(16.dp))
                        Text(spec.first, Modifier.padding(start = Space.xs), color = if (on) Pal.Gold else Pal.Text, fontSize = Type.body2,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Seçili bildirim sesini bir kez çalar ("Sessiz" hiçbir şey çalmaz). */
fun playTone(ctx: Context, sound: String) {
    if (sound == "sessiz") return
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





