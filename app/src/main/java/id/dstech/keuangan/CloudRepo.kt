package id.dstech.keuangan

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source

/**
 * Sinkronisasi ke Firestore:
 *   users/{uid}/debts/{id}   hutang dan piutang
 *   users/{uid}/goals/{id}   tujuan tabungan (riwayat setoran disimpan di dalam dokumen)
 *   users/{uid}/cash/{id}    riwayat uang tunai (masuk dan keluar) di luar tabungan
 * Firestore menyimpan antrean tulis saat offline dan mengirimnya otomatis begitu online.
 */
object CloudRepo {

    private fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    private fun col(name: String): CollectionReference? {
        val u = uid() ?: return null
        return FirebaseFirestore.getInstance()
            .collection("users").document(u).collection(name)
    }

    /**
     * Sinkron manual: ambil seluruh data langsung dari server Firestore (bukan dari cache),
     * lalu ganti cache lokal dengan hasilnya. onDone(false) bila gagal (mis. tidak ada koneksi).
     */
    fun syncNow(ctx: Context, onDone: (Boolean) -> Unit) {
        val d = col("debts")
        val g = col("goals")
        val c = col("cash")
        if (d == null || g == null || c == null) {
            onDone(false)
            return
        }
        val app = ctx.applicationContext
        d.get(Source.SERVER).addOnSuccessListener { ds ->
            g.get(Source.SERVER).addOnSuccessListener { gs ->
                c.get(Source.SERVER).addOnSuccessListener { cs ->
                    DebtStore.replaceAll(app, ds.documents.mapNotNull { parse(it) })
                    GoalStore.replaceAll(app, gs.documents.mapNotNull { parseGoal(it) })
                    CashStore.replaceAll(app, cs.documents.mapNotNull { parseCash(it) })
                    onDone(true)
                }.addOnFailureListener { onDone(false) }
            }.addOnFailureListener { onDone(false) }
        }.addOnFailureListener { onDone(false) }
    }

    // ------------------------------------------------------------ Hutang

    fun push(d: Debt) {
        col("debts")?.document(d.id.toString())?.set(
            hashMapOf<String, Any>(
                "name" to d.name,
                "amount" to d.amount,
                "note" to d.note,
                "createdAt" to d.createdAt,
                "paid" to d.paid,
                "iOwe" to d.iOwe,
                "dueAt" to d.dueAt
            )
        )
    }

    fun remove(id: Long) {
        col("debts")?.document(id.toString())?.delete()
    }

    private fun parse(doc: DocumentSnapshot): Debt? {
        val id = doc.id.toLongOrNull() ?: return null
        return Debt(
            id = id,
            name = doc.getString("name") ?: "",
            amount = doc.getLong("amount") ?: 0L,
            note = doc.getString("note") ?: "",
            createdAt = doc.getLong("createdAt") ?: id,
            paid = doc.getBoolean("paid") ?: false,
            iOwe = doc.getBoolean("iOwe") ?: true,
            dueAt = doc.getLong("dueAt") ?: 0L
        )
    }

    /**
     * Pantau perubahan hutang di cloud; setiap ada perubahan, cache lokal diganti lalu onChange()
     * dipanggil (di main thread). Data lama dari versi lokal-saja diunggah sekali saat cloud masih kosong.
     */
    fun listen(ctx: Context, onChange: () -> Unit): ListenerRegistration? {
        val c = col("debts") ?: return null
        val u = uid() ?: return null
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences("cloud", Context.MODE_PRIVATE)
        val flag = "migrated_$u"

        return c.addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            val list = snap.documents.mapNotNull { parse(it) }
            val fromCache = snap.metadata.isFromCache
            val migrated = prefs.getBoolean(flag, false)

            // Cache kosong sebelum data server tiba: jangan timpa data lokal.
            if (fromCache && list.isEmpty() && !migrated) return@addSnapshotListener

            if (!fromCache && !migrated) {
                prefs.edit().putBoolean(flag, true).apply()
                val local = DebtStore.load(app)
                if (list.isEmpty() && local.isNotEmpty()) {
                    local.forEach { push(it) } // snapshot berikutnya akan memuat hasilnya
                    return@addSnapshotListener
                }
            }

            DebtStore.replaceAll(app, list)
            onChange()
        }
    }

    // ---------------------------------------------------------- Tabungan

    fun pushGoal(g: Goal) {
        val hist = g.history.map {
            hashMapOf<String, Any>(
                "amount" to it.amount,
                "note" to it.note,
                "at" to it.at
            )
        }
        col("goals")?.document(g.id.toString())?.set(
            hashMapOf<String, Any>(
                "name" to g.name,
                "target" to g.target,
                "createdAt" to g.createdAt,
                "daily" to g.daily,
                "icon" to g.icon,
                "history" to hist
            )
        )
    }

    fun removeGoal(id: Long) {
        col("goals")?.document(id.toString())?.delete()
    }

    private fun parseGoal(doc: DocumentSnapshot): Goal? {
        val id = doc.id.toLongOrNull() ?: return null
        val raw = doc.get("history") as? List<*> ?: emptyList<Any>()
        val hist = ArrayList<Deposit>()
        for (item in raw) {
            val m = item as? Map<*, *> ?: continue
            hist.add(
                Deposit(
                    amount = (m["amount"] as? Number)?.toLong() ?: 0L,
                    note = (m["note"] as? String) ?: "",
                    at = (m["at"] as? Number)?.toLong() ?: 0L
                )
            )
        }
        return Goal(
            id = id,
            name = doc.getString("name") ?: "",
            target = doc.getLong("target") ?: 0L,
            createdAt = doc.getLong("createdAt") ?: id,
            history = hist,
            daily = doc.getLong("daily") ?: 0L,
            icon = doc.getString("icon") ?: ""
        )
    }

    /** Sama seperti listen(), tetapi untuk tujuan tabungan. */
    fun listenGoals(ctx: Context, onChange: () -> Unit): ListenerRegistration? {
        val c = col("goals") ?: return null
        val u = uid() ?: return null
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences("cloud", Context.MODE_PRIVATE)
        val flag = "migrated_goals_$u"

        return c.addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            val list = snap.documents.mapNotNull { parseGoal(it) }
            val fromCache = snap.metadata.isFromCache
            val migrated = prefs.getBoolean(flag, false)

            if (fromCache && list.isEmpty() && !migrated) return@addSnapshotListener

            if (!fromCache && !migrated) {
                prefs.edit().putBoolean(flag, true).apply()
                val local = GoalStore.load(app)
                if (list.isEmpty() && local.isNotEmpty()) {
                    local.forEach { pushGoal(it) }
                    return@addSnapshotListener
                }
            }

            GoalStore.replaceAll(app, list)
            onChange()
        }
    }

    // -------------------------------------------------------------- Uang

    fun pushCash(e: CashEntry) {
        col("cash")?.document(e.id.toString())?.set(
            hashMapOf<String, Any>(
                "amount" to e.amount,
                "note" to e.note,
                "at" to e.at
            )
        )
    }

    fun removeCash(id: Long) {
        col("cash")?.document(id.toString())?.delete()
    }

    private fun parseCash(doc: DocumentSnapshot): CashEntry? {
        val id = doc.id.toLongOrNull() ?: return null
        return CashEntry(
            id = id,
            amount = doc.getLong("amount") ?: 0L,
            note = doc.getString("note") ?: "",
            at = doc.getLong("at") ?: id
        )
    }

    /** Sama seperti listen(), tetapi untuk riwayat uang tunai. */
    fun listenCash(ctx: Context, onChange: () -> Unit): ListenerRegistration? {
        val c = col("cash") ?: return null
        val u = uid() ?: return null
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences("cloud", Context.MODE_PRIVATE)
        val flag = "migrated_cash_$u"

        return c.addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            val list = snap.documents.mapNotNull { parseCash(it) }
            val fromCache = snap.metadata.isFromCache
            val migrated = prefs.getBoolean(flag, false)

            if (fromCache && list.isEmpty() && !migrated) return@addSnapshotListener

            if (!fromCache && !migrated) {
                prefs.edit().putBoolean(flag, true).apply()
                val local = CashStore.load(app)
                if (list.isEmpty() && local.isNotEmpty()) {
                    local.forEach { pushCash(it) }
                    return@addSnapshotListener
                }
            }

            CashStore.replaceAll(app, list)
            onChange()
        }
    }
}
