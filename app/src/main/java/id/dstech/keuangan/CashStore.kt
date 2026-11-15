package id.dstech.keuangan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cache lokal (SharedPreferences) untuk riwayat uang tunai, dipakai UI dan widget.
 * Sumber kebenaran ada di Firestore (lihat CloudRepo).
 */
object CashStore {
    private const val PREFS = "cash"
    private const val KEY = "data"

    fun load(ctx: Context): List<CashEntry> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<CashEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                CashEntry(
                    id = o.optLong("id"),
                    amount = o.optLong("amount"),
                    note = o.optString("note"),
                    at = o.optLong("at")
                )
            )
        }
        return out.sortedWith(compareByDescending<CashEntry> { it.at }.thenByDescending { it.id })
    }

    private fun saveLocal(ctx: Context, list: List<CashEntry>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("amount", e.amount)
                    .put("note", e.note)
                    .put("at", e.at)
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
        MoneyWidget.refresh(ctx)
    }

    /** Ganti seluruh cache dengan data dari cloud (tanpa mendorong balik). */
    fun replaceAll(ctx: Context, list: List<CashEntry>) = saveLocal(ctx, list)

    /** Kosongkan cache lokal, dipakai saat keluar akun. */
    fun clear(ctx: Context) = saveLocal(ctx, emptyList())

    fun upsert(ctx: Context, entry: CashEntry) {
        val list = load(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == entry.id }
        if (idx >= 0) list[idx] = entry else list.add(entry)
        saveLocal(ctx, list)
        CloudRepo.pushCash(entry)
    }

    fun delete(ctx: Context, id: Long) {
        saveLocal(ctx, load(ctx).filter { it.id != id })
        CloudRepo.removeCash(id)
    }

    /** Saldo tunai = jumlah seluruh catatan. */
    fun total(list: List<CashEntry>): Long = list.sumOf { it.amount }
}
