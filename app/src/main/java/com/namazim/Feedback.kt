package com.namazim

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Ayarlar › Geri bildirim: the message goes to the owner's Telegram through a bot (Bot API sendMessage).
// Token and chat id come from local.properties (telegram.token / telegram.chat) at build time, never from git.
// shortcut: the token ships inside the APK and can be pulled out of it; use a bot that does nothing else,
// and move the call behind a tiny server (like the planned Diyanet proxy) before a public release.

/** Anti-spam, per phone: at most [FEEDBACK_PER_DAY] messages a day and one a minute.
 *  [log] is "yyyy-mm-dd|count|lastMs" (Store.feedbackLog). Returns why sending is refused, or null when it may go. */
const val FEEDBACK_PER_DAY = 5
fun feedbackBlock(log: String, now: Long, today: String): String? {
    val (day, count, last) = parseFeedbackLog(log)
    if (now - last < 60_000) return "Bir dakika sonra tekrar gönderebilirsin."
    if (day == today && count >= FEEDBACK_PER_DAY) return "Bugünlük gönderme sınırı doldu ($FEEDBACK_PER_DAY mesaj). Yarın tekrar yazabilirsin."
    return null
}
fun feedbackLogged(log: String, now: Long, today: String): String {
    val (day, count, _) = parseFeedbackLog(log)
    return "$today|${if (day == today) count + 1 else 1}|$now"
}
private fun parseFeedbackLog(log: String): Triple<String, Int, Long> {
    val f = log.split('|')
    return Triple(f.getOrElse(0) { "" }, f.getOrNull(1)?.toIntOrNull() ?: 0, f.getOrNull(2)?.toLongOrNull() ?: 0L)
}

val feedbackReady get() = BuildConfig.TG_TOKEN.isNotEmpty() && BuildConfig.TG_CHAT.isNotEmpty()

/** Blocking; call off the main thread. True when Telegram accepted the message. */
fun sendFeedback(text: String, contact: String): Boolean {
    val body = buildString {
        append("Namazım geri bildirim\n\n").append(text.trim())
        if (contact.isNotBlank()) append("\n\nİletişim: ").append(contact.trim())
        append("\n\n— ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        append(" · Android ").append(Build.VERSION.RELEASE).append(" · v").append(BuildConfig.VERSION_NAME)
    }
    val form = "chat_id=" + URLEncoder.encode(BuildConfig.TG_CHAT, "UTF-8") + "&text=" + URLEncoder.encode(body.take(4000), "UTF-8")
    return runCatching {
        val c = URL("https://api.telegram.org/bot${BuildConfig.TG_TOKEN}/sendMessage").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10_000; c.readTimeout = 10_000
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        c.outputStream.use { it.write(form.toByteArray()) }
        val ok = c.responseCode == 200
        c.disconnect()
        ok
    }.getOrDefault(false)
}

@Composable
fun FeedbackForm() {
    var text by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf(0) }   // 0 idle, 1 sending, 2 sent, 3 failed
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var blocked by remember { mutableStateOf<String?>(null) }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Pal.Gold, unfocusedBorderColor = Pal.Border, focusedTextColor = Pal.Text, unfocusedTextColor = Pal.Text,
        cursorColor = Pal.Gold, focusedLabelColor = Pal.Gold, unfocusedLabelColor = Pal.Muted,
    )
    if (state == 2) {
        Text("Mesajın ulaştı, teşekkürler. Allah razı olsun.", color = Pal.Gold, fontSize = Type.body)
        GhostButton("Yeni mesaj", Modifier.fillMaxWidth()) { text = ""; state = 0 }
        return
    }
    Text("Bir hata, bir istek ya da bir fikir: doğrudan geliştiriciye gider.", color = Pal.Muted, fontSize = Type.body2)
    OutlinedTextField(text, { text = it; if (state == 3) state = 0 }, Modifier.fillMaxWidth().heightIn(min = 140.dp),
        label = { Text("Mesajın") }, colors = colors)
    OutlinedTextField(contact, { contact = it }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text("Telegram kullanıcı adın ya da e-posta (isteğe bağlı)") }, colors = colors)
    Text("Mesajla birlikte yalnızca telefon modeli ve uygulama sürümü gider.", color = Pal.Muted, fontSize = Type.label)
    blocked?.let { Text(it, color = Pal.Warn, fontSize = Type.body2) }
    if (state == 3) Text("Gönderilemedi. İnternet bağlantını kontrol edip tekrar dene.", color = Pal.Warn, fontSize = Type.body2)
    if (!feedbackReady) Text("Geri bildirim bu sürümde henüz ayarlanmadı.", color = Pal.Warn, fontSize = Type.body2)
    GoldButton(if (state == 1) "Gönderiliyor…" else "Gönder", Modifier.fillMaxWidth(), Icons.AutoMirrored.Filled.Send) {
        if (state == 1 || text.isBlank() || !feedbackReady) return@GoldButton
        val now = System.currentTimeMillis()
        val today = java.time.LocalDate.now().toString()
        blocked = feedbackBlock(store.feedbackLog, now, today)
        if (blocked != null) return@GoldButton
        state = 1
        scope.launch {
            val ok = withContext(Dispatchers.IO) { sendFeedback(text, contact) }
            if (ok) store.feedbackLog = feedbackLogged(store.feedbackLog, now, today)
            state = if (ok) 2 else 3
        }
    }
}
