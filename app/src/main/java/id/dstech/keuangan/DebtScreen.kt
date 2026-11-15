package id.dstech.keuangan

import android.app.Activity
import android.graphics.Color
import android.graphics.Paint
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

/** Layar "Hutang Piutang": ringkasan, filter, daftar catatan, dan formulir. */
class DebtScreen(private val act: Activity) {

    private var filter = 0 // 0 = semua, 1 = hutang, 2 = piutang
    private var items: List<Debt> = emptyList()
    private val tabViews = ArrayList<TextView>()

    private lateinit var adapter: DebtAdapter
    private lateinit var tvOwe: TextView
    private lateinit var tvOwed: TextView
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

        tvOwe = act.tv("Rp 0", 18f, C.RED, true)
        tvOwed = act.tv("Rp 0", 18f, C.GREEN, true)
        val sum = LinearLayout(act)
        sum.orientation = LinearLayout.HORIZONTAL
        sum.addView(act.summaryCard("Hutang", tvOwe), act.lp(0, WRAP_CONTENT, 1f, r = 6))
        sum.addView(act.summaryCard("Piutang", tvOwed), act.lp(0, WRAP_CONTENT, 1f, l = 6))
        col.addView(sum, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 4))

        val tabs = LinearLayout(act)
        tabs.orientation = LinearLayout.HORIZONTAL
        listOf("Semua", "Hutang", "Piutang").forEachIndexed { i, label ->
            val t = act.tv(label, 13f, C.MUTED, true)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(10), 0, dp(10))
            t.pressFeedback()
            t.setOnClickListener {
                filter = i
                refresh()
            }
            tabViews.add(t)
            tabs.addView(t, act.lp(0, WRAP_CONTENT, 1f, l = if (i == 0) 0 else 8))
        }
        col.addView(tabs, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 16, b = 12))

        val box = FrameLayout(act)
        val lv = ListView(act)
        lv.divider = null
        lv.dividerHeight = 0
        lv.selector = ColorDrawable(Color.TRANSPARENT)
        lv.clipToPadding = false
        lv.setPadding(0, 0, 0, dp(170))
        lv.isVerticalScrollBarEnabled = false
        adapter = DebtAdapter()
        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, pos, _ ->
            items.getOrNull(pos)?.let { showOptions(it) }
        }
        tvEmpty = act.tv("Belum ada catatan.\nKetuk + untuk menambah.", 14f, C.MUTED)
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
        val all = DebtStore.load(act)
        val (owe, owed) = DebtStore.totals(all)
        tvOwe.animateRupiah(owe)
        tvOwed.animateRupiah(owed)

        val filtered = when (filter) {
            1 -> all.filter { it.iOwe }
            2 -> all.filter { !it.iOwe }
            else -> all
        }
        items = filtered.sortedBy { it.paid } // yang belum lunas di atas
        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        tabViews.forEachIndexed { i, t ->
            val sel = i == filter
            t.background = act.round(if (sel) C.BLUE else C.SURFACE, 12, if (sel) C.BLUE else C.STROKE)
            t.setTextColor(if (sel) C.WHITE else C.MUTED)
        }
    }

    fun add() = showForm(null)

    private inner class DebtAdapter : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val d = items[position]

            val outer = LinearLayout(act)
            outer.setPadding(0, 0, 0, dp(10))

            val card = LinearLayout(act)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.setPadding(dp(16), dp(14), dp(16), dp(14))
            card.background = act.round(C.SURFACE, 16, if (d.paid) C.STROKE else C.BLUE_DIM)

            val left = LinearLayout(act)
            left.orientation = LinearLayout.VERTICAL
            val name = act.tv(d.name, 16f, if (d.paid) C.MUTED else C.WHITE, true)
            if (d.paid) name.paintFlags = name.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            left.addView(name)
            val kind = if (d.iOwe) "Hutang" else "Piutang"
            left.addView(
                act.tv("$kind • ${Fmt.date(d.createdAt)}", 12f, C.MUTED),
                act.lp(WRAP_CONTENT, WRAP_CONTENT, t = 2)
            )
            if (d.note.isNotBlank()) {
                left.addView(act.tv(d.note, 13f, C.NOTE), act.lp(WRAP_CONTENT, WRAP_CONTENT, t = 4))
            }
            card.addView(left, act.lp(0, WRAP_CONTENT, 1f))

            val right = LinearLayout(act)
            right.orientation = LinearLayout.VERTICAL
            right.gravity = Gravity.END
            val amountColor = if (d.paid) C.MUTED else if (d.iOwe) C.RED else C.GREEN
            right.addView(act.tv(Fmt.rupiah(d.amount), 16f, amountColor, true))
            val badge = act.tv(if (d.paid) "LUNAS" else "BELUM", 10f, if (d.paid) C.MUTED else C.WHITE, true)
            badge.setPadding(dp(8), dp(2), dp(8), dp(2))
            badge.background = act.round(if (d.paid) C.BLACK else C.BLUE, 8, if (d.paid) C.STROKE else null)
            right.addView(badge, act.lp(WRAP_CONTENT, WRAP_CONTENT, t = 6))
            card.addView(right, act.lp(WRAP_CONTENT, WRAP_CONTENT, l = 12))

            card.pressFeedback()
            outer.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            outer.rise(position)
            return outer
        }
    }

    // ------------------------------------------------------------ Dialog

    private fun showOptions(d: Debt) {
        val opts = arrayOf(
            if (d.paid) "Tandai belum lunas" else "Tandai lunas",
            "Ubah",
            "Hapus"
        )
        act.darkDialog(
            act.dialogBuilder().setTitle(d.name).setItems(opts) { _, which ->
                when (which) {
                    0 -> {
                        DebtStore.upsert(act, d.copy(paid = !d.paid))
                        refresh()
                    }
                    1 -> showForm(d)
                    else -> confirmDelete(d)
                }
            }
        ).show()
    }

    private fun confirmDelete(d: Debt) {
        act.darkDialog(
            act.dialogBuilder()
                .setTitle("Hapus catatan?")
                .setMessage("${d.name} • ${Fmt.rupiah(d.amount)}")
                .setPositiveButton("Hapus") { _, _ ->
                    DebtStore.delete(act, d.id)
                    refresh()
                }
                .setNegativeButton("Batal", null)
        ).show()
    }

    private fun showForm(existing: Debt?) {
        val form = LinearLayout(act)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(12), dp(20), dp(8))

        val etName = act.field("Nama orang", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val etAmount = act.field("Jumlah (Rp)", InputType.TYPE_CLASS_NUMBER)
        val etNote = act.field("Catatan (opsional)", InputType.TYPE_CLASS_TEXT)
        if (existing != null) {
            etName.setText(existing.name)
            etAmount.setText(existing.amount.toString())
            etNote.setText(existing.note)
        }
        form.addView(etName)
        form.addView(etAmount, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 8))
        form.addView(etNote, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 8))

        val group = RadioGroup(act)
        group.orientation = RadioGroup.VERTICAL
        val rbOwe = act.radio("Hutang (saya berhutang)")
        val rbOwed = act.radio("Piutang (orang berhutang ke saya)")
        group.addView(rbOwe)
        group.addView(rbOwed)
        val defaultOwe = existing?.iOwe ?: (filter != 2)
        if (defaultOwe) rbOwe.isChecked = true else rbOwed.isChecked = true
        form.addView(group, act.lp(MATCH_PARENT, WRAP_CONTENT, t = 14))

        val scroll = ScrollView(act)
        scroll.addView(form)

        val dlg = act.darkDialog(
            act.dialogBuilder()
                .setTitle(if (existing == null) "Catat baru" else "Ubah catatan")
                .setView(scroll)
                .setPositiveButton("Simpan", null)
                .setNegativeButton("Batal", null)
        )
        dlg.show()
        dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = etName.text.toString().trim()
            val amount = etAmount.text.toString().filter { it.isDigit() }.toLongOrNull()
            if (name.isEmpty()) {
                etName.error = "Nama wajib diisi"
                return@setOnClickListener
            }
            if (amount == null || amount <= 0L) {
                etAmount.error = "Masukkan jumlah yang valid"
                return@setOnClickListener
            }
            val now = System.currentTimeMillis()
            DebtStore.upsert(
                act,
                Debt(
                    id = existing?.id ?: now,
                    name = name,
                    amount = amount,
                    note = etNote.text.toString().trim(),
                    createdAt = existing?.createdAt ?: now,
                    paid = existing?.paid ?: false,
                    iOwe = rbOwe.isChecked
                )
            )
            refresh()
            dlg.dismiss()
        }
    }
}
