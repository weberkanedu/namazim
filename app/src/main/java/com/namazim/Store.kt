package com.namazim

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A book being read; [until] set = a dated plan that started at page [startPage] on [from]. The name is its id. */
data class Book(
    val name: String, val page: Int, val total: Int,
    val startPage: Int = 0, val from: LocalDate? = null, val until: LocalDate? = null,
)

/** One reading on the Okuma shelf: the Kur'an (id "quran") or a book. */
data class Reading(
    val id: String, val title: String, val page: Int, val total: Int,
    val startPage: Int, val from: LocalDate?, val until: LocalDate?, val quran: Boolean,
)

/** Bumped by Store on every write: screens read it so a change in Ayarlar shows up immediately. */
object StoreRev {
    var n by mutableIntStateOf(0)
}

/** All app data lives in one SharedPreferences file; it is small enough. */
class Store(context: Context) {
    private val appCtx = context.applicationContext
    private val p: SharedPreferences = context.getSharedPreferences("namazim", Context.MODE_PRIVATE)

    // --- Location
    val hasLocation get() = p.contains("lat")
    val lat get() = Double.fromBits(p.getLong("lat", 0))
    val lng get() = Double.fromBits(p.getLong("lng", 0))
    val city get() = p.getString("city", "") ?: ""
    /** The prayer day (imsak to imsak) the Özet rose and the daily logs count in. */
    fun prayerDay(now: Long = System.currentTimeMillis()): LocalDate =
        if (!hasLocation) LocalDate.now() else prayerDayOf(LocalDate.now(), now, dayTimes(lat, lng, LocalDate.now()).imsak)
    /** Next moment the prayer day turns over; without a location, check again in an hour. */
    fun nextImsak(now: Long = System.currentTimeMillis()): Long =
        if (!hasLocation) now + 3_600_000L
        else nextImsakAt(now, dayTimes(lat, lng, LocalDate.now()).imsak, dayTimes(lat, lng, LocalDate.now().plusDays(1)).imsak)
    fun setLocation(lat: Double, lng: Double, city: String) {
        val e = p.edit().putLong("lat", lat.toRawBits()).putLong("lng", lng.toRawBits()).putString("city", city)
        if (!p.contains("since")) e.putLong("since", System.currentTimeMillis())
        e.apply()
    }
    /** When the app started tracking; prayers that ended before this are neither prayed nor kaza. */
    val since: Long get() {
        if (!p.contains("since")) p.edit().putLong("since", System.currentTimeMillis()).apply()
        return p.getLong("since", 0)
    }
    val firstDay: LocalDate get() = java.time.Instant.ofEpochMilli(since).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    // --- İlk kurulum ve tanıtım (Kurulum.kt)
    /** True on a fresh install until setup ends; installs that had a location before setup existed skip it. */
    fun needsKurulum(): Boolean {
        if (p.contains("kurulum")) return !p.getBoolean("kurulum", true)
        if (hasLocation) return false
        p.edit().putBoolean("kurulum", false).apply()
        return true
    }
    fun kurulumBitti() = p.edit().putBoolean("kurulum", true).apply()
    var kurulumStep: Int
        get() = p.getInt("kurulumStep", 0)
        set(v) { p.edit().putInt("kurulumStep", v).apply() }
    var turDone: Boolean
        get() = p.getBoolean("tanitim", false)
        set(v) { p.edit().putBoolean("tanitim", v).apply() }
    /** Setup's "takibe nereden başlayalım": prayers that end before [at] are neither prayed nor kaza. */
    fun startTracking(at: Long) { StoreRev.n++; p.edit().putLong("since", at).putLong("settledUntil", at).apply() }
    /** Tracked = after first use and not in a woman's özel gün (no nag, no kaza). */
    fun tracked(s: Slot) = s.end > since && !exempt(s)

    // --- Kadınlar: özel günler (OzelGun.kt)
    var kadin: Boolean
        get() = p.getString("cinsiyet", "erkek") == "kadin"
        set(v) { StoreRev.n++; p.edit().putString("cinsiyet", if (v) "kadin" else "erkek").apply() }
    /** Hide the "Özel gün" button on home (it is then started from Ayarlar only). */
    var ozelGunGizli: Boolean
        get() = p.getBoolean("ozelGunGizli", true) // off unless she turns it on (owner, 2026-11-08)
        set(v) { StoreRev.n++; p.edit().putBoolean("ozelGunGizli", v).apply() }
    // shortcut: switching gender or mezhep later does not recount the kaza of past periods; recount if that matters
    val ozelGunler: List<OzelGun> get() = if (!kadin) emptyList() else ozelFromJson(p.getString("ozelGunler", null))
    val ozelGunNow: OzelGun? get() = ozelGunler.lastOrNull { it.end == null }
    fun exempt(s: Slot): Boolean {
        val ps = ozelGunler
        if (ps.isEmpty()) return false
        val day = slots(lat, lng, s.date)
        return exemptAny(s, day.getOrNull(day.indexOfFirst { it.key == s.key } + 1), ps, hanefi)
    }
    /** Every prayer of [d] falls in an özel gün (kaza streaks skip such days). */
    fun ozelGunDay(d: LocalDate) = ozelGunler.isNotEmpty() && slots(lat, lng, d).all { exempt(it) }
    /** Saves the periods and fixes the kaza already counted for ended prayers whose status the change flips
     *  (a period entered late, ended, edited or deleted). */
    fun setOzelGunler(new: List<OzelGun>, now: Long = System.currentTimeMillis()) {
        val old = ozelFromJson(p.getString("ozelGunler", null))
        val all = old + new
        if (all.isNotEmpty() && hasLocation) {
            val zone = java.time.ZoneId.systemDefault()
            val from = maxOf(all.minOf { it.start }, now - 400 * DAY_MS)
            var d = java.time.Instant.ofEpochMilli(from).atZone(zone).toLocalDate().minusDays(1)
            val last = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            while (!d.isAfter(last)) {
                val day = slots(lat, lng, d)
                day.forEachIndexed { i, s ->
                    if (s.end <= settledUntil && s.end > since && !isPrayed(s.key)) {
                        val was = exemptAny(s, day.getOrNull(i + 1), old, hanefi)
                        val isNow = exemptAny(s, day.getOrNull(i + 1), new, hanefi)
                        if (was != isNow) {
                            val n = if (isNow) -1 else 1
                            addKaza(s.vakit, n)
                            if (s.vakit == Vakit.YATSI) addKaza("VITIR", n)
                        }
                    }
                }
                d = d.plusDays(1)
            }
        }
        StoreRev.n++
        p.edit().putString("ozelGunler", ozelToJson(new.sortedBy { it.start })).apply()
        runCatching { VakitWidgets.refreshAll(appCtx) }
    }

    // --- Prayed log, keyed by Slot.key
    fun isPrayed(key: String) = key in prayed()
    fun prayed(): Set<String> = p.getStringSet("prayed", emptySet())!!
    /** Marks [key] prayed at [at]; without [at], a prayer marked before and undone gets its first time back
     *  (owner, 2026-11-11: an undo and a re-mark made a prayer prayed on time look late), else now. */
    fun markPrayed(key: String, at: Long? = null) {
        StoreRev.n++
        val was = isPrayed(key)
        val s = if (was) null else slotOf(key)
        val now = System.currentTimeMillis()
        val ended = s != null && s.end <= now
        val first = prayedAt(key)
        // marked after its vakit ended and no time given: kept just inside the vakit and flagged "saat bilinmiyor" (never on time)
        val t = at ?: first ?: if (ended) s!!.end - 60_000L else now
        val writtenOff = s != null && kazaSayildi(s)
        val e = p.edit().putStringSet("prayed", prayed() + key).putLong("prayedAt_$key", t)
        if (at != null) e.remove("saatsiz_$key") else if (first == null && ended) e.putBoolean("saatsiz_$key", true)
        // the tesbihat the undo put aside comes back with it
        p.getString("tesbihatEski_$key", null)?.let { e.putString("tesbihat_$key", it).remove("tesbihatEski_$key") }
        e.apply()
        // a prayer already written off as kaza and marked afterwards (phone off, forgot): the kaza comes off
        if (writtenOff) kazaDuzelt(s!!, -1)
        runCatching { VakitWidgets.refreshAll(appCtx) }
    }
    /** Undo a mis-tap: the time stays (for a re-mark), the tesbihat is put aside; a vakit already over goes back to kaza. */
    fun unmarkPrayed(key: String) {
        StoreRev.n++
        val s = if (isPrayed(key)) slotOf(key) else null
        val e = p.edit().putStringSet("prayed", prayed() - key)
        tesbihat(key)?.let { e.putString("tesbihatEski_$key", it).remove("tesbihat_$key") }
        e.apply()
        if (s != null && kazaSayildi(s)) kazaDuzelt(s, +1)
        runCatching { VakitWidgets.refreshAll(appCtx) }
    }
    /** "yyyy-mm-dd|VAKIT" → its slot. */
    private fun slotOf(key: String): Slot? = runCatching {
        val (d, v) = key.split('|')
        slots(lat, lng, LocalDate.parse(d))[Vakit.valueOf(v).ordinal]
    }.getOrNull()
    /** settleKaza has already passed this vakit: unprayed, it is counted as kaza. */
    private fun kazaSayildi(s: Slot) = s.end <= maxOf(settledUntil, since) && s.end > since && !exempt(s)
    private fun kazaDuzelt(s: Slot, n: Int) { addKaza(s.vakit, n); if (s.vakit == Vakit.YATSI) addKaza("VITIR", n) }
    /** Marked after its vakit, without a time: counted as prayed, not on time. */
    fun saatsiz(key: String) = p.getBoolean("saatsiz_$key", false)
    /** When home was last opened, for the "while you were away" card (Gecmis.kt). */
    var sonAcilis: Long
        get() = p.getLong("sonAcilis", 0L)
        set(v) { p.edit().putLong("sonAcilis", v).apply() }
    /** Prayers written off as kaza since [sonAcilis], in the last seven prayer days: the return card asks about them. */
    fun donusKazalari(now: Long = System.currentTimeMillis()): List<Slot> {
        val from = sonAcilis
        if (from == 0L || !hasLocation) return emptyList()
        val today = prayerDay(now)
        return (6L downTo 0L).flatMap { slots(lat, lng, today.minusDays(it)) }
            .filter { it.end in (from + 1)..now && !isPrayed(it.key) && tracked(it) }
    }
    /** When the prayer was marked (for the "12:41'de kılındı" line); also kept after an undo, so check [isPrayed]. */
    fun prayedAt(key: String): Long? = p.getLong("prayedAt_$key", 0L).takeIf { it > 0 }

    // --- Kaza, keyed by Vakit name or "VITIR"
    fun kaza(key: String) = p.getInt("kaza_$key", 0)
    fun kaza(v: Vakit) = kaza(v.name)
    fun addKaza(key: String, n: Int) { StoreRev.n++; p.edit().putInt("kaza_$key", (kaza(key) + n).coerceAtLeast(0)).apply() }
    fun addKaza(v: Vakit, n: Int) = addKaza(v.name, n)
    /** A kaza prayer made up: one less owed, one more on this month's tally. */
    fun payKaza(key: String) {
        if (kaza(key) == 0) return
        addKaza(key, -1)
        p.edit().putInt(paidKey(), kazaPaidThisMonth() + 1).putInt("kazaDone_$key", kazaDone(key) + 1).apply()
        logKaza(1) // günlük grafik için: bugün kaç kaza kılındı
    }

    /** Gün → o gün kılınan kaza sayısı (türlere göre grafikte kullanılır). */
    fun kazaLog(): Map<String, Int> {
        val o = JSONObject(p.getString("kazaLog", "{}")!!)
        return o.keys().asSequence().associateWith { o.getInt(it) }
    }

    fun logKaza(n: Int) {
        val o = JSONObject(p.getString("kazaLog", "{}")!!)
        val today = prayerDay().toString()
        o.put(today, (o.optInt(today) + n).coerceAtLeast(0))
        p.edit().putString("kazaLog", o.toString()).apply()
        StoreRev.n++
    }
    /** Undo a mis-tapped "Kıldım": one more owed, one less on this month's tally. */
    fun unpayKaza(key: String) {
        addKaza(key, 1)
        StoreRev.n++
        logKaza(-1)
        p.edit().putInt(paidKey(), (kazaPaidThisMonth() - 1).coerceAtLeast(0))
            .putInt("kazaDone_$key", (kazaDone(key) - 1).coerceAtLeast(0)).apply()
    }
    /** Kaza of this vakit made up since this counter existed (Nov 2026); drives "gün gün eda". */
    fun kazaDone(key: String) = p.getInt("kazaDone_$key", 0)
    fun kazaPaidThisMonth() = p.getInt(paidKey(), 0)
    private fun paidKey() = "kazaPaid_" + LocalDate.now().toString().take(7)
    var kazaRate: Int
        get() = p.getInt("kazaRate", 2)
        set(v) { StoreRev.n++; p.edit().putInt("kazaRate", v).apply() }

    // --- Settings
    /** Minutes before a prayer time for the "hazırlan" notifications. */
    var prepOffsets: Set<Int>
        get() = p.getStringSet("prepOffsets", null)?.map { it.toInt() }?.toSet() ?: setOf(30, 15, 5)
        set(v) { StoreRev.n++; p.edit().putStringSet("prepOffsets", v.map { it.toString() }.toSet()).apply() }
    // sounds (Reminders.SOUNDS): the two the user picks, and the two signature sounds that can only be switched off
    var prepSound: String
        get() = p.getString("prepSound", "coming")!!
        set(v) { StoreRev.n++; p.edit().putString("prepSound", v).apply() }
    var nagSound: String
        get() = p.getString("nagSound", "ding")!!
        set(v) { StoreRev.n++; p.edit().putString("nagSound", v).apply() }
    var girdiSes: Boolean
        get() = p.getBoolean("girdiSes", true)
        set(v) { StoreRev.n++; p.edit().putBoolean("girdiSes", v).apply() }
    /** The launcher icon follows the vakit, and is the Cuma one from Thursday's akşam to Friday's akşam (pref key kept from the Cuma-only switch). */
    var ikonDegissin: Boolean
        get() = p.getBoolean("cumaIkon", true)
        set(v) { StoreRev.n++; p.edit().putBoolean("cumaIkon", v).apply() }
    var faziletSes: Boolean
        get() = p.getBoolean("faziletSes", true)
        set(v) { StoreRev.n++; p.edit().putBoolean("faziletSes", v).apply() }
    var nagEvery: Int
        get() = p.getInt("nagEvery", 15)
        set(v) { StoreRev.n++; p.edit().putInt("nagEvery", v).apply() }
    /** Nag every 5 minutes in the last half hour of a prayer time. */
    var nagBurst: Boolean
        get() = p.getBoolean("nagBurst", true)
        set(v) { StoreRev.n++; p.edit().putBoolean("nagBurst", v).apply() }
    /** Which öğüt (ayet/hadis) the next reminder shows; no StoreRev bump, nothing on screen depends on it. */
    /** Defter's last section (0 Özet, 1 İstikrar, 2 Kaza) and İstikrar period (0 hafta … 3 tümü), so it opens where it was left. */
    var defterSection: Int
        get() = p.getInt("defterSection", 0)
        set(v) { p.edit().putInt("defterSection", v).apply() }
    var defterPeriod: Int
        get() = p.getInt("defterPeriod", 2)
        set(v) { p.edit().putInt("defterPeriod", v).apply() }
    /** The Friday whose Cuma ayet card was already shown on home (it shows once, after the Cuma vakti enters). */
    var cumaAyetGun: String
        get() = p.getString("cumaAyetGun", "") ?: ""
        set(v) { p.edit().putString("cumaAyetGun", v).apply() }
    var ogutIndex: Int
        get() = p.getInt("ogutIndex", 0)
        set(v) { p.edit().putInt("ogutIndex", v).apply() }

    /** Feedback sends today (date "yyyy-mm-dd|count") and the last send time, for the anti-spam limit. */
    var feedbackLog: String
        get() = p.getString("feedbackLog", "") ?: ""
        set(v) { p.edit().putString("feedbackLog", v).apply() }

    var snoozeMin: Int
        get() = p.getInt("snoozeMin", 15)
        set(v) { StoreRev.n++; p.edit().putInt("snoozeMin", v).apply() }
    fun reminderCfg() = ReminderCfg(prepOffsets.map { it.toLong() }.sortedDescending(), nagEvery.toLong(), nagBurst)

    var showExtras: Boolean
        get() = p.getBoolean("showExtras", false)
        set(v) { StoreRev.n++; p.edit().putBoolean("showExtras", v).apply() }
    var readGoal: Int
        get() = p.getInt("readGoal", 10)
        set(v) { StoreRev.n++; p.edit().putInt("readGoal", v).apply() }

    // --- Snooze: no reminders for a prayer until this moment
    fun snoozedUntil(key: String) = p.getLong("snooze_$key", 0)
    fun snooze(key: String, until: Long) { StoreRev.n++; p.edit().putLong("snooze_$key", until).apply() }

    /** Prayers whose time ended before this moment have already been counted as prayed or kaza. */
    var settledUntil: Long
        get() = p.getLong("settledUntil", 0)
        set(v) { StoreRev.n++; p.edit().putLong("settledUntil", v).apply() }

    /** Any slot that ended unprayed since the last check becomes kaza. */
    fun settleKaza(now: Long = System.currentTimeMillis()) {
        if (!hasLocation) return
        if (settledUntil == 0L) { settledUntil = now; return } // nothing before first use counts
        // settledUntil'den bu yana kaçan vakitler: 30 güne kadar geriye bak (telefon kapalı kalmış olabilir)
        var day = LocalDate.now().minusDays(30)
        while (!day.isAfter(LocalDate.now())) {
            val ds = slots(lat, lng, day)
            for ((i, s) in ds.withIndex()) {
                if (s.end in (maxOf(settledUntil, since) + 1)..now && !isPrayed(s.key) && !exemptAny(s, ds.getOrNull(i + 1), ozelGunler, hanefi)) {
                    addKaza(s.vakit, 1)
                    if (s.vakit == Vakit.YATSI) addKaza("VITIR", 1) // vitir is owed along with a missed yatsı
                }
            }
            day = day.plusDays(1)
        }
        settledUntil = now
    }

    // --- Reading
    var quranPage: Int
        get() = p.getInt("quranPage", 0)
        set(v) { StoreRev.n++; p.edit().putInt("quranPage", v).apply() }
    var hatim: Int
        get() = p.getInt("hatim", 0)
        set(v) { StoreRev.n++; p.edit().putInt("hatim", v).apply() }

    fun books(): List<Book> {
        val a = JSONArray(p.getString("books", "[]"))
        return (0 until a.length()).map { a.getJSONObject(it).run {
            Book(getString("name"), getInt("page"), getInt("total"), optInt("startPage"),
                optString("from").takeIf { it.isNotEmpty() }?.let(LocalDate::parse), optString("until").takeIf { it.isNotEmpty() }?.let(LocalDate::parse))
        } }
    }
    fun saveBooks(list: List<Book>) {
        StoreRev.n++
        val a = JSONArray()
        list.forEach {
            val o = JSONObject().put("name", it.name).put("page", it.page).put("total", it.total).put("startPage", it.startPage)
            it.from?.let { d -> o.put("from", d.toString()) }; it.until?.let { d -> o.put("until", d.toString()) }
            a.put(o)
        }
        p.edit().putString("books", a.toString()).apply()
    }

    // Kur'an plan: absent = no date, the fixed daily goal (readGoal) applies
    val quranPlanName get() = p.getString("quranPlanName", "") ?: ""
    fun setQuranPlan(name: String, startPage: Int, from: LocalDate?, until: LocalDate?) {
        StoreRev.n++
        val e = p.edit().putString("quranPlanName", name).putInt("quranPlanStart", startPage)
        if (from != null && until != null) e.putString("quranPlanFrom", from.toString()).putString("quranPlanUntil", until.toString())
        else e.remove("quranPlanFrom").remove("quranPlanUntil")
        e.apply()
    }

    /** The shelf: the Kur'an first, then the books in the order they were added. */
    fun readings(): List<Reading> {
        val from = p.getString("quranPlanFrom", null)?.let(LocalDate::parse)
        val until = p.getString("quranPlanUntil", null)?.let(LocalDate::parse)
        val quran = Reading("quran", quranPlanName.ifEmpty { "Kur'an-ı Kerîm" }, quranPage, QURAN_PAGES,
            p.getInt("quranPlanStart", 0), from, until, true)
        return listOf(quran) + books().map { Reading(it.name, it.name, it.page, it.total, it.startPage, it.from, it.until, false) }
    }

    /** Pages read per reading per day: "2026-11-01" -> (reading id -> count). */
    fun readLogBy(day: LocalDate): Map<String, Int> {
        val o = JSONObject(p.getString("readLogBy", "{}")!!).optJSONObject(day.toString())
        val by = o?.keys()?.asSequence()?.associateWith { o.getInt(it) }.orEmpty()
        // pages logged before per-reading logs existed (Nov 2026) came almost all from the Kur'an counter: count them there
        val loose = (readLog()[day.toString()] ?: 0) - by.values.sum()
        return if (loose > 0) by + ("quran" to (by["quran"] ?: 0) + loose) else by
    }
    /** Logs [n] pages (may be negative) both to the day's total and to the reading [id]. */
    fun logPagesFor(id: String, n: Int) {
        logPages(n)
        val all = JSONObject(p.getString("readLogBy", "{}")!!)
        val today = prayerDay().toString()
        val o = all.optJSONObject(today) ?: JSONObject()
        o.put(id, (o.optInt(id) + n).coerceAtLeast(0))
        p.edit().putString("readLogBy", all.put(today, o).toString()).apply()
    }

    /** The user's zikir list; until it is first saved it is the tesbihat. */
    var zikirs: List<Zikir>
        get() {
            val raw = p.getString("zikirs", null) ?: return DEFAULT_ZIKIRS
            val a = JSONArray(raw)
            return (0 until a.length()).map { a.getJSONObject(it).run {
                Zikir(getString("name"), getInt("target"), optString("ar").ifEmpty { null }, optString("mean").ifEmpty { null }, optString("okunus").ifEmpty { null })
            } }
        }
        set(v) {
            StoreRev.n++
            val a = JSONArray(); v.forEach { a.put(JSONObject().put("name", it.name).put("target", it.target).putOpt("ar", it.ar).putOpt("mean", it.mean).putOpt("okunus", it.okunus)) }
            p.edit().putString("zikirs", a.toString()).apply()
        }

    /** Counts on the given prayer day (imsak to imsak): zikir name -> count. */
    fun zikirCounts(day: LocalDate = prayerDay()): Map<String, Int> {
        val o = JSONObject(p.getString("zikirLog", "{}")!!).optJSONObject(day.toString())
        val raw = o?.keys()?.asSequence()?.associateWith { o.getInt(it) } ?: emptyMap()
        if (!tesbihatCounts) return raw
        // "Tesbihat günlük zikrime sayılsın": added on read, so flipping the setting changes Takip and the rose at once
        val add = tesbihatOn(day)
        return raw.toMutableMap().apply { DEFAULT_ZIKIRS.forEachIndexed { i, z -> if (add[i] > 0) merge(z.name, add[i], Int::plus) } }
    }
    /** What the day's namaz sonrası tesbihat said: marked ones count in full, the one under way in Zikirhane as far as it got. */
    fun tesbihatOn(day: LocalDate): List<Int> {
        val marked = p.all.keys.count { it.startsWith("tesbihat_$day|") }
        val sum = MutableList(DEFAULT_ZIKIRS.size) { marked * DEFAULT_ZIKIRS[it].target }
        val f = (p.getString("tesbihProgress", "") ?: "").split('|')
        if (f.size == 5 && f[0] == day.toString() && tesbihat("${f[0]}|${f[1]}") == null)
            f.drop(2).forEachIndexed { i, s -> sum[i] += s.toIntOrNull() ?: 0 }
        return sum
    }
    /** Before Nov 5 2026 (one build) the tesbihat was written into zikirLog itself; take it out once, it is added on read now. */
    fun migrateTesbihatLog() {
        if (p.getBoolean("tesbihLogV2", false)) return
        p.all.keys.filter { it.startsWith("tesbihat_") }.forEach { k ->
            val day = k.removePrefix("tesbihat_").substringBefore('|')
            DEFAULT_ZIKIRS.forEach { addZikir(it.name, -it.target, day) }
        }
        val f = (p.getString("tesbihProgress", "") ?: "").split('|')
        if (f.size == 5 && tesbihat("${f[0]}|${f[1]}") == null)
            f.drop(2).forEachIndexed { i, s -> addZikir(DEFAULT_ZIKIRS[i].name, -(s.toIntOrNull() ?: 0), f[0]) }
        p.edit().putBoolean("tesbihLogV2", true).apply()
    }
    fun addZikir(name: String, n: Int, day: String = prayerDay().toString()) {
        StoreRev.n++
        val all = JSONObject(p.getString("zikirLog", "{}")!!)
        val today = day
        val o = all.optJSONObject(today) ?: JSONObject()
        o.put(name, (o.optInt(name) + n).coerceAtLeast(0))
        p.edit().putString("zikirLog", all.put(today, o).toString()).apply()
    }

    // --- Namaz sonrası tesbihat (33 Sübhânallah · 33 Elhamdülillah · 33 Allâhu ekber after each farz)
    /** "el" = done by hand or with the cemaat, "app" = counted in Zikirhane, null = not yet. Key = slot key. */
    fun tesbihat(key: String): String? = p.getString("tesbihat_$key", null)
    fun setTesbihat(key: String, kind: String?) {
        val was = tesbihat(key)
        if (was == kind) return
        StoreRev.n++   // the zikir log is not touched: zikirCounts adds the tesbihat on read
        p.edit().apply { if (kind == null) remove("tesbihat_$key") else putString("tesbihat_$key", kind) }.apply()
        if (kind == null) setTesbihProgress(key, listOf(0, 0, 0))
    }
    /** Mezhep for the faziletli slices of a vakit (`Fazilet.kt`): Hanefî (default) or Şâfiî. */
    var hanefi: Boolean
        get() = p.getString("mezhep", "hanefi") != "safii"
        set(v) { StoreRev.n++; p.edit().putString("mezhep", if (v) "hanefi" else "safii").apply() }
    /** The seven slices of [s] for the chosen mezhep; the sarı kerahat is 45 min before akşam, as in `extras()`. */
    fun dilimler(s: Slot): List<Dilim> {
        val d = dayTimes(lat, lng, s.date)
        return dilimler(s, hanefi, isSummer(s.date), d.aksam, if (s.vakit == Vakit.IKINDI) d.aksam - 45 * 60_000L else null)
    }
    /** Prayed in a faziletli slice ("vaktinde"). */
    fun onTime(s: Slot): Boolean = isPrayed(s.key) && inFazilet(s, dilimler(s), prayedAt(s.key))

    // --- Nafile namazlar (işrak, kuşluk, evvâbin, teheccüd): marked from their chips on home, never kaza, never nagged
    var nafileTakip: Boolean
        get() = p.getBoolean("nafileTakip", false)
        set(v) { StoreRev.n++; p.edit().putBoolean("nafileTakip", v).apply() }
    /** [key] = "prayer day|name", e.g. "2026-11-08|Teheccüd". */
    fun nafileKeys(): Set<String> = p.getStringSet("nafile", emptySet()) ?: emptySet()
    fun nafileKilindi(key: String) = key in nafileKeys()
    fun toggleNafile(key: String) {
        val s = (p.getStringSet("nafile", emptySet()) ?: emptySet()).toMutableSet()
        if (!s.remove(key)) s.add(key)
        StoreRev.n++
        p.edit().putStringSet("nafile", s).apply()
    }

    /** Ask for the tesbihat after Kıldım (card on home, icon on the row). */
    var askTesbihat: Boolean
        get() = p.getBoolean("askTesbihat", true)
        set(v) { StoreRev.n++; p.edit().putBoolean("askTesbihat", v).apply() }
    /** The tesbihat counts toward the daily zikir targets (on), or the daily list comes on top of it (off). */
    var tesbihatCounts: Boolean
        get() = p.getBoolean("tesbihatCounts", true)
        set(v) { StoreRev.n++; p.edit().putBoolean("tesbihatCounts", v).apply() }
    /** Counts of the tesbihat being said in Zikirhane, kept so leaving the page does not lose them. One at a time. */
    fun tesbihProgress(key: String): List<Int> {
        val f = (p.getString("tesbihProgress", "") ?: "").split('|')
        // stored as "yyyy-mm-dd|VAKIT|a|b|c" (the slot key itself holds one '|')
        return if (f.size == 5 && "${f[0]}|${f[1]}" == key) f.drop(2).map { it.toIntOrNull() ?: 0 } else listOf(0, 0, 0)
    }
    fun setTesbihProgress(key: String, c: List<Int>) = p.edit().putString("tesbihProgress", "$key|${c.joinToString("|")}").apply()

    // --- Backup: the whole preferences file as one JSON document (kaza, prayed, reading, zikir, settings)
    /** When a backup file was last written; 0 = never. */
    val lastBackup get() = p.getLong("lastBackup", 0)
    /** The home band ("yedek al") stays away until this moment after "Sonra". */
    var backupNagUntil: Long
        get() = p.getLong("backupNagUntil", 0)
        set(v) { StoreRev.n++; p.edit().putLong("backupNagUntil", v).apply() }
    /** Show the home band when nothing has been backed up for 30 days (counted from first use). */
    fun backupDue(now: Long = System.currentTimeMillis()) =
        hasLocation && now > backupNagUntil && now - maxOf(lastBackup, since) > 30L * 86_400_000

    fun exportBackup(now: Long = System.currentTimeMillis()): String {
        val prefs = JSONObject()
        p.all.forEach { (k, v) ->
            val e = when (v) {
                is String -> JSONObject().put("t", "s").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> null
            }
            if (e != null) prefs.put(k, e)
        }
        return JSONObject().put("app", "namazim").put("v", 1).put("at", now).put("prefs", prefs).toString(1)
    }
    /**
     * Wipes what the user entered (prayers marked, kaza, reading, zikir counts) and starts tracking again from now.
     * Location, every setting, the zikir list and the reminder chain's state stay. Tracking restarts at [startAt].
     */
    fun resetEntries(startAt: Long = System.currentTimeMillis()): Boolean {
        val keep = setOf("lat", "lng", "city", "nagEvery", "nagBurst", "prepSound", "nagSound", "girdiSes", "faziletSes", "cumaIkon", "prepOffsets", "readGoal",
            "kazaRate", "showExtras", "snoozeMin", "zikirs", "lastBackup", "backupNagUntil", "lastFired",
            "askTesbihat", "tesbihatCounts", "ogutIndex", "tesbihLogV2", "mezhep", "cinsiyet", "ozelGunGizli", "nafileTakip", "kurulum", "tanitim")
        val e = p.edit()
        p.all.keys.filter { it !in keep }.forEach { e.remove(it) }
        val ok = e.putLong("since", startAt).putLong("settledUntil", startAt).commit()
        StoreRev.n++
        return ok
    }

    fun markBackedUp(now: Long = System.currentTimeMillis()) { StoreRev.n++; p.edit().putLong("lastBackup", now).apply() }

    /** A parsed backup file, checked before anything is written. */
    class Backup(val at: Long, val values: Map<String, Any>) {
        val prayedCount get() = (values["prayed"] as? Set<*>)?.size ?: 0
        val kazaTotal get() = values.filterKeys { it.startsWith("kaza_") }.values.sumOf { (it as? Int) ?: 0 }
        val city get() = values["city"] as? String ?: ""
    }

    /** Reads a backup file; throws IllegalArgumentException with a Turkish message if it is not a Namazım backup. */
    fun readBackup(text: String): Backup {
        val root = try { JSONObject(text) } catch (e: org.json.JSONException) { throw IllegalArgumentException("Bu dosya bir Namazım yedeği değil.") }
        require(root.optString("app") == "namazim") { "Bu dosya bir Namazım yedeği değil." }
        require(root.optInt("v") == 1) { "Bu yedek, uygulamanın daha yeni bir sürümünden." }
        val prefs = root.optJSONObject("prefs") ?: throw IllegalArgumentException("Yedek dosyası bozuk.")
        val out = mutableMapOf<String, Any>()
        for (k in prefs.keys()) {
            val e = prefs.optJSONObject(k) ?: throw IllegalArgumentException("Yedek dosyası bozuk.")
            require(k.length <= 200) { "Yedek dosyası bozuk." }
            out[k] = try {
                when (e.getString("t")) {
                    "s" -> e.getString("v")
                    "i" -> e.getInt("v")
                    "l" -> e.getLong("v")
                    "b" -> e.getBoolean("v")
                    "f" -> e.getDouble("v").toFloat()
                    "set" -> e.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                    else -> throw IllegalArgumentException("Yedek dosyası bozuk.")
                }
            } catch (x: org.json.JSONException) { throw IllegalArgumentException("Yedek dosyası bozuk.") }
        }
        return Backup(root.optLong("at"), out)
    }

    /** Replaces everything with the backup in one commit, so a failure leaves the old data in place. */
    fun restore(b: Backup): Boolean {
        val e = p.edit().clear()
        b.values.forEach { (k, v) ->
            @Suppress("UNCHECKED_CAST")
            when (v) {
                is String -> e.putString(k, v); is Int -> e.putInt(k, v); is Long -> e.putLong(k, v)
                is Boolean -> e.putBoolean(k, v); is Float -> e.putFloat(k, v); is Set<*> -> e.putStringSet(k, v as Set<String>)
            }
        }
        val ok = e.commit()
        StoreRev.n++
        return ok
    }

    /** Pages read per day: "2026-11-01" -> count. */
    fun readLog(): Map<String, Int> {
        val o = JSONObject(p.getString("readLog", "{}")!!)
        return o.keys().asSequence().associateWith { o.getInt(it) }
    }
    fun logPages(n: Int) {
        StoreRev.n++
        val o = JSONObject(p.getString("readLog", "{}")!!)
        val today = prayerDay().toString()
        o.put(today, (o.optInt(today) + n).coerceAtLeast(0))
        p.edit().putString("readLog", o.toString()).apply()
    }
}








