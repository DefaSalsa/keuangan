package id.dstech.keuangan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cache lokal (SharedPreferences) supaya UI dan widget bisa baca data seketika.
 * Sumber kebenaran ada di Firestore (lihat CloudRepo): upsert/delete di sini
 * menulis ke cache lalu mendorong perubahan ke cloud.
 */
object DebtStore {
    private const val PREFS = "debts"
    private const val KEY = "data"

    fun load(ctx: Context): List<Debt> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<Debt>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Debt(
                    id = o.optLong("id"),
                    name = o.optString("name"),
                    amount = o.optLong("amount"),
                    note = o.optString("note"),
                    createdAt = o.optLong("createdAt"),
                    paid = o.optBoolean("paid", false),
                    iOwe = o.optBoolean("iOwe", true),
                    dueAt = o.optLong("dueAt", 0L)
                )
            )
        }
        return out.sortedByDescending { it.createdAt }
    }

    private fun saveLocal(ctx: Context, list: List<Debt>) {
        val arr = JSONArray()
        for (d in list) {
            arr.put(
                JSONObject()
                    .put("id", d.id)
                    .put("name", d.name)
                    .put("amount", d.amount)
                    .put("note", d.note)
                    .put("createdAt", d.createdAt)
                    .put("paid", d.paid)
                    .put("iOwe", d.iOwe)
                    .put("dueAt", d.dueAt)
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
        DebtWidget.refresh(ctx)
    }

    /** Ganti seluruh cache dengan data dari cloud (tanpa mendorong balik). */
    fun replaceAll(ctx: Context, list: List<Debt>) = saveLocal(ctx, list)

    /** Kosongkan cache lokal, dipakai saat keluar akun. */
    fun clear(ctx: Context) = saveLocal(ctx, emptyList())

    fun upsert(ctx: Context, debt: Debt) {
        val list = load(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == debt.id }
        if (idx >= 0) list[idx] = debt else list.add(debt)
        saveLocal(ctx, list)
        CloudRepo.push(debt)
    }

    fun delete(ctx: Context, id: Long) {
        saveLocal(ctx, load(ctx).filter { it.id != id })
        CloudRepo.remove(id)
    }

    /** Pair(total saya berhutang, total piutang saya) — hanya yang belum lunas. */
    fun totals(list: List<Debt>): Pair<Long, Long> {
        val open = list.filter { !it.paid }
        val owe = open.filter { it.iOwe }.sumOf { it.amount }
        val owed = open.filter { !it.iOwe }.sumOf { it.amount }
        return Pair(owe, owed)
    }
}
