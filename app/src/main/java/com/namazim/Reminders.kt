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

/** What a reminder is: before the time, the time entering (sound "done"), the faziletli time beginning ("tam vakti"), a nag. */
enum class Ses { HAZIR, GIRDI, FAZILET, ISRAR }

/** A moment to remind about [slot]; [prep] = before the time enters, otherwise "go pray". */
data class Reminder(val at: Long, val slot: Slot, val prep: Boolean, val kind: Ses = if (prep) Ses.HAZIR else Ses.ISRAR)

/** User-tunable cadence (Ayarlar). Minutes. */
data class ReminderCfg(val prep: List<Long> = listOf(30, 15, 5), val every: Long = 15, val burst: Boolean = true)

/**
 * "Hazırlan" at each prep offset; then "kıl" every [ReminderCfg.every] min, every 5 min in the last half hour if [ReminderCfg.burst].
 * The first "kıl" is the time entering. [fz] = start of the faziletli time (design A, bildirim-sesleri-secenekler.html):
 * at the start it replaces the entering one, later it is its own reminder and a nag closer than 5 min to it is dropped.
 */
fun reminders(slot: Slot, cfg: ReminderCfg = ReminderCfg(), fz: Long? = null): List<Reminder> {
    val list = cfg.prep.map { Reminder(slot.start - it * MIN, slot, true) }.toMutableList()
    var t = slot.start
    // Yatsı gece yarısını geçince seyrelir: 60 dakikada bir; son yarım saatte yine sık.
    val night = if (slot.vakit == Vakit.YATSI) slot.start + (slot.end - slot.start) / 2 else Long.MAX_VALUE
    val later = fz != null && fz > slot.start && fz < slot.nagEnd
    while (t < slot.nagEnd) {
        val kind = when { t != slot.start -> Ses.ISRAR; fz == slot.start -> Ses.FAZILET; else -> Ses.GIRDI }
        if (!(kind == Ses.ISRAR && later && kotlin.math.abs(t - fz!!) < 5 * MIN)) list += Reminder(t, slot, false, kind)
        val step = when {
            slot.nagEnd - t <= 30 * MIN && cfg.burst -> 5 * MIN
            t >= night -> 60 * MIN
            else -> cfg.every * MIN
        }
        t += step
    }
    if (later) list += Reminder(fz!!, slot, false, Ses.FAZILET)
    return list.sortedBy { it.at }
}

/** [reminders] with the user's settings, minus the ones a snooze skipped, plus one when the snooze ends. */
fun remindersFor(s: Store, slot: Slot): List<Reminder> {
    if (!s.tracked(slot)) return emptyList()   // a prayer from before tracking started (or in an özel gün) is neither nagged nor kaza
    if (s.ozelGunNow != null) return emptyList()   // nothing can be prayed while an özel gün lasts, even a prayer that became owed
    val fz = if (!s.faziletSes) null else s.dilimler(slot).indexOf(Dilim.FAZILET).takeIf { it >= 0 }?.let { dilimStart(slot, it) }
    val all = reminders(slot, s.reminderCfg(), fz)
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
        VakitWidgets.refreshAll(ctx) // the widgets follow the same state
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

    /**
     * Turns the launcher icon to the Cuma one and back (two activity-aliases, one enabled at a time). Called from the
     * background (alarms, boot, the app going to the background): the launcher may redraw or briefly drop the icon.
     */
    fun ikonuAyarla(ctx: Context) {
        val s = Store(ctx)
        if (!s.hasLocation) return
        val today = LocalDate.now()
        val cuma = SkyDebug.cumaIkon ?: (s.cumaIkon && cumaVakti(today, System.currentTimeMillis(), dayTimes(s.lat, s.lng, today).aksam))
        val pm = ctx.packageManager
        fun set(name: String, on: Boolean) {
            val c = android.content.ComponentName(ctx, "com.namazim.$name")
            val now = when (pm.getComponentEnabledSetting(c)) {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> name == "Giris"   // as in the manifest
                else -> false
            }
            if (now != on) pm.setComponentEnabledSetting(c,
                if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP)
        }
        // the new entry first, so there is never a moment without a launcher icon
        if (cuma) { set("GirisCuma", true); set("Giris", false) } else { set("Giris", true); set("GirisCuma", false) }
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
        ikonuAyarla(ctx)
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
        val s = Store(ctx)
        // the sound is fixed when a channel is made, so the chosen sound is part of the id; an unknown key (old list) falls back
        if (SOUNDS.none { it.first == s.prepSound }) s.prepSound = "coming"
        if (SOUNDS.none { it.first == s.nagSound }) s.nagSound = "ding"
        val want = listOf(
            channel(ctx, "hazir_${s.prepSound}", "Namaza hazırlık", NotificationManager.IMPORTANCE_DEFAULT, s.prepSound),
            channel(ctx, "kil_${s.nagSound}", "Kıldın mı?", NotificationManager.IMPORTANCE_HIGH, s.nagSound),
            // the two signature sounds never change, so the ear learns them
            channel(ctx, "ses_girdi", "Vakit girdi", NotificationManager.IMPORTANCE_HIGH, "done"),
            channel(ctx, "ses_fazilet", "En faziletli vakit", NotificationManager.IMPORTANCE_HIGH, "tam"),
        )
        want.forEach { nm.createNotificationChannel(it) }
        nm.notificationChannels.filter { c -> want.none { it.id == c.id } }.forEach { nm.deleteNotificationChannel(it.id) }
    }

    private fun channel(ctx: Context, id: String, name: String, importance: Int, sound: String) =
        NotificationChannel(id, name, importance).apply {
            when (sound) {
                "system" -> {}
                "sessiz" -> setSound(null, null)
                else -> setSound(soundUri(ctx, sound), Notification.AUDIO_ATTRIBUTES_DEFAULT)
            }
            enableVibration(true)
        }

    /** Bildirim sesleri (the wavs in the `bildirim` folder, as OGG): anahtar → (etiket, ham kaynak). done ve tam vakti imza seslerdir. */
    val SOUNDS = listOf(
        "ding" to ("Ding" to R.raw.notif_ding),
        "dririn" to ("Dririn" to R.raw.notif_dririn),
        "key" to ("Key" to R.raw.notif_key),
        "coming" to ("Coming" to R.raw.notif_coming),
        "sparkly" to ("Sparkly" to R.raw.notif_sparkly),
        "magic" to ("Magic" to R.raw.notif_magic),
        "done" to ("Done" to R.raw.notif_done),
        "tam" to ("Tam vakti" to R.raw.notif_tam),
        "system" to ("Sistem sesi" to 0),
        "sessiz" to ("Sessiz" to 0),
    )

    fun soundRes(sound: String): Int = SOUNDS.firstOrNull { it.first == sound }?.second?.second ?: 0

    fun soundLabel(sound: String): String = SOUNDS.firstOrNull { it.first == sound }?.second?.first ?: "Sistem sesi"

    fun soundUri(ctx: Context, sound: String): android.net.Uri? {
        val res = soundRes(sound)
        // by name, not number: a channel keeps its sound for good, and resource numbers move when the raw files change
        return if (res == 0) null else android.net.Uri.parse("android.resource://${ctx.packageName}/raw/${ctx.resources.getResourceEntryName(res)}")
    }

    fun channelFor(kind: String, ctx: Context) = Store(ctx).let { if (kind == "hazirlik") "hazir_${it.prepSound}" else "kil_${it.nagSound}" }


    private fun notify(ctx: Context, r: Reminder, now: Long) {
        val v = r.slot.vakit
        val ad = r.slot.ad(Store(ctx).kadin)
        val left = (r.slot.end - now) / MIN
        val (channel, title, text) = if (r.prep) {
            Triple(channelFor("hazirlik", ctx), "$ad vaktine ${(r.slot.start - now + MIN - 1) / MIN} dk", "Abdestini al, namaza hazırlan.")
        } else if (r.kind == Ses.FAZILET) {
            Triple("ses_fazilet", if (r.at == r.slot.start) "$ad vakti girdi · en faziletli vakit" else "$ad: en faziletli vakit",
                "Şimdi kılmanın tam vakti.")
        } else if (r.kind == Ses.GIRDI) {
            val o = nextOgut(Store(ctx))
            Triple(if (Store(ctx).girdiSes) "ses_girdi" else channelFor("israr", ctx), "$ad vakti girdi — kıldın mı?", "${o.text}\n— ${o.source}")
        } else if (r.slot.nagEnd - now <= 30 * MIN) {
            Triple(channelFor("israr", ctx), "$ad vakti çıkıyor!", "Son $left dakika. Kazaya bırakma, hemen kıl.")
        } else {
            val o = nextOgut(Store(ctx))
            Triple(channelFor("israr", ctx), "$ad vakti — kıldın mı?", "${o.text}\n— ${o.source}")
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
            .setStyle(Notification.BigTextStyle().bigText(text))   // the öğüt is longer than one line
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
    override fun onReceive(ctx: Context, intent: Intent) { Reminders.scheduleNext(ctx); Reminders.ikonuAyarla(ctx) }
}










