package com.namazim

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.widget.RemoteViews
import java.time.LocalDate

/**
 * 1×1 home-screen widget, the "canlı ikon": the icon of the vakit we are in (design/design-system/vakit-ikon-secenekler.html,
 * 2026-11-10): İmsak Sedef, Öğle Altın zümrüt, İkindi Gün batımı, Akşam Zümrüt, Yatsı Gece nuru, a Friday's öğle the Cuma icon.
 * The launcher icon itself stays put (changing it five times a day makes launchers drop or reshuffle it).
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
            views.setTextViewText(R.id.w_label, state(ctx).label)
            views.setOnClickPendingIntent(R.id.w_root, open(ctx))
            mgr.updateAppWidget(id, views)
        }

        private fun open(ctx: Context) = PendingIntent.getActivity(
            ctx, 500, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private data class State(val label: String, val art: Int)

        /** The art of a vakit; a Friday's öğle gets the Cuma icon (for everyone: it is the day's picture, not the prayer's name). */
        fun vakitArt(s: Slot): Int = when {
            s.vakit == Vakit.OGLE && s.date.dayOfWeek == java.time.DayOfWeek.FRIDAY -> R.drawable.wg_vakit_cuma
            s.vakit == Vakit.SABAH -> R.drawable.wg_vakit_imsak
            s.vakit == Vakit.OGLE -> R.drawable.wg_vakit_ogle
            s.vakit == Vakit.IKINDI -> R.drawable.wg_vakit_ikindi
            s.vakit == Vakit.AKSAM -> R.drawable.wg_vakit_zumrut
            else -> R.drawable.wg_vakit_yatsi
        }

        private fun state(ctx: Context): State {
            val s = Store(ctx)
            if (!s.hasLocation) return State("Konum gerekli", R.drawable.wg_vakit_zumrut)
            val now = System.currentTimeMillis()
            // Namaz günü imsakla başlar: gece yarısından sonra imsaka kadar dünün yatsısı hâlâ kılınabilir.
            val calDate = LocalDate.now()
            val imsakToday = slots(s.lat, s.lng, calDate).first().start
            val today = if (now < imsakToday) calDate.minusDays(1) else calDate
            val todaySlots = slots(s.lat, s.lng, today)
            val next = todaySlots.firstOrNull { it.start > now } ?: slots(s.lat, s.lng, today.plusDays(1)).first()
            val current = todaySlots.firstOrNull { now in it.start until it.end }
            // Right after a prayer time has passed, the label still shows the one you owe.
            val owed = current?.takeIf { !s.isPrayed(it.key) }
            val label = if (owed != null) "${owed.ad(s.kadin)} · kıl" else "${next.ad(s.kadin)} ${hm(next.start)}"
            return State(label, vakitArt(current ?: next))   // between güneş and öğle: öğle's picture
        }

        /** The icon's caption, e.g. "İkindi 15:27" (also used by the debug preview screen). */
        internal fun label(ctx: Context): String = state(ctx).label

        /** The round icon as a bitmap: the vakit's art, clipped to a circle. */
        internal fun art(ctx: Context, px: Int): Bitmap {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.clipPath(Path().apply { addCircle(px / 2f, px / 2f, px / 2f, Path.Direction.CW) })
            ctx.getDrawable(state(ctx).art)?.apply { setBounds(0, 0, px, px); draw(c) }
            return bmp
        }
    }
}
