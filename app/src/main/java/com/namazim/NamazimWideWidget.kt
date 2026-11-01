package com.namazim

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate

/**
 * 2×1 "geniş ikon": solda hilal, sağda "3/5 kılındı" ve altında sıradaki vakte kalan süre.
 * 1×1 widget'ın kardeşi; aynı hilal çizimi ve aynı namaz-günü mantığı.
 */
class NamazimWideWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = ids.forEach { update(ctx, mgr, it) }
    override fun onEnabled(ctx: Context) = refresh(ctx)
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: android.os.Bundle) = update(ctx, mgr, id)

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NamazimWideWidget::class.java))
            ids.forEach { update(ctx, mgr, it) }
        }

        private fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_namazim_wide)
            views.setImageViewBitmap(R.id.w_icon, NamazimWidget.art(ctx, 192))

            val s = Store(ctx)
            val now = System.currentTimeMillis()
            val accent = if (s.theme == OdakPalette.id) 0xFFC6F45E.toInt() else 0xFFE8BE74.toInt()

            if (!s.hasLocation) {
                views.setTextViewText(R.id.w_count, "Konum gerekli")
                views.setTextViewText(R.id.w_next, "Uygulamayı aç ve konumu güncelle")
            } else {
                // Namaz günü imsakla başlar (gece yarısından sonra dünün yatsısı hâlâ kılınabilir).
                val calDate = LocalDate.now()
                val imsakToday = slots(s.lat, s.lng, calDate).first().start
                val today = if (now < imsakToday) calDate.minusDays(1) else calDate
                val todaySlots = slots(s.lat, s.lng, today)
                val prayed = todaySlots.count { s.isPrayed(it.key) }
                val owed = todaySlots.firstOrNull { now in it.start until it.end && !s.isPrayed(it.key) }
                val next = todaySlots.firstOrNull { it.start > now }
                    ?: slots(s.lat, s.lng, today.plusDays(1)).first()

                views.setTextViewText(R.id.w_count, "$prayed/5 kılındı")
                views.setTextViewText(
                    R.id.w_next,
                    if (owed != null) "${owed.vakit.ad} · ${left(owed.end - now)} kaldı"
                    else "${next.vakit.ad}'ya ${left(next.start - now)}",
                )
            }
            views.setOnClickPendingIntent(R.id.w_root, open(ctx))
            mgr.updateAppWidget(id, views)
        }

        /** "5sa 12dk" ya da "12dk". */
        private fun left(ms: Long): String {
            val total = (ms / 60_000).coerceAtLeast(0)
            val h = total / 60
            val m = total % 60
            return if (h > 0) "${h}sa ${m}dk" else "${m}dk"
        }

        private fun open(ctx: Context) = PendingIntent.getActivity(
            ctx, 501, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

