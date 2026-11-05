package com.namazim

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification
import java.time.LocalDate

private const val MIN = 60_000L

/** A moment to remind about [slot]; [prep] = before the time enters, otherwise "go pray". */
data class Reminder(val at: Long, val slot: Slot, val prep: Boolean)

/** User-tunable cadence (Ayarlar). Minutes. */
data class ReminderCfg(val prep: List<Long> = listOf(30, 15, 5), val every: Long = 15, val burst: Boolean = true)

/** "Hazırlan" at each prep offset; then "kıl" every [ReminderCfg.every] min, every 5 min in the last half hour if [ReminderCfg.burst]. */
fun reminders(slot: Slot, cfg: ReminderCfg = ReminderCfg()): List<Reminder> {
    val list = cfg.prep.map { Reminder(slot.start - it * MIN, slot, true) }.toMutableList()
    var t = slot.start
    // Yatsı gece yarısını geçince seyrelir: 60 dakikada bir; son yarım saatte yine sık.
    val night = if (slot.vakit == Vakit.YATSI) slot.start + (slot.end - slot.start) / 2 else Long.MAX_VALUE
    while (t < slot.nagEnd) {
        list += Reminder(t, slot, false)
        val step = when {
            slot.nagEnd - t <= 30 * MIN && cfg.burst -> 5 * MIN
            t >= night -> 60 * MIN
            else -> cfg.every * MIN
        }
        t += step
    }
    return list
}

/** [reminders] with the user's settings, minus the ones a snooze skipped, plus one when the snooze ends. */
fun remindersFor(s: Store, slot: Slot): List<Reminder> {
    if (!s.tracked(slot)) return emptyList()   // a prayer from before tracking started is neither nagged nor kaza
    val all = reminders(slot, s.reminderCfg())
    val until = s.snoozedUntil(slot.key)
    if (until == 0L) return all
    return all.filter { it.at >= until } + Reminder(until, slot, false)
}

object Reminders {
    private fun nearSlots(s: Store): List<Slot> {
        val today = LocalDate.now()
        return (-1L..1L).flatMap { slots(s.lat, s.lng, today.plusDays(it)) }
    }

    /** Re-arm the single alarm for the next pending reminder. Call after anything changes. */
    fun scheduleNext(ctx: Context) {
        val s = Store(ctx)
        NamazimWidget.refresh(ctx) // the live icon follows the same state
        if (!s.hasLocation) return
        s.settleKaza()
        val now = System.currentTimeMillis()
        val next = nearSlots(s).filterNot { s.isPrayed(it.key) }
            .flatMap { remindersFor(s, it) }.filter { it.at > now }.minByOrNull { it.at } ?: return

        val am = ctx.getSystemService(AlarmManager::class.java)
        val fire = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (am.canScheduleExactAlarms()) {
            // Alarm-clock alarms are the only kind Doze never delays or throttles; the cost is an alarm icon in the status bar.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(next.at, openApp(ctx)), fire)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, fire)
        }
    }

    /** Show every reminder that came due since the last run (alarms can coalesce or arrive late). */
    fun fire(ctx: Context) {
        val s = Store(ctx)
        if (!s.hasLocation) return
        val p = ctx.getSharedPreferences("namazim", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val since = p.getLong("lastFired", now - MIN)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // Time is over: the "go pray" nag is stale (the prayer is now kaza).
        nearSlots(s).filter { it.end in (since + 1)..now }.forEach { nm.cancel(it.vakit.ordinal + 1) }
        nearSlots(s).filterNot { s.isPrayed(it.key) }
            .mapNotNull { slot -> remindersFor(s, slot).filter { it.at in (since + 1)..now }.maxByOrNull { it.at } }
            .forEach { notify(ctx, it, now) }
        p.edit().putLong("lastFired", now).apply()
        scheduleNext(ctx)
    }

    fun markPrayed(ctx: Context, key: String, vakit: Vakit) {
        Store(ctx).markPrayed(key)
        ctx.getSystemService(NotificationManager::class.java).cancel(vakit.ordinal + 1)
        scheduleNext(ctx)
    }

    fun unmarkPrayed(ctx: Context, key: String) {
        Store(ctx).unmarkPrayed(key)
        scheduleNext(ctx)
    }

    fun snooze(ctx: Context, key: String, vakit: Vakit) {
        val s = Store(ctx)
        s.snooze(key, System.currentTimeMillis() + s.snoozeMin * MIN)
        ctx.getSystemService(NotificationManager::class.java).cancel(vakit.ordinal + 1)
        scheduleNext(ctx)
    }

    /** Shows a "kıldın mı?" notification right now, for the current (or next) prayer. */
    fun test(ctx: Context) {
        val s = Store(ctx)
        if (!s.hasLocation) return
        val now = System.currentTimeMillis()
        val slots = nearSlots(s)
        val slot = slots.firstOrNull { now in it.start until it.end } ?: slots.first { it.start > now }
        notify(ctx, Reminder(now, slot, false), now)
    }

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // Ses, kanal oluşturulurken sabitlenir; bu yüzden her ses için ayrı kanal kimliği kullanılır.
        val s = Store(ctx)
        // Ses listesi değişmiş olabilir: bilinmeyen anahtar kalırsa sisteme dön
        if (SOUNDS.none { it.first == s.notifSound }) s.notifSound = "system"
        val sound = s.notifSound
        val uri = soundUri(ctx, sound)
        val prep = NotificationChannel("hazirlik_$sound", "Namaza hazırlık", NotificationManager.IMPORTANCE_DEFAULT)
        val nag = NotificationChannel("israr_$sound", "Kıldın mı?", NotificationManager.IMPORTANCE_HIGH)
        if (uri != null) { prep.setSound(uri, null); nag.setSound(uri, null) }
        nag.enableVibration(true)
        nm.createNotificationChannel(prep)
        nm.createNotificationChannel(nag)
        // eski kimlikleri temizle
        (SOUNDS.map { it.first } + listOf("")).flatMap { listOf("hazirlik_$it", "israr_$it") }
            .filter { it != "hazirlik_$sound" && it != "israr_$sound" }
            .forEach { nm.deleteNotificationChannel(it) }
    }

    /** Seçili ses (sistem = null → kanal varsayılanı). */
    /** Seçilebilir bildirim sesleri: anahtar → (etiket, ham kaynak). */
    val SOUNDS = listOf(
        "system" to ("Sistem" to 0),
        "ezan" to ("Ezan · Hicaz" to R.raw.notif_ezan),
        "sala" to ("Sala · Rast" to R.raw.notif_sala),
        "ney" to ("Ney" to R.raw.notif_ney),
        "kudum" to ("Kudüm" to R.raw.notif_kudum),
        "def" to ("Def" to R.raw.notif_def),
        "ud" to ("Ud" to R.raw.notif_ud),
        "rebap" to ("Rebap" to R.raw.notif_rebap),
        "zikir" to ("Zikir" to R.raw.notif_zikir),
        "hilal" to ("Hilal çanağı" to R.raw.notif_hilal),
        "sabah" to ("Sabah · Saba" to R.raw.notif_sabah),
    )

    fun soundRes(sound: String): Int = SOUNDS.firstOrNull { it.first == sound }?.second?.second ?: 0

    fun soundLabel(sound: String): String = SOUNDS.firstOrNull { it.first == sound }?.second?.first ?: "Sistem"

    fun soundUri(ctx: Context, sound: String): android.net.Uri? {
        val res = soundRes(sound)
        return if (res == 0) null else android.net.Uri.parse("android.resource://${ctx.packageName}/$res")
    }

    fun channelFor(kind: String, ctx: Context) = "${kind}_${Store(ctx).notifSound}"

    private val nags = listOf(
        "Kalk kıl, vakit geçiyor!",
        "Hadi, sevaptan mahrum kalma.",
        "Dünya işi bekler, namaz beklemez.",
        "Birkaç dakikanı ayır, kıl gel.",
        "Seccade seni bekliyor. Hadi kalk.",
    )

    private fun notify(ctx: Context, r: Reminder, now: Long) {
        val v = r.slot.vakit
        val left = (r.slot.end - now) / MIN
        val (channel, title, text) = if (r.prep) {
            Triple(channelFor("hazirlik", ctx), "${v.ad} vaktine ${(r.slot.start - now + MIN - 1) / MIN} dk", "Abdestini al, namaza hazırlan.")
        } else if (r.slot.nagEnd - now <= 30 * MIN) {
            Triple(channelFor("israr", ctx), "${v.ad} vakti çıkıyor!", "Son $left dakika. Kazaya bırakma, hemen kıl.")
        } else {
            Triple(channelFor("israr", ctx), "${v.ad} vakti — kıldın mı?", nags[((now / MIN) % nags.size).toInt()])
        }
        val done = PendingIntent.getBroadcast(
            ctx, v.ordinal + 1,
            Intent(ctx, PrayedReceiver::class.java).putExtra("key", r.slot.key).putExtra("vakit", v.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val later = PendingIntent.getBroadcast(
            ctx, 10 + v.ordinal,
            Intent(ctx, SnoozeReceiver::class.java).putExtra("key", r.slot.key).putExtra("vakit", v.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(if (r.prep) openApp(ctx) else openReminder(ctx, r.slot))
            .setCategory(if (r.prep) Notification.CATEGORY_REMINDER else Notification.CATEGORY_ALARM)
            .setAutoCancel(false)
            .apply {
                if (!r.prep) {
                    addAction(Notification.Action.Builder(null, "Kıldım ✓", done).build())
                    addAction(Notification.Action.Builder(null, "${Store(ctx).snoozeMin} dk sonra", later).build())
                }
            }
            .build()
        // Same id per vakit: each nag replaces the previous one instead of piling up.
        ctx.getSystemService(NotificationManager::class.java).notify(v.ordinal + 1, n)
    }

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 100, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
    )

    /** Opens the full-screen "Kıldın mı?" page for this prayer. */
    private fun openReminder(ctx: Context, slot: Slot) = PendingIntent.getActivity(
        ctx, 200 + slot.vakit.ordinal,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_SLOT, slot.key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        Reminders.snooze(ctx, key, Vakit.valueOf(intent.getStringExtra("vakit") ?: return))
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Reminders.fire(ctx)
}

class PrayedReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        Reminders.markPrayed(ctx, key, Vakit.valueOf(intent.getStringExtra("vakit") ?: return))
    }
}

/** Boot, clock or timezone change, app update: the pending alarm is gone or wrong, so re-arm it. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Reminders.scheduleNext(ctx)
}










