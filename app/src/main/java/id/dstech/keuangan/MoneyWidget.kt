package id.dstech.keuangan

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlin.math.abs

/** Widget terpisah untuk uang tunai di luar tabungan: saldo dan beberapa catatan terakhir. */
class MoneyWidget : AppWidgetProvider() {

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
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, MoneyWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(ctx)
            for (id in ids) mgr.updateAppWidget(id, views)
        }

        private fun build(ctx: Context): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_money)
            val all = CashStore.load(ctx) // terbaru di atas
            val cash = CashStore.total(all)
            val savings = GoalStore.totals(GoalStore.load(ctx)).first
            rv.setTextViewText(R.id.m_total, Fmt.rupiah(cash))
            rv.setTextViewText(R.id.m_all, Fmt.rupiah(cash + savings))

            val shown = all.take(4)
            rv.setViewVisibility(R.id.m_empty, if (shown.isEmpty()) View.VISIBLE else View.GONE)

            val rows = intArrayOf(R.id.m_r1, R.id.m_r2, R.id.m_r3, R.id.m_r4)
            val names = intArrayOf(R.id.m_n1, R.id.m_n2, R.id.m_n3, R.id.m_n4)
            val amounts = intArrayOf(R.id.m_a1, R.id.m_a2, R.id.m_a3, R.id.m_a4)

            for (i in rows.indices) {
                if (i < shown.size) {
                    val e = shown[i]
                    rv.setViewVisibility(rows[i], View.VISIBLE)
                    rv.setTextViewText(
                        names[i],
                        e.note.ifBlank { if (e.amount >= 0L) "Uang masuk" else "Uang keluar" }
                    )
                    rv.setTextViewText(
                        amounts[i],
                        (if (e.amount >= 0L) "+ " else "- ") + Fmt.rupiah(abs(e.amount))
                    )
                    rv.setTextColor(amounts[i], if (e.amount >= 0L) C.GREEN else C.RED)
                } else {
                    rv.setViewVisibility(rows[i], View.GONE)
                }
            }

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

            // Request code unik per widget (DebtWidget 0-1, SavingsWidget 10-11), karena extras
            // tidak ikut diperhitungkan saat Android membandingkan PendingIntent.
            val openApp = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("tab", 2)
            rv.setOnClickPendingIntent(
                R.id.m_root,
                PendingIntent.getActivity(ctx, 20, openApp, flags)
            )

            val addIntent = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("tab", 2)
                .putExtra("add", true)
            rv.setOnClickPendingIntent(
                R.id.m_add,
                PendingIntent.getActivity(ctx, 21, addIntent, flags)
            )
            return rv
        }
    }
}
