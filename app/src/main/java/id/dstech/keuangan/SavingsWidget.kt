package id.dstech.keuangan

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/** Widget terpisah untuk tabungan: total terkumpul dan progres tiap tujuan. */
class SavingsWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, build(context))
        }
    }

    companion object {
        /** Dipanggil setiap data berubah supaya widget ikut diperbarui. */
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, SavingsWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(ctx)
            for (id in ids) mgr.updateAppWidget(id, views)
        }

        private fun build(ctx: Context): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_savings)
            val all = GoalStore.load(ctx)
            val (saved, target) = GoalStore.totals(all)
            rv.setTextViewText(R.id.s_saved, Fmt.rupiah(saved))
            rv.setTextViewText(R.id.s_target, Fmt.rupiah(target))

            // Tujuan yang belum tercapai ditampilkan lebih dulu
            val shown = all.sortedBy { it.reached }.take(3)
            rv.setViewVisibility(R.id.s_empty, if (shown.isEmpty()) View.VISIBLE else View.GONE)

            val rows = intArrayOf(R.id.s_r1, R.id.s_r2, R.id.s_r3)
            val names = intArrayOf(R.id.s_n1, R.id.s_n2, R.id.s_n3)
            val percents = intArrayOf(R.id.s_p1, R.id.s_p2, R.id.s_p3)
            val bars = intArrayOf(R.id.s_b1, R.id.s_b2, R.id.s_b3)
            val infos = intArrayOf(R.id.s_t1, R.id.s_t2, R.id.s_t3)

            for (i in rows.indices) {
                if (i < shown.size) {
                    val g = shown[i]
                    rv.setViewVisibility(rows[i], View.VISIBLE)
                    rv.setTextViewText(names[i], g.name)
                    rv.setTextViewText(percents[i], "${g.percent}%")
                    rv.setProgressBar(bars[i], 100, g.percent, false)
                    val daysLeft = g.daysLeft
                    val info = "${Fmt.rupiah(g.saved)} dari ${Fmt.rupiah(g.target)}" +
                        (if (daysLeft != null) " • $daysLeft hari lagi" else "")
                    rv.setTextViewText(infos[i], info)
                } else {
                    rv.setViewVisibility(rows[i], View.GONE)
                }
            }

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

            // Request code harus berbeda dari DebtWidget (0 dan 1), karena extras
            // tidak ikut diperhitungkan saat Android membandingkan PendingIntent.
            val openApp = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("tab", 1)
            rv.setOnClickPendingIntent(
                R.id.s_root,
                PendingIntent.getActivity(ctx, 10, openApp, flags)
            )

            val addIntent = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("tab", 1)
                .putExtra("add", true)
            rv.setOnClickPendingIntent(
                R.id.s_add,
                PendingIntent.getActivity(ctx, 11, addIntent, flags)
            )
            return rv
        }
    }
}
