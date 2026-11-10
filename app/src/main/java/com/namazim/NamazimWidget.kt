package com.namazim

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.widget.RemoteViews
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 1×1 home-screen widget: the "Beş vakit gülü" logo, but alive — the sky behind it takes the colour of the
 * time of day, the petal of the next prayer is outlined bright, the petals fill with gold as prayers are marked.
 * Android has no live launcher icons, so this is the next best thing.
 */
class NamazimWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = ids.forEach { update(ctx, mgr, it) }
    override fun onEnabled(ctx: Context) = refresh(ctx)
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: android.os.Bundle) = update(ctx, mgr, id)

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NamazimWidget::class.java))
            ids.forEach { update(ctx, mgr, it) }
        }

        private fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_namazim)
            views.setImageViewBitmap(R.id.w_icon, art(ctx, 256))
            val st = state(ctx)
            views.setTextViewText(R.id.w_label, st.label)
            views.setOnClickPendingIntent(R.id.w_root, open(ctx))
            mgr.updateAppWidget(id, views)
        }

        private fun open(ctx: Context) = PendingIntent.getActivity(
            ctx, 500, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private data class State(
            val label: String, val accent: Int, val skyTop: Int, val skyBottom: Int,
            val nextIndex: Int, val prayed: Set<Int>,
        )

        private fun state(ctx: Context): State {
            val s = Store(ctx)
            val now = System.currentTimeMillis()
            val accent = 0xFFE8BE74.toInt()
            val alt = if (s.hasLocation) skyBodies(s.lat, s.lng, now).sun.alt / (PI / 180) else 30.0
            // Sky colour of the moment: the same ranges the live sky uses.
            val (top, bottom) = when {
                alt > 8 -> 0xFF2A6CB2.toInt() to 0xFFA9CBE8.toInt()
                alt > 0 -> 0xFF3A4C82.toInt() to 0xFFE59A62.toInt()
                alt > -9 -> 0xFF1B2547.toInt() to 0xFF8E6B82.toInt()
                else -> 0xFF0D1730.toInt() to 0xFF1C2C50.toInt()
            }
            if (!s.hasLocation) return State("Konum gerekli", accent, top, bottom, 0, emptySet())

            // Namaz günü imsakla başlar: gece yarısından sonra imsaka kadar dünün yatsısı hâlâ kılınabilir.
            val calDate = LocalDate.now()
            val imsakToday = slots(s.lat, s.lng, calDate).first().start
            val today = if (now < imsakToday) calDate.minusDays(1) else calDate
            val todaySlots = slots(s.lat, s.lng, today)
            val next = todaySlots.firstOrNull { it.start > now }
                ?: slots(s.lat, s.lng, today.plusDays(1)).first()
            val prayed = todaySlots.filter { s.isPrayed(it.key) }.map { it.vakit.ordinal }.toSet()
            // Right after a prayer time has passed, the hand still shows the one you owe.
            val owed = todaySlots.firstOrNull { now in it.start until it.end && !s.isPrayed(it.key) }
            val idx = owed?.vakit?.ordinal ?: next.vakit.ordinal
            val label = if (owed != null) "${owed.ad(s.kadin)} · kıl" else "${next.ad(s.kadin)} ${hm(next.start)}"
            return State(label, accent, top, bottom, idx, prayed)
        }

        /** The icon's caption, e.g. "İkindi 15:27" (also used by the debug preview screen). */
        internal fun label(ctx: Context): String = state(ctx).label

        /** The round icon as a bitmap: sky, the rose, a petal per vakit. */
        internal fun art(ctx: Context, px: Int): Bitmap {
            val st = state(ctx)
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val u = px / 108f
            val round = Path().apply { addCircle(54f * u, 54f * u, 54f * u, Path.Direction.CW) }
            c.save()
            c.clipPath(round)
            c.drawRect(0f, 0f, px.toFloat(), px.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, 0f, 0f, px.toFloat(), st.skyTop, st.skyBottom, Shader.TileMode.CLAMP)
                })

            // the rose of the launcher icon ("Beş vakit gülü"): outer petal i is vakit i, clockwise from the top.
            // A prayed vakit fills its petal with gold; the next (or owed) one gets a bright, heavier edge.
            val r = 31f
            fun pt(rad: Float, deg: Float): Pair<Float, Float> {
                val t = Math.toRadians(deg - 90.0)
                return (54f + rad * cos(t).toFloat()) * u to (54f + rad * sin(t).toFloat()) * u
            }
            fun petal(r0: Float, r1: Float, a0: Float, a1: Float, close: Boolean) = Path().apply {
                val w = a1 - a0
                val s0 = pt(r0, a0); moveTo(s0.first, s0.second)
                val c1 = pt(r1 * 1.02f, a0 - w * .08f); val c2 = pt(r1 * 1.16f, a0 + w * .34f); val m = pt(r1, a0 + w * .5f)
                cubicTo(c1.first, c1.second, c2.first, c2.second, m.first, m.second)
                val c3 = pt(r1 * 1.16f, a1 - w * .34f); val c4 = pt(r1 * 1.02f, a1 + w * .08f); val e0 = pt(r0, a1)
                cubicTo(c3.first, c3.second, c4.first, c4.second, e0.first, e0.second)
                if (close) close()
            }
            val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 14, 26, 48); style = Paint.Style.FILL }
            val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.accent; style = Paint.Style.FILL; alpha = 105 }
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = st.accent; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            }
            // the next vakit's petal goes last so nothing covers its bright edge
            for (i in (0 until 5).filter { it != st.nextIndex } + st.nextIndex) {
                val a = i * 72f
                val shape = petal(r * .34f, r, a - 40f, a + 40f, true)
                c.drawPath(shape, shade)   // each petal covers the one before it
                if (i in st.prayed) c.drawPath(shape, gold)
                val next = i == st.nextIndex
                line.strokeWidth = (if (next) 3.6f else 2.2f) * u
                line.color = if (next) 0xFFF3EEE3.toInt() else st.accent
                c.drawPath(petal(r * .34f, r, a - 40f, a + 40f, false), line)
            }
            line.color = st.accent; line.strokeWidth = 1.7f * u
            for (i in 0 until 5) { val a = i * 72f + 36f; c.drawPath(petal(r * .16f, r * .6f, a - 38f, a + 38f, false), line) }
            val bud = Path()
            for (k in 0..60) {
                val th = k / 60.0 * PI * 3.2
                val rad = r * .24f * (1 - k / 68f)
                val x = (54f + rad * cos(th).toFloat()) * u; val y = (54f + rad * sin(th).toFloat()) * u
                if (k == 0) bud.moveTo(x, y) else bud.lineTo(x, y)
            }
            c.drawPath(bud, line)
            c.restore()
            return bmp
        }
    }
}
