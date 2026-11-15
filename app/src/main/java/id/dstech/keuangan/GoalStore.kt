package id.dstech.keuangan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cache lokal (SharedPreferences) untuk tujuan tabungan, dipakai UI dan widget.
 * Sumber kebenaran ada di Firestore (lihat CloudRepo).
 */
object GoalStore {
    private const val PREFS = "goals"
    private const val KEY = "data"

    fun load(ctx: Context): List<Goal> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<Goal>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val hist = ArrayList<Deposit>()
            val ha = o.optJSONArray("history")
            if (ha != null) {
                for (j in 0 until ha.length()) {
                    val h = ha.optJSONObject(j) ?: continue
                    hist.add(
                        Deposit(
                            amount = h.optLong("amount"),
                            note = h.optString("note"),
                            at = h.optLong("at")
                        )
                    )
                }
            }
            out.add(
                Goal(
                    id = o.optLong("id"),
                    name = o.optString("name"),
                    target = o.optLong("target"),
                    createdAt = o.optLong("createdAt"),
                    history = hist,
                    daily = o.optLong("daily")
                )
            )
        }
        return out.sortedByDescending { it.createdAt }
    }

    private fun saveLocal(ctx: Context, list: List<Goal>) {
        val arr = JSONArray()
        for (g in list) {
            val ha = JSONArray()
            for (h in g.history) {
                ha.put(
                    JSONObject()
                        .put("amount", h.amount)
                        .put("note", h.note)
                        .put("at", h.at)
                )
            }
            arr.put(
                JSONObject()
                    .put("id", g.id)
                    .put("name", g.name)
                    .put("target", g.target)
                    .put("createdAt", g.createdAt)
                    .put("daily", g.daily)
                    .put("history", ha)
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
        SavingsWidget.refresh(ctx)
    }

    /** Ganti seluruh cache dengan data dari cloud (tanpa mendorong balik). */
    fun replaceAll(ctx: Context, list: List<Goal>) = saveLocal(ctx, list)

    /** Kosongkan cache lokal, dipakai saat keluar akun. */
    fun clear(ctx: Context) = saveLocal(ctx, emptyList())

    fun upsert(ctx: Context, goal: Goal) {
        val list = load(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == goal.id }
        if (idx >= 0) list[idx] = goal else list.add(goal)
        saveLocal(ctx, list)
        CloudRepo.pushGoal(goal)
    }

    fun delete(ctx: Context, id: Long) {
        saveLocal(ctx, load(ctx).filter { it.id != id })
        CloudRepo.removeGoal(id)
    }

    /** Pair(total terkumpul, total target) seluruh tujuan. */
    fun totals(list: List<Goal>): Pair<Long, Long> =
        Pair(list.sumOf { it.saved }, list.sumOf { it.target })
}
