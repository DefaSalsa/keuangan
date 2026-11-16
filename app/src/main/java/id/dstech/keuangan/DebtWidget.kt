package id.dstech.keuangan

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar

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

        /** Satu baris widget = satu orang: total dan janji bayar terdekat dari seluruh catatannya. */
        private class Row(val name: String, val amount: Long, val due: Long)

        private fun group(list: List<Debt>): List<Row> =
            list.groupBy { it.name.trim().lowercase().replace(Regex("\\s+"), " ") }
                .map { (_, items) ->
                    Row(
                        name = items.first().name.trim(),
                        amount = items.sumOf { it.amount },
                        due = items.filter { it.dueAt > 0L }.minOfOrNull { it.dueAt } ?: 0L
                    )
                }
                .sortedWith(
                    compareBy<Row> { if (it.due == 0L) Long.MAX_VALUE else it.due }
                        .thenByDescending { it.amount }
                )

        private fun startOfDay(ts: Long): Long {
            val c = Calendar.getInstance()
            c.timeInMillis = ts
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        private fun fillSection(
            rv: RemoteViews,
            list: List<Debt>,
            total: Int,
            empty: Int,
            rows: IntArray,
            names: IntArray,
            dues: IntArray,
            amounts: IntArray
        ) {
            rv.setTextViewText(total, Fmt.rupiah(list.sumOf { it.amount }))
            rv.setViewVisibility(empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
            val people = group(list)
            val today = startOfDay(System.currentTimeMillis())
            for (i in rows.indices) {
                if (i < people.size) {
                    val p = people[i]
                    rv.setViewVisibility(rows[i], View.VISIBLE)
                    rv.setTextViewText(names[i], p.name)
                    rv.setTextViewText(amounts[i], Fmt.rupiah(p.amount))
                    if (p.due > 0L) {
                        val days = ((startOfDay(p.due) - today) / 86_400_000L).toInt()
                        val text = when {
                            days < 0 -> "Terlambat ${-days} hari"
                            days == 0 -> "Janji bayar hari ini"
                            else -> "Janji bayar ${Fmt.date(p.due)} • $days hari lagi"
                        }
                        val color = when {
                            days <= 0 -> C.RED
                            else -> C.BLUE_LIGHT
                        }
                        rv.setTextViewText(dues[i], text)
                        rv.setTextColor(dues[i], color)
                        rv.setViewVisibility(dues[i], View.VISIBLE)
                    } else {
                        rv.setViewVisibility(dues[i], View.GONE)
                    }
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
                intArrayOf(R.id.w_h_d1, R.id.w_h_d2, R.id.w_h_d3),
                intArrayOf(R.id.w_h_a1, R.id.w_h_a2, R.id.w_h_a3)
            )
            fillSection(
                rv, open.filter { !it.iOwe },
                R.id.w_p_total, R.id.w_p_empty,
                intArrayOf(R.id.w_p_r1, R.id.w_p_r2, R.id.w_p_r3),
                intArrayOf(R.id.w_p_n1, R.id.w_p_n2, R.id.w_p_n3),
                intArrayOf(R.id.w_p_d1, R.id.w_p_d2, R.id.w_p_d3),
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
