package com.namazim

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Book(val name: String, val page: Int, val total: Int)

/** All app data lives in one SharedPreferences file; it is small enough. */
class Store(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("namazim", Context.MODE_PRIVATE)

    // --- Location
    val hasLocation get() = p.contains("lat")
    val lat get() = Double.fromBits(p.getLong("lat", 0))
    val lng get() = Double.fromBits(p.getLong("lng", 0))
    val city get() = p.getString("city", "") ?: ""
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
    fun tracked(s: Slot) = s.end > since

    // --- Prayed log, keyed by Slot.key
    fun isPrayed(key: String) = key in prayed()
    fun prayed(): Set<String> = p.getStringSet("prayed", emptySet())!!
    fun markPrayed(key: String) = p.edit().putStringSet("prayed", prayed() + key).apply()

    // --- Kaza, keyed by Vakit name or "VITIR"
    fun kaza(key: String) = p.getInt("kaza_$key", 0)
    fun kaza(v: Vakit) = kaza(v.name)
    fun addKaza(key: String, n: Int) = p.edit().putInt("kaza_$key", (kaza(key) + n).coerceAtLeast(0)).apply()
    fun addKaza(v: Vakit, n: Int) = addKaza(v.name, n)
    /** A kaza prayer made up: one less owed, one more on this month's tally. */
    fun payKaza(key: String) {
        if (kaza(key) == 0) return
        addKaza(key, -1)
        p.edit().putInt(paidKey(), kazaPaidThisMonth() + 1).apply()
    }
    fun kazaPaidThisMonth() = p.getInt(paidKey(), 0)
    private fun paidKey() = "kazaPaid_" + LocalDate.now().toString().take(7)
    var kazaRate: Int
        get() = p.getInt("kazaRate", 2)
        set(v) = p.edit().putInt("kazaRate", v).apply()

    // --- Settings
    var theme: String
        get() = p.getString("theme", SeherPalette.id)!!
        set(v) = p.edit().putString("theme", v).apply()
    /** Which renderer the Seher header uses: "shader" (live), "photo" (plates) or "video" (looping clip). */
    var skyMode: String
        get() = p.getString("skyMode", "shader")!!
        set(v) = p.edit().putString("skyMode", v).apply()
    /** Minutes before a prayer time for the "hazırlan" notifications. */
    var prepOffsets: Set<Int>
        get() = p.getStringSet("prepOffsets", null)?.map { it.toInt() }?.toSet() ?: setOf(30, 15, 5)
        set(v) = p.edit().putStringSet("prepOffsets", v.map { it.toString() }.toSet()).apply()
    var nagEvery: Int
        get() = p.getInt("nagEvery", 15)
        set(v) = p.edit().putInt("nagEvery", v).apply()
    /** Nag every 5 minutes in the last half hour of a prayer time. */
    var nagBurst: Boolean
        get() = p.getBoolean("nagBurst", true)
        set(v) = p.edit().putBoolean("nagBurst", v).apply()
    var snoozeMin: Int
        get() = p.getInt("snoozeMin", 15)
        set(v) = p.edit().putInt("snoozeMin", v).apply()
    fun reminderCfg() = ReminderCfg(prepOffsets.map { it.toLong() }.sortedDescending(), nagEvery.toLong(), nagBurst)

    var showExtras: Boolean
        get() = p.getBoolean("showExtras", false)
        set(v) = p.edit().putBoolean("showExtras", v).apply()
    var readGoal: Int
        get() = p.getInt("readGoal", 10)
        set(v) = p.edit().putInt("readGoal", v).apply()

    // --- Snooze: no reminders for a prayer until this moment
    fun snoozedUntil(key: String) = p.getLong("snooze_$key", 0)
    fun snooze(key: String, until: Long) = p.edit().putLong("snooze_$key", until).apply()

    /** Prayers whose time ended before this moment have already been counted as prayed or kaza. */
    var settledUntil: Long
        get() = p.getLong("settledUntil", 0)
        set(v) = p.edit().putLong("settledUntil", v).apply()

    /** Any slot that ended unprayed since the last check becomes kaza. */
    fun settleKaza(now: Long = System.currentTimeMillis()) {
        if (!hasLocation) return
        if (settledUntil == 0L) { settledUntil = now; return } // nothing before first use counts
        var day = LocalDate.now().minusDays(2)
        // shortcut: only looks 2 days back, so a phone off for longer misses kaza; widen if that matters.
        while (!day.isAfter(LocalDate.now())) {
            for (s in slots(lat, lng, day)) {
                if (s.end in (settledUntil + 1)..now && !isPrayed(s.key)) {
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
        set(v) = p.edit().putInt("quranPage", v).apply()
    var hatim: Int
        get() = p.getInt("hatim", 0)
        set(v) = p.edit().putInt("hatim", v).apply()

    fun books(): List<Book> {
        val a = JSONArray(p.getString("books", "[]"))
        return (0 until a.length()).map { a.getJSONObject(it).run { Book(getString("name"), getInt("page"), getInt("total")) } }
    }
    fun saveBooks(list: List<Book>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("page", it.page).put("total", it.total)) }
        p.edit().putString("books", a.toString()).apply()
    }

    /** Pages read per day: "2026-11-01" -> count. */
    fun readLog(): Map<String, Int> {
        val o = JSONObject(p.getString("readLog", "{}")!!)
        return o.keys().asSequence().associateWith { o.getInt(it) }
    }
    fun logPages(n: Int) {
        val o = JSONObject(p.getString("readLog", "{}")!!)
        val today = LocalDate.now().toString()
        o.put(today, (o.optInt(today) + n).coerceAtLeast(0))
        p.edit().putString("readLog", o.toString()).apply()
    }
}
