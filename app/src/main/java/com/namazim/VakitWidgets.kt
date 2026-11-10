package com.namazim

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// Home-screen widgets W1 "Harem", W2 "Gül", W3 "Şerit" (design/widget/widget-secenekler.html, owner chose all three
// on 2026-11-08 so the user picks). Each shows the vakit, a live countdown (a Chronometer, so no ticking from us) and a
// "Kıldım" that marks the prayer through PrayedReceiver without opening the app.

class HaremWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = ids.forEach { VakitWidgets.update(ctx, mgr, it, VakitWidgets.Tur.HAREM) }
}
class GulWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = ids.forEach { VakitWidgets.update(ctx, mgr, it, VakitWidgets.Tur.GUL) }
}
class SeritWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = ids.forEach { VakitWidgets.update(ctx, mgr, it, VakitWidgets.Tur.SERIT) }
}

object VakitWidgets {
    enum class Tur(val layout: Int, val code: Int) { HAREM(R.layout.widget_harem, 610), GUL(R.layout.widget_gul, 620), SERIT(R.layout.widget_serit, 630) }

    /** Every widget of the app, after a mark, an undo, an alarm or an özel gün change. */
    fun refreshAll(ctx: Context) {
        runCatching { NamazimWidget.refresh(ctx) }
        val mgr = AppWidgetManager.getInstance(ctx)
        listOf(HaremWidget::class.java to Tur.HAREM, GulWidget::class.java to Tur.GUL, SeritWidget::class.java to Tur.SERIT).forEach { (cls, tur) ->
            mgr.getAppWidgetIds(ComponentName(ctx, cls)).forEach { runCatching { update(ctx, mgr, it, tur) } }
        }
    }

    private val GOLD = 0xFFE8BE74.toInt()
    private val TEXT = 0xFFF3EEE3.toInt()
    private val MUTED = 0xFFC9D0DE.toInt()
    private val INK = 0xFF0B1322.toInt()

    /** What the widgets say right now. */
    private class Durum(
        val slots: List<Slot>, val cur: Int, val curDone: Boolean, val prayed: Set<Int>, val onTime: Set<Int>,
        val k: String, val big: String, val sub: String, val target: Long, val ozel: Boolean, val photo: Int, val dilim: List<Dilim>?, val nowFrac: Float, val kadin: Boolean,
    )

    private fun durum(ctx: Context): Durum? {
        val s = Store(ctx)
        if (!s.hasLocation) return null
        val now = System.currentTimeMillis()
        val tr = Locale("tr")
        val cal = LocalDate.now()
        val imsakToday = slots(s.lat, s.lng, cal).first().start
        val day = if (now < imsakToday) cal.minusDays(1) else cal
        val sl = slots(s.lat, s.lng, day)
        val next = sl.firstOrNull { it.start > now } ?: slots(s.lat, s.lng, day.plusDays(1)).first()
        val cur = sl.indexOfFirst { now in it.start until it.end }
        val c = sl.getOrNull(cur)
        val curDone = c != null && s.isPrayed(c.key)
        val ozel = s.ozelGunNow != null
        val d = dayTimes(s.lat, s.lng, cal)
        val photo = when (haremClip(now, d.imsak, d.gunes, d.ikindi, d.aksam)) {
            "medine-safak.mp4" -> R.drawable.harem_medine_safak
            "mekke-gun.mp4" -> R.drawable.harem_mekke_gun
            "medine-aksam.mp4" -> R.drawable.harem_medine_aksam
            else -> R.drawable.harem_mekke_gece
        }
        val (k, big, sub, target) = when {
            ozel -> listOf("ÖZEL GÜN", hm(next.start), "takip duraklatıldı", next.start)
            c != null && !curDone && !s.exempt(c) -> listOf("${c.satir(s.kadin)} vakti".uppercase(tr), hm(c.start), "vaktin çıkmasına", c.end)
            else -> listOf("${next.satir(s.kadin)} vaktine".uppercase(tr), hm(next.start), if (curDone) "${c!!.satir(s.kadin)} kılındı" else "kaldı", next.start)
        }
        return Durum(
            sl, cur, curDone, sl.filter { s.isPrayed(it.key) }.map { it.vakit.ordinal }.toSet(),
            sl.filter { s.onTime(it) }.map { it.vakit.ordinal }.toSet(), k as String, big as String, sub as String, target as Long, ozel, photo,
            c?.let { s.dilimler(it) }, c?.let { ((now - it.start).toFloat() / (it.end - it.start)).coerceIn(0f, 1f) } ?: 0f, s.kadin,
        )
    }

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int, tur: Tur) = mgr.updateAppWidget(id, views(ctx, tur))

    /** The widget's views (also shown big on the debug preview screen, `--ez widgetpreview true`). */
    fun views(ctx: Context, tur: Tur): RemoteViews {
        val v = RemoteViews(ctx.packageName, tur.layout)
        v.setOnClickPendingIntent(R.id.w_root, open(ctx, tur.code))
        val d = durum(ctx)
        if (d == null) {
            v.setTextViewText(R.id.w_k, "KONUM GEREKLİ"); v.setTextViewText(R.id.w_big, "—")
            v.setViewVisibility(R.id.w_btn, View.GONE)
            return v
        }
        v.setTextViewText(R.id.w_k, d.k)
        v.setTextViewText(R.id.w_big, d.big)
        if (tur == Tur.HAREM) v.setTextViewText(R.id.w_sub, d.sub)
        // live countdown to the target; the system ticks it
        v.setChronometer(R.id.w_cd, SystemClock.elapsedRealtime() + (d.target - System.currentTimeMillis()), null, true)
        v.setChronometerCountDown(R.id.w_cd, true)

        // Kıldım: only for an open, unmarked vakit; after it "Kılındı" (opens the app); nothing outside a prayer time
        val c = d.slots.getOrNull(d.cur)
        when {
            d.ozel || c == null -> v.setViewVisibility(R.id.w_btn, View.GONE)
            !d.curDone -> {
                v.setViewVisibility(R.id.w_btn, View.VISIBLE)
                v.setOnClickPendingIntent(R.id.w_btn, PendingIntent.getBroadcast(ctx, tur.code + 1,
                    Intent(ctx, PrayedReceiver::class.java).putExtra("key", c.key).putExtra("vakit", c.vakit.name),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                if (tur == Tur.SERIT) { v.setInt(R.id.w_btn, "setBackgroundResource", R.drawable.wg_check); v.setTextColor(R.id.w_btn, GOLD) }
                else { v.setTextViewText(R.id.w_btn, "✓ Kıldım"); v.setInt(R.id.w_btn, "setBackgroundResource", R.drawable.wg_btn_gold); v.setTextColor(R.id.w_btn, INK) }
            }
            else -> {
                v.setViewVisibility(R.id.w_btn, View.VISIBLE)
                v.setOnClickPendingIntent(R.id.w_btn, open(ctx, tur.code + 2))
                if (tur == Tur.SERIT) { v.setInt(R.id.w_btn, "setBackgroundResource", R.drawable.wg_check_on); v.setTextColor(R.id.w_btn, INK) }
                else { v.setTextViewText(R.id.w_btn, "✓ Kılındı"); v.setInt(R.id.w_btn, "setBackgroundResource", R.drawable.wg_btn_done); v.setTextColor(R.id.w_btn, GOLD) }
            }
        }

        val focus = if (d.cur >= 0) d.cur else d.slots.indexOfFirst { it.start > System.currentTimeMillis() }.coerceAtLeast(0)
        when (tur) {
            Tur.HAREM -> {
                v.setImageViewResource(R.id.w_photo, d.photo)
                val rows = listOf(R.id.w_r0, R.id.w_r1, R.id.w_r2, R.id.w_r3, R.id.w_r4)
                val names = listOf(R.id.w_n0, R.id.w_n1, R.id.w_n2, R.id.w_n3, R.id.w_n4)
                val times = listOf(R.id.w_t0, R.id.w_t1, R.id.w_t2, R.id.w_t3, R.id.w_t4)
                d.slots.forEachIndexed { i, s ->
                    val on = i == focus
                    v.setInt(rows[i], "setBackgroundResource", if (on) R.drawable.wg_row_on else R.drawable.wg_row_off)
                    v.setTextViewText(names[i], s.satir(d.kadin) + if (i in d.prayed && !on) " ✓" else "")
                    v.setTextColor(names[i], if (on) INK else if (i in d.prayed) GOLD else MUTED)
                    v.setTextViewText(times[i], hm(s.start))
                    v.setTextColor(times[i], if (on) INK else TEXT)
                }
            }
            Tur.GUL -> {
                v.setImageViewBitmap(R.id.w_rose, gulBitmap(360, d.prayed, d.onTime, if (d.cur >= 0 && !d.curDone) d.cur else -1))
                val xs = listOf(R.id.w_x0, R.id.w_x1, R.id.w_x2, R.id.w_x3, R.id.w_x4)
                d.slots.forEachIndexed { i, s ->
                    v.setTextViewText(xs[i], "${s.satir(d.kadin).let { if (it == "Cuma") it else it.take(3) }}\n${hm(s.start)}")
                    v.setTextColor(xs[i], if (i == focus) GOLD else MUTED)
                }
            }
            Tur.SERIT -> {
                v.setImageViewBitmap(R.id.w_rose, gulBitmap(160, d.prayed, d.onTime, if (d.cur >= 0 && !d.curDone) d.cur else -1))
                v.setImageViewBitmap(R.id.w_strip, seritBitmap(d.dilim, d.nowFrac, d.curDone))
            }
        }
        return v
    }

    private fun open(ctx: Context, code: Int) = PendingIntent.getActivity(
        ctx, code, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The home rose (K6): cupped petals, soft shadows; gold when prayed, the inner petal gold when on time;
     *  [open] (or -1) is the current, unmarked vakit, drawn last with a light edge. */
    fun gulBitmap(px: Int, prayed: Set<Int>, onTime: Set<Int>, open: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val k = px / 222f
        val cx = px / 2f
        fun pt(r: Float, a: Float): Pair<Float, Float> { val t = Math.toRadians(a - 90.0); return (cx + r * k * cos(t).toFloat()) to (cx + r * k * sin(t).toFloat()) }
        fun petal(r0: Float, r1: Float, a0: Float, a1: Float) = Path().apply {
            val w = a1 - a0
            pt(r0, a0).let { moveTo(it.first, it.second) }
            val c1 = pt(r1 * 1.02f, a0 - w * .08f); val c2 = pt(r1 * 1.16f, a0 + w * .34f); val m = pt(r1, a0 + w * .5f)
            cubicTo(c1.first, c1.second, c2.first, c2.second, m.first, m.second)
            val c3 = pt(r1 * 1.16f, a1 - w * .34f); val c4 = pt(r1 * 1.02f, a1 + w * .08f); val e = pt(r0, a1)
            cubicTo(c3.first, c3.second, c4.first, c4.second, e.first, e.second)
            close()
        }
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
        val velvet = intArrayOf(0xFF060A18.toInt(), 0xFF1A2A52.toInt(), 0xFF4A65A6.toInt())
        val warm = intArrayOf(0xFF5A3A10.toInt(), 0xFFC8913C.toInt(), 0xFFFFE7B0.toInt())
        val velvet2 = intArrayOf(0xFF050914.toInt(), 0xFF1C2C58.toInt(), 0xFF5370B2.toInt())
        val warm2 = intArrayOf(0xFF6B4312.toInt(), 0xFFE2B060.toInt(), 0xFFFFF3D6.toInt())
        fun drawShadow(p: Path, dy: Float, blur: Float) {
            shade.maskFilter = BlurMaskFilter(blur * k, BlurMaskFilter.Blur.NORMAL)
            c.save(); c.translate(0f, dy * k); c.drawPath(p, shade); c.restore()
        }
        val order = (0 until 5).filter { it != open } + listOfNotNull(open.takeIf { it >= 0 })
        for (i in order) {
            val a = i * 72f
            val o = petal(24f, 70f, a - 40f, a + 40f)
            drawShadow(o, 3f, 2.6f)
            val g = pt(16f, a)
            fill.shader = RadialGradient(g.first, g.second, 66f * k, if (i in prayed) warm else velvet, floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(o, fill)
            edge.color = if (i == open) TEXT else if (i in prayed) 0xFF7A5418.toInt() else 0xFF0A1124.toInt()
            edge.strokeWidth = (if (i == open) 2.4f else 1f) * k
            c.drawPath(o, edge)
        }
        for (i in order) {
            val a = i * 72f
            val inn = petal(12f, 46f, a - 26f, a + 26f)
            drawShadow(inn, 2.4f, 1.3f)
            val g = pt(8f, a)
            fill.shader = RadialGradient(g.first, g.second, 44f * k, if (i in onTime) warm2 else velvet2, floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(inn, fill)
        }
        fill.shader = RadialGradient(cx, cx + 10.5f * k, 22f * k, intArrayOf(0xFF6B4312.toInt(), 0xFFD49E48.toInt(), 0xFFFFF0C8.toInt()),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cx, 10.5f * k, fill)
        return bmp
    }

    /** The current vakit's seven slices for W3: faziletli gold, güzel half gold, kerahat hatched peach, a light at [nowFrac]. */
    fun seritBitmap(d: List<Dilim>?, nowFrac: Float, done: Boolean): Bitmap {
        val w = 700; val h = 30
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val bar = 12f; val y0 = (h - bar) / 2f; val gap = 6f
        val parts = d ?: List(7) { Dilim.VAKIT }
        for (j in 0 until 7) {
            val x0 = j * w / 7f + if (j > 0) gap / 2 else 0f
            val x1 = (j + 1) * w / 7f - if (j < 6) gap / 2 else 0f
            p.shader = null
            p.color = when (parts[j]) {
                Dilim.FAZILET -> GOLD
                Dilim.GUZEL -> 0x73E8BE74
                Dilim.KERAHAT -> 0x4DF4B08F
                Dilim.VAKIT -> 0xFF22304F.toInt()
            }
            if (parts[j] == Dilim.KERAHAT) p.shader = LinearGradient(x0, 0f, x0 + 8f, 8f, intArrayOf(0xB3F4B08F.toInt(), 0x33F4B08F), null, Shader.TileMode.MIRROR)
            c.drawRoundRect(RectF(x0, y0, x1, y0 + bar), bar / 2, bar / 2, p)
        }
        p.shader = null
        if (d != null) {
            val nx = nowFrac * w
            p.color = 0x8C0B1322.toInt(); c.drawRoundRect(RectF(0f, y0, nx, y0 + bar), bar / 2, bar / 2, p)
            if (!done) { p.color = 0x59E8BE74; c.drawCircle(nx.coerceIn(10f, w - 10f), h / 2f, 13f, p); p.color = 0xFFFFF4DC.toInt(); c.drawCircle(nx.coerceIn(10f, w - 10f), h / 2f, 9f, p) }
        }
        return bmp
    }
}
