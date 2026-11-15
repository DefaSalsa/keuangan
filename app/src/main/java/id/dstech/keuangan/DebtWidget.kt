package id.dstech.keuangan

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class DebtWidget : AppWidgetProvider() {

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
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, DebtWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(ctx)
            for (id in ids) mgr.updateAppWidget(id, views)
        }

        private fun fillSection(
            rv: RemoteViews,
            list: List<Debt>,
            total: Int,
            empty: Int,
            rows: IntArray,
            names: IntArray,
            amounts: IntArray
        ) {
            rv.setTextViewText(total, Fmt.rupiah(list.sumOf { it.amount }))
            rv.setViewVisibility(empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
            for (i in rows.indices) {
                if (i < list.size) {
                    rv.setViewVisibility(rows[i], View.VISIBLE)
                    rv.setTextViewText(names[i], list[i].name)
                    rv.setTextViewText(amounts[i], Fmt.rupiah(list[i].amount))
                } else {
                    rv.setViewVisibility(rows[i], View.GONE)
                }
            }
        }

        private fun build(ctx: Context): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_debt)
            val open = DebtStore.load(ctx).filter { !it.paid }

            fillSection(
                rv, open.filter { it.iOwe },
                R.id.w_h_total, R.id.w_h_empty,
                intArrayOf(R.id.w_h_r1, R.id.w_h_r2, R.id.w_h_r3),
                intArrayOf(R.id.w_h_n1, R.id.w_h_n2, R.id.w_h_n3),
                intArrayOf(R.id.w_h_a1, R.id.w_h_a2, R.id.w_h_a3)
            )
            fillSection(
                rv, open.filter { !it.iOwe },
                R.id.w_p_total, R.id.w_p_empty,
                intArrayOf(R.id.w_p_r1, R.id.w_p_r2, R.id.w_p_r3),
                intArrayOf(R.id.w_p_n1, R.id.w_p_n2, R.id.w_p_n3),
                intArrayOf(R.id.w_p_a1, R.id.w_p_a2, R.id.w_p_a3)
            )

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

            val openApp = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("tab", 0)
            rv.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(ctx, 0, openApp, flags)
            )

            val addIntent = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("tab", 0)
                .putExtra("add", true)
            rv.setOnClickPendingIntent(
                R.id.w_add,
                PendingIntent.getActivity(ctx, 1, addIntent, flags)
            )
            return rv
        }
    }
}
