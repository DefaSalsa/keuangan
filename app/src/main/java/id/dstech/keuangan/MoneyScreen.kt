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
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Layar "Uang": satu saldo uang tunai di luar tabungan, dengan riwayat uang masuk dan keluar.
 * Setiap catatan menyimpan keterangan (dari mana / dipakai untuk apa).
 */
class MoneyScreen(private val act: Activity) {

    private var items: List<CashEntry> = emptyList()

    private lateinit var adapter: EntryAdapter
    private lateinit var tvTotal: TextView
    private lateinit var tvWithSavings: TextView
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

        tvTotal = act.tv("Rp 0", 18f, C.WHITE, true)
        tvWithSavings = act.tv("Rp 0", 18f, C.WHITE, true)
        val sum = LinearLayout(act)
        sum.orientation = LinearLayout.HORIZONTAL
        sum.addView(act.summaryCard("Total tunai", tvTotal), act.lp(0, WRAP_CONTENT, 1f, r = 6))
        sum.addView(act.summaryCard("Tunai + tabungan", tvWithSavings), act.lp(0, WRAP_CONTENT, 1f, l = 6))
        col.addView(sum, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 4, b = 12))

        col.addView(act.tv("Riwayat", 13f, C.MUTED, true), act.lp(WRAP_CONTENT, WRAP_CONTENT, t = 4, b = 8))

        val box = FrameLayout(act)
        val lv = ListView(act)
        lv.divider = null
        lv.dividerHeight = 0
        lv.selector = ColorDrawable(Color.TRANSPARENT)
        lv.clipToPadding = false
        lv.setPadding(0, 0, 0, dp(170))
        lv.isVerticalScrollBarEnabled = false
        adapter = EntryAdapter()
        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, pos, _ ->
            items.getOrNull(pos)?.let { showOptions(it) }
        }
        tvEmpty = act.tv("Belum ada riwayat tunai.\nKetuk + untuk mencatat uang masuk atau keluar.", 14f, C.MUTED)
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
        val all = CashStore.load(act)
        val total = CashStore.total(all)
        val savings = GoalStore.totals(GoalStore.load(act)).first
        tvTotal.animateRupiah(total)
        tvWithSavings.animateRupiah(total + savings)
        items = all // terbaru di atas
        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Dipanggil dari tombol + dan widget: membuka form catat tunai (masuk atau keluar). */
    fun add() = showEntryDialog()

    private fun title(e: CashEntry): String =
        e.note.ifBlank { if (e.amount >= 0L) "Uang masuk" else "Uang keluar" }

    private fun signed(e: CashEntry): String =
        (if (e.amount >= 0L) "+ " else "- ") + Fmt.rupiah(abs(e.amount))

    private inner class EntryAdapter : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val e = items[position]

            val outer = LinearLayout(act)
            outer.setPadding(0, 0, 0, dp(10))

            val card = LinearLayout(act)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.setPadding(dp(16), dp(14), dp(16), dp(14))
            card.background = act.round(C.SURFACE, 16, C.STROKE)

            val left = LinearLayout(act)
            left.orientation = LinearLayout.VERTICAL
            left.addView(act.tv(title(e), 15f, C.WHITE, true))
            left.addView(
                act.tv(Fmt.date(e.at), 12f, C.MUTED),
                act.lp(WRAP_CONTENT, WRAP_CONTENT, t = 2)
            )
            card.addView(left, act.lp(0, WRAP_CONTENT, 1f))

            card.addView(
                act.tv(signed(e), 15f, if (e.amount >= 0L) C.GREEN else C.RED, true),
                act.lp(WRAP_CONTENT, WRAP_CONTENT, l = 12)
            )

            card.pressFeedback()
            outer.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            outer.rise(position)
            return outer
        }
    }

    // ------------------------------------------------------------ Dialog

    private fun showOptions(e: CashEntry) {
        act.darkDialog(
            act.dialogBuilder()
                .setTitle("${title(e)} • ${signed(e)}")
                .setItems(arrayOf("Ubah", "Hapus")) { _, which ->
                    if (which == 0) showEditForm(e) else confirmDelete(e)
                }
        ).show()
    }

    private fun confirmDelete(e: CashEntry) {
        act.darkDialog(
            act.dialogBuilder()
                .setTitle("Hapus catatan?")
                .setMessage("${title(e)} • ${signed(e)} • ${Fmt.date(e.at)}. Saldo tunai ikut menyesuaikan.")
                .setPositiveButton("Hapus") { _, _ ->
                    val rest = CashStore.load(act).filter { it.id != e.id }
                    if (CashStore.total(rest) < 0L) {
                        Toast.makeText(act, "Gagal: saldo tunai jadi minus.", Toast.LENGTH_LONG).show()
                    } else {
                        CashStore.delete(act, e.id)
                        refresh()
                    }
                }
                .setNegativeButton("Batal", null)
        ).show()
    }

    /** Form catat tunai: jumlah, keterangan, lalu pilih uang masuk atau keluar. */
    private fun showEntryDialog() {
        val form = LinearLayout(act)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(12), dp(20), dp(8))

        val etAmount = act.field("Jumlah (Rp)", InputType.TYPE_CLASS_NUMBER)
        val etNote = act.field(
            "Dari mana (mis. gaji, saldo awal)",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )
        form.addView(etAmount)
        form.addView(etNote, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 8))

        val group = RadioGroup(act)
        group.orientation = RadioGroup.VERTICAL
        val rbIn = act.radio("Uang masuk (tambah)")
        val rbOut = act.radio("Uang keluar (kurangi)")
        group.addView(rbIn)
        group.addView(rbOut)
        rbIn.isChecked = true
        group.setOnCheckedChangeListener { _, checked ->
            etNote.hint = if (checked == rbIn.id) "Dari mana (mis. gaji, saldo awal)"
            else "Dipakai untuk apa (mis. makan siang)"
        }
        form.addView(group, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 14))
        form.addView(
            act.tv("Saldo tunai: ${Fmt.rupiah(CashStore.total(CashStore.load(act)))}", 12f, C.MUTED),
            act.lp(MATCH_PARENT, WRAP_CONTENT, t = 10)
        )

        val dlg = act.darkDialog(
            act.dialogBuilder()
                .setTitle("Catat tunai")
                .setView(form)
                .setPositiveButton("Simpan", null)
                .setNegativeButton("Batal", null)
        )
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val deposit = rbIn.isChecked
            val amount = etAmount.text.toString().filter { it.isDigit() }.toLongOrNull()
            if (amount == null || amount <= 0L) {
                etAmount.error = "Masukkan jumlah yang valid"
                return@setOnClickListener
            }
            val note = etNote.text.toString().trim()
            if (!deposit && note.isEmpty()) {
                etNote.error = "Isi untuk apa uangnya dipakai"
                return@setOnClickListener
            }
            if (!deposit && amount > CashStore.total(CashStore.load(act))) {
                etAmount.error = "Melebihi saldo tunai"
                return@setOnClickListener
            }
            val now = System.currentTimeMillis()
            CashStore.upsert(
                act,
                CashEntry(id = now, amount = if (deposit) amount else -amount, note = note, at = now)
            )
            refresh()
            dlg.dismiss()
        }
    }

    private fun showEditForm(e: CashEntry) {
        val form = LinearLayout(act)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(12), dp(20), dp(8))

        val etAmount = act.field("Jumlah (Rp)", InputType.TYPE_CLASS_NUMBER)
        val etNote = act.field("Keterangan", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        etAmount.setText(abs(e.amount).toString())
        etNote.setText(e.note)
        form.addView(etAmount)
        form.addView(etNote, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 8))

        val group = RadioGroup(act)
        group.orientation = RadioGroup.VERTICAL
        val rbIn = act.radio("Uang masuk")
        val rbOut = act.radio("Uang keluar")
        group.addView(rbIn)
        group.addView(rbOut)
        if (e.amount >= 0L) rbIn.isChecked = true else rbOut.isChecked = true
        form.addView(group, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 14))

        val dlg = act.darkDialog(
            act.dialogBuilder()
                .setTitle("Ubah catatan")
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
            val note = etNote.text.toString().trim()
            if (rbOut.isChecked && note.isEmpty()) {
                etNote.error = "Isi untuk apa uangnya dipakai"
                return@setOnClickListener
            }
            val fixed = e.copy(amount = if (rbIn.isChecked) amount else -amount, note = note)
            val newTotal = CashStore.total(CashStore.load(act).map { if (it.id == e.id) fixed else it })
            if (newTotal < 0L) {
                Toast.makeText(act, "Gagal: saldo tunai jadi minus.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            CashStore.upsert(act, fixed)
            refresh()
            dlg.dismiss()
        }
    }
}
