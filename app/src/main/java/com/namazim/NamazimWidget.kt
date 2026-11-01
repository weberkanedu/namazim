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
 * 1×1 home-screen widget: the "hilal saat" logo, but alive — the sky behind it takes the colour of the
 * time of day, the hand points at the next prayer, the five dots fill in as prayers are marked.
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
            val accent = if (s.theme == OdakPalette.id) 0xFFC6F45E.toInt() else 0xFFE8BE74.toInt()
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
            val label = if (owed != null) "${owed.vakit.ad} · kıl" else "${next.vakit.ad} ${hm(next.start)}"
            return State(label, accent, top, bottom, idx, prayed)
        }

        /** The icon's caption, e.g. "İkindi 15:27" (also used by the debug preview screen). */
        internal fun label(ctx: Context): String = state(ctx).label

        /** The round icon as a bitmap: sky, crescent, five dots, hand. */
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

            // crescent: the same lune as the launcher icon, drawn as a thin gold rim so the widget
            // and the icon look like one family (the lace lattice itself is too fine at 52 dp)
            val outer = Path().apply { addCircle(54f * u, 54f * u, 30f * u, Path.Direction.CW) }
            val inner = Path().apply { addCircle(64f * u, 46f * u, 26f * u, Path.Direction.CW) }
            val crescent = Path().apply { op(outer, inner, Path.Op.DIFFERENCE) }
            c.drawPath(crescent, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = st.accent; style = Paint.Style.STROKE; strokeWidth = 3.0f * u; strokeJoin = Paint.Join.ROUND
            })

            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            for (i in 0 until 5) {
                val a = (-90 + i * 30) * PI / 180
                val x = 54f + cos(a).toFloat() * 21f
                val y = 54f + sin(a).toFloat() * 21f
                if (i in st.prayed) {
                    dot.style = Paint.Style.FILL; dot.color = st.accent
                } else {
                    dot.style = Paint.Style.STROKE; dot.strokeWidth = 1.5f * u; dot.color = Color.argb(150, 255, 255, 255)
                }
                c.drawCircle(x * u, y * u, 2.4f * u, dot)
            }

            val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF3EEE3.toInt(); strokeWidth = 3.5f * u; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
            }
            val a = (-90 + st.nextIndex * 30) * PI / 180
            c.drawLine(54f * u, 54f * u, (54f + cos(a).toFloat() * 14f) * u, (54f + sin(a).toFloat() * 14f) * u, hand)
            c.drawCircle(54f * u, 54f * u, 3.4f * u,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF3EEE3.toInt(); style = Paint.Style.FILL })
            c.restore()
            return bmp
        }
    }
}
