package id.dstech.keuangan

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/** Layar "Tabungan": banyak tujuan, masing-masing dengan target, saldo, dan riwayat setoran. */
class SavingsScreen(private val act: Activity) {

    private var items: List<Goal> = emptyList()

    private lateinit var adapter: GoalAdapter
    private lateinit var tvSaved: TextView
    private lateinit var tvTarget: TextView
    private lateinit var tvEmpty: TextView

    val view: View = build()

    private fun dp(v: Int): Int = act.dp(v)

    // ---------------------------------------------------------------- UI

    private fun build(): View {
        val root = FrameLayout(act)

        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(8), dp(18), 0)
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        tvSaved = act.tv("Rp 0", 18f, C.WHITE, true)
        tvTarget = act.tv("Rp 0", 18f, C.WHITE, true)
        val sum = LinearLayout(act)
        sum.orientation = LinearLayout.HORIZONTAL
        sum.addView(act.summaryCard("Terkumpul", tvSaved), act.lp(0, WRAP_CONTENT, 1f, r = 6))
        sum.addView(act.summaryCard("Target", tvTarget), act.lp(0, WRAP_CONTENT, 1f, l = 6))
        col.addView(sum, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 4, b = 14))

        val box = FrameLayout(act)
        val lv = ListView(act)
        lv.divider = null
        lv.dividerHeight = 0
        lv.selector = ColorDrawable(Color.TRANSPARENT)
        lv.clipToPadding = false
        lv.setPadding(0, 0, 0, dp(170))
        lv.isVerticalScrollBarEnabled = false
        adapter = GoalAdapter()
        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, pos, _ ->
            items.getOrNull(pos)?.let { showOptions(it) }
        }
        tvEmpty = act.tv("Belum ada tujuan tabungan.\nKetuk + untuk menambah.", 14f, C.MUTED)
        tvEmpty.gravity = Gravity.CENTER
        box.addView(lv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        box.addView(
            tvEmpty,
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
        )
        col.addView(box, act.lp(MATCH_PARENT, 0, 1f))

        root.addView(act.fab { add() }, act.fabParams())
        return root
    }

    fun refresh() {
        val all = GoalStore.load(act)
        val (saved, target) = GoalStore.totals(all)
        tvSaved.animateRupiah(saved)
        tvTarget.animateRupiah(target)
        items = all.sortedBy { it.reached } // yang belum tercapai di atas
        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    fun add() = showGoalForm(null)

    private inner class GoalAdapter : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val g = items[position]
            val done = g.reached

            val outer = LinearLayout(act)
            outer.setPadding(0, 0, 0, dp(10))

            val card = LinearLayout(act)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(16), dp(14), dp(16), dp(14))
            card.background = act.round(C.SURFACE, 16, if (done) C.GREEN else C.BLUE_DIM)

            val top = LinearLayout(act)
            top.orientation = LinearLayout.HORIZONTAL
            top.gravity = Gravity.CENTER_VERTICAL
            top.addView(act.tv(g.name, 16f, C.WHITE, true), act.lp(0, WRAP_CONTENT, 1f))
            top.addView(
                act.tv("${g.percent}%", 15f, if (done) C.GREEN else C.BLUE_LIGHT, true),
                act.lp(WRAP_CONTENT, WRAP_CONTENT, l = 8)
            )
            card.addView(top)

            card.addView(
                act.progressBar(g.percent, if (done) C.GREEN else C.BLUE, g.id),
                act.lp(MATCH_PARENT, dp(8), t = 10)
            )

            val bottom = LinearLayout(act)
            bottom.orientation = LinearLayout.HORIZONTAL
            bottom.addView(
                act.tv("${Fmt.rupiah(g.saved)} dari ${Fmt.rupiah(g.target)}", 12f, C.MUTED),
                act.lp(0, WRAP_CONTENT, 1f)
            )
            if (done) {
                bottom.addView(act.tv("Tercapai", 12f, C.GREEN, true))
            } else {
                bottom.addView(act.tv("Kurang ${Fmt.rupiah(g.target - g.saved)}", 12f, C.NOTE))
            }
            card.addView(bottom, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 8))

            if (!done) {
                val eta = g.etaText()
                val plan = if (eta != null) {
                    "${Fmt.rupiah(g.daily)} per hari • $eta"
                } else {
                    "Atur nominal per hari untuk melihat estimasi"
                }
                card.addView(
                    act.tv(plan, 12f, if (eta != null) C.BLUE_LIGHT else C.MUTED),
                    act.lp(MATCH_PARENT, WRAP_CONTENT, t = 4)
                )
            }

            card.pressFeedback()
            outer.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            outer.rise(position)
            return outer
        }
    }

    // ------------------------------------------------------------ Dialog

    private fun showOptions(g: Goal) {
        val opts = arrayOf("Setor", "Tarik", "Riwayat", "Ubah", "Hapus")
        act.darkDialog(
            act.dialogBuilder().setTitle(g.name).setItems(opts) { _, which ->
                when (which) {
                    0 -> showAmountDialog(g, true)
                    1 -> showAmountDialog(g, false)
                    2 -> showHistory(g)
                    3 -> showGoalForm(g)
                    else -> confirmDelete(g)
                }
            }
        ).show()
    }

    private fun confirmDelete(g: Goal) {
        act.darkDialog(
            act.dialogBuilder()
                .setTitle("Hapus tujuan?")
                .setMessage("${g.name} • terkumpul ${Fmt.rupiah(g.saved)}. Riwayat setoran ikut terhapus.")
                .setPositiveButton("Hapus") { _, _ ->
                    GoalStore.delete(act, g.id)
                    refresh()
                }
                .setNegativeButton("Batal", null)
        ).show()
    }

    private fun showHistory(g: Goal) {
        // Simpan urutan asli supaya entri yang diketuk bisa dikenali, walau daftar ditampilkan terbaru di atas
        val sorted = g.history.sortedByDescending { it.at }
        val b = act.dialogBuilder().setTitle("Riwayat ${g.name}")
        if (sorted.isEmpty()) {
            b.setMessage("Belum ada setoran.")
        } else {
            val lines = sorted.map { h ->
                val sign = if (h.amount >= 0L) "+ " else "- "
                val note = if (h.note.isBlank()) "" else "\n${h.note}"
                "$sign${Fmt.rupiah(abs(h.amount))} • ${Fmt.date(h.at)}$note"
            }.toTypedArray()
            b.setItems(lines) { _, which -> showEntryOptions(g, sorted[which]) }
        }
        b.setNegativeButton("Tutup", null)
        act.darkDialog(b).show()
    }

    private fun showEntryOptions(g: Goal, entry: Deposit) {
        val label = (if (entry.amount >= 0L) "Setoran " else "Penarikan ") + Fmt.rupiah(abs(entry.amount))
        act.darkDialog(
            act.dialogBuilder().setTitle(label).setItems(arrayOf("Ubah", "Hapus")) { _, which ->
                if (which == 0) showEntryForm(g, entry) else confirmDeleteEntry(g, entry)
            }.setNegativeButton("Kembali") { _, _ -> showHistory(g) }
        ).show()
    }

    /** Ambil data terbaru tujuan ini, lalu terapkan perubahan riwayat. Ditolak bila saldo jadi minus. */
    private fun applyHistory(g: Goal, change: (List<Deposit>) -> List<Deposit>): Goal? {
        val latest = GoalStore.load(act).firstOrNull { it.id == g.id } ?: g
        val updated = latest.copy(history = change(latest.history))
        if (updated.saved < 0L) {
            Toast.makeText(act, "Gagal: saldo tabungan jadi minus.", Toast.LENGTH_LONG).show()
            return null
        }
        GoalStore.upsert(act, updated)
        refresh()
        return updated
    }

    private fun showEntryForm(g: Goal, entry: Deposit) {
        val form = LinearLayout(act)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(12), dp(20), dp(8))

        val etAmount = act.field("Jumlah (Rp)", InputType.TYPE_CLASS_NUMBER)
        val etNote = act.field("Catatan (opsional)", InputType.TYPE_CLASS_TEXT)
        etAmount.setText(abs(entry.amount).toString())
        etNote.setText(entry.note)
        form.addView(etAmount)
        form.addView(etNote, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 8))

        val group = RadioGroup(act)
        group.orientation = RadioGroup.VERTICAL
        val rbIn = act.radio("Setoran")
        val rbOut = act.radio("Penarikan")
        group.addView(rbIn)
        group.addView(rbOut)
        if (entry.amount >= 0L) rbIn.isChecked = true else rbOut.isChecked = true
        form.addView(group, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 14))

        val dlg = act.darkDialog(
            act.dialogBuilder()
                .setTitle("Ubah entri")
                .setView(form)
                .setPositiveButton("Simpan", null)
                .setNegativeButton("Batal", null)
        )
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val amount = etAmount.text.toString().filter { it.isDigit() }.toLongOrNull()
            if (amount == null || amount <= 0L) {
                etAmount.error = "Masukkan jumlah yang valid"
                return@setOnClickListener
            }
            val fixed = Deposit(
                amount = if (rbIn.isChecked) amount else -amount,
                note = etNote.text.toString().trim(),
                at = entry.at // tanggal asli dipertahankan
            )
            val updated = applyHistory(g) { list ->
                val idx = list.indexOf(entry)
                if (idx < 0) list else list.toMutableList().also { it[idx] = fixed }
            }
            if (updated != null) {
                dlg.dismiss()
                showHistory(updated)
            }
        }
    }

    private fun confirmDeleteEntry(g: Goal, entry: Deposit) {
        val label = (if (entry.amount >= 0L) "Setoran " else "Penarikan ") + Fmt.rupiah(abs(entry.amount))
        act.darkDialog(
            act.dialogBuilder()
                .setTitle("Hapus entri?")
                .setMessage("$label • ${Fmt.date(entry.at)}. Saldo tabungan ikut menyesuaikan.")
                .setPositiveButton("Hapus") { _, _ ->
                    val updated = applyHistory(g) { list ->
                        val idx = list.indexOf(entry)
                        if (idx < 0) list else list.toMutableList().also { it.removeAt(idx) }
                    }
                    if (updated != null) showHistory(updated)
                }
                .setNegativeButton("Batal", null)
        ).show()
    }

    private fun showAmountDialog(g: Goal, deposit: Boolean) {
        val form = LinearLayout(act)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(12), dp(20), dp(8))

        val etAmount = act.field("Jumlah (Rp)", InputType.TYPE_CLASS_NUMBER)
        val etNote = act.field("Catatan (opsional)", InputType.TYPE_CLASS_TEXT)
        form.addView(etAmount)
        form.addView(etNote, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 8))
        if (!deposit) {
            form.addView(
                act.tv("Saldo: ${Fmt.rupiah(g.saved)}", 12f, C.MUTED),
                act.lp(MATCH_PARENT, WRAP_CONTENT, t = 10)
            )
        }

        val dlg = act.darkDialog(
            act.dialogBuilder()
                .setTitle(if (deposit) "Setor ke ${g.name}" else "Tarik dari ${g.name}")
                .setView(form)
                .setPositiveButton(if (deposit) "Setor" else "Tarik", null)
                .setNegativeButton("Batal", null)
        )
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val amount = etAmount.text.toString().filter { it.isDigit() }.toLongOrNull()
            if (amount == null || amount <= 0L) {
                etAmount.error = "Masukkan jumlah yang valid"
                return@setOnClickListener
            }
            val latest = GoalStore.load(act).firstOrNull { it.id == g.id } ?: g
            if (!deposit && amount > latest.saved) {
                etAmount.error = "Melebihi saldo tabungan"
                return@setOnClickListener
            }
            val entry = Deposit(
                amount = if (deposit) amount else -amount,
                note = etNote.text.toString().trim(),
                at = System.currentTimeMillis()
            )
            GoalStore.upsert(act, latest.copy(history = latest.history + entry))
            refresh()
            dlg.dismiss()
        }
    }

    private fun showGoalForm(existing: Goal?) {
        val form = LinearLayout(act)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(12), dp(20), dp(8))

        val etName = act.field("Nama tujuan (mis. Liburan)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val etTarget = act.field("Target (Rp)", InputType.TYPE_CLASS_NUMBER)
        val etDaily = act.field("Nabung per hari (Rp, opsional)", InputType.TYPE_CLASS_NUMBER)
        val etInitial = act.field("Setoran awal (opsional)", InputType.TYPE_CLASS_NUMBER)
        if (existing != null) {
            etName.setText(existing.name)
            etTarget.setText(existing.target.toString())
            if (existing.daily > 0L) etDaily.setText(existing.daily.toString())
        }
        form.addView(etName)
        form.addView(etTarget, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 8))
        form.addView(etDaily, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 8))
        if (existing == null) {
            form.addView(etInitial, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 8))
        }

        val scroll = ScrollView(act)
        scroll.addView(form)

        val dlg = act.darkDialog(
            act.dialogBuilder()
                .setTitle(if (existing == null) "Tujuan baru" else "Ubah tujuan")
                .setView(scroll)
                .setPositiveButton("Simpan", null)
                .setNegativeButton("Batal", null)
        )
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = etName.text.toString().trim()
            val target = etTarget.text.toString().filter { it.isDigit() }.toLongOrNull()
            if (name.isEmpty()) {
                etName.error = "Nama wajib diisi"
                return@setOnClickListener
            }
            if (target == null || target <= 0L) {
                etTarget.error = "Masukkan target yang valid"
                return@setOnClickListener
            }
            val now = System.currentTimeMillis()
            val daily = etDaily.text.toString().filter { it.isDigit() }.toLongOrNull() ?: 0L
            if (existing != null) {
                GoalStore.upsert(act, existing.copy(name = name, target = target, daily = daily))
            } else {
                val initial = etInitial.text.toString().filter { it.isDigit() }.toLongOrNull() ?: 0L
                val history = if (initial > 0L) listOf(Deposit(initial, "Setoran awal", now)) else emptyList()
                GoalStore.upsert(act, Goal(id = now, name = name, target = target, createdAt = now, history = history, daily = daily))
            }
            refresh()
            dlg.dismiss()
        }
    }
}
