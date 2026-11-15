package id.dstech.keuangan

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

/**
 * Layar utama: judul + akun di atas, tiga tab di bawah
 * (0 = Hutang Piutang, 1 = Tabungan, 2 = Uang).
 */
class MainActivity : Activity() {

    private lateinit var debtScreen: DebtScreen
    private lateinit var savingsScreen: SavingsScreen
    private lateinit var moneyScreen: MoneyScreen
    private val navViews = ArrayList<TextView>()
    private lateinit var navIndicator: View
    private var navAnim: ValueAnimator? = null
    private var tab = 0
    private var ready = false
    private var regDebts: ListenerRegistration? = null
    private var regGoals: ListenerRegistration? = null
    private var regCash: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        setContentView(buildUi())
        ready = true
        selectTab(savedInstanceState?.getInt("tab") ?: 0, animate = false)
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (!ready) return
        removeListeners()
        regDebts = CloudRepo.listen(this) { debtScreen.refresh() }
        regGoals = CloudRepo.listenGoals(this) {
            savingsScreen.refresh()
            moneyScreen.refresh() // kartu "Uang + tabungan" ikut berubah
        }
        regCash = CloudRepo.listenCash(this) { moneyScreen.refresh() }
    }

    override fun onStop() {
        super.onStop()
        removeListeners()
    }

    private fun removeListeners() {
        regDebts?.remove()
        regGoals?.remove()
        regCash?.remove()
        regDebts = null
        regGoals = null
        regCash = null
    }

    override fun onResume() {
        super.onResume()
        if (!ready) return
        refreshAll()
    }

    private fun refreshAll() {
        debtScreen.refresh()
        savingsScreen.refresh()
        moneyScreen.refresh()
    }

    /** Intent dari widget: "tab" memilih tab, "add" membuka formulir tambah di tab itu. */
    private fun handleIntent(intent: Intent?) {
        if (!ready) return
        val i = intent ?: return
        if (i.hasExtra("tab")) {
            selectTab(i.getIntExtra("tab", 0))
            i.removeExtra("tab")
        }
        if (i.getBooleanExtra("add", false)) {
            i.removeExtra("add")
            when (tab) {
                1 -> savingsScreen.add()
                2 -> moneyScreen.add()
                else -> debtScreen.add()
            }
        }
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val frame = FrameLayout(this)
        frame.setBackgroundColor(C.BLACK)

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        frame.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        // Header: nama aplikasi, email akun, tombol keluar
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(18), dp(20), dp(18), dp(8))

        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(tv(getString(R.string.app_name), 28f, C.WHITE, true))
        val email = FirebaseAuth.getInstance().currentUser?.email ?: "Data tersimpan di cloud"
        titles.addView(tv(email, 13f, C.MUTED), lp(WRAP_CONTENT, WRAP_CONTENT, t = 2))
        header.addView(titles, lp(0, WRAP_CONTENT, 1f))

        val logout = tv("Keluar", 13f, C.MUTED, true)
        logout.setPadding(dp(10), dp(8), 0, dp(8))
        logout.pressFeedback()
        logout.setOnClickListener { confirmSignOut() }
        header.addView(logout)
        column.addView(header)

        // Isi: tiga layar yang ditukar-tampilkan. Mengisi sampai dasar layar, kapsul menimpanya.
        debtScreen = DebtScreen(this)
        savingsScreen = SavingsScreen(this)
        moneyScreen = MoneyScreen(this)
        val content = FrameLayout(this)
        content.addView(debtScreen.view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(savingsScreen.view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(moneyScreen.view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        column.addView(content, lp(MATCH_PARENT, 0, 1f))

        // Navigasi kapsul melayang: putih dengan opasitas 20%. Pil biru satu saja yang bergeser antar tab.
        val nav = FrameLayout(this)
        nav.setPadding(dp(6), dp(6), dp(6), dp(6))
        val glass = Color.argb(51, 255, 255, 255) // 20% dari 255
        val capsule = GradientDrawable()
        capsule.setColor(glass)
        capsule.cornerRadius = dp(40).toFloat()
        capsule.setStroke(dp(1), glass)
        nav.background = capsule

        navIndicator = View(this)
        navIndicator.background = round(C.BLUE, 32)
        nav.addView(navIndicator, FrameLayout.LayoutParams(0, MATCH_PARENT))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        nav.addView(row, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val weights = floatArrayOf(1.5f, 1f, 0.8f)
        listOf("Hutang Piutang", "Tabungan", "Uang").forEachIndexed { i, label ->
            val t = tv(label, 13f, C.NOTE, true)
            t.gravity = Gravity.CENTER
            t.maxLines = 1
            t.setPadding(dp(6), dp(12), dp(6), dp(12))
            t.setOnClickListener { selectTab(i) }
            t.pressFeedback()
            navViews.add(t)
            row.addView(t, lp(0, WRAP_CONTENT, weights[i]))
        }
        val navParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM)
        navParams.setMargins(dp(18), 0, dp(18), dp(18))
        frame.addView(nav, navParams)

        return frame
    }

    private fun screenView(i: Int): View = when (i) {
        0 -> debtScreen.view
        1 -> savingsScreen.view
        else -> moneyScreen.view
    }

    private fun selectTab(i: Int, animate: Boolean = true) {
        val old = tab
        val next = if (i in 0..2) i else 0
        tab = next
        Motion.startEnter()

        // Hentikan sisa animasi supaya ketukan beruntun tidak meninggalkan layar setengah transparan.
        for (n in 0..2) {
            screenView(n).animate().cancel()
            screenView(n).alpha = 1f
            screenView(n).translationX = 0f
        }

        val incoming = screenView(next)
        if (animate && old != next) {
            val outgoing = screenView(old)
            val dir = if (next > old) 1f else -1f
            outgoing.visibility = View.VISIBLE
            outgoing.animate().alpha(0f).translationX(-dir * dp(24))
                .setDuration(140).setStartDelay(0)
                .withEndAction {
                    if (tab != old) {
                        outgoing.visibility = View.GONE
                        outgoing.alpha = 1f
                        outgoing.translationX = 0f
                    }
                }.start()
            incoming.visibility = View.VISIBLE
            incoming.alpha = 0f
            incoming.translationX = dir * dp(28)
            incoming.animate().alpha(1f).translationX(0f)
                .setStartDelay(90).setDuration(320)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        } else {
            for (n in 0..2) screenView(n).visibility = if (n == next) View.VISIBLE else View.GONE
        }
        moveNav(animate)
        refreshAll()
    }

    /** Menggeser pil biru ke tab terpilih sambil mengubah warna teks, dengan gerak yang melambat di ujung. */
    private fun moveNav(animate: Boolean) {
        navAnim?.cancel()
        val target = navViews[tab]
        if (target.width == 0) {
            // Belum diukur: tunggu tata letak pertama, lalu tempatkan tanpa animasi.
            target.post { moveNav(false) }
            return
        }
        val lp = navIndicator.layoutParams
        val toX = target.left.toFloat()
        val toW = target.width
        val fromX = navIndicator.translationX
        val fromW = if (lp.width > 0) lp.width else toW
        val fromColors = IntArray(navViews.size) { navViews[it].currentTextColor }
        val toColors = IntArray(navViews.size) { if (it == tab) C.WHITE else C.NOTE }

        fun apply(f: Float) {
            navIndicator.translationX = fromX + (toX - fromX) * f
            lp.width = (fromW + (toW - fromW) * f).toInt()
            navIndicator.layoutParams = lp
            val ev = ArgbEvaluator()
            navViews.forEachIndexed { idx, t ->
                t.setTextColor(ev.evaluate(f, fromColors[idx], toColors[idx]) as Int)
            }
        }
        if (!animate) {
            apply(1f)
            return
        }
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 360
        a.interpolator = DecelerateInterpolator(1.8f)
        a.addUpdateListener { apply(it.animatedFraction) }
        navAnim = a
        a.start()
    }

    private fun confirmSignOut() {
        darkDialog(
            dialogBuilder()
                .setTitle("Keluar akun?")
                .setMessage("Data tetap aman di cloud dan muncul lagi saat kamu masuk.")
                .setPositiveButton("Keluar") { _, _ ->
                    removeListeners()
                    FirebaseAuth.getInstance().signOut()
                    DebtStore.clear(this)
                    GoalStore.clear(this)
                    CashStore.clear(this)
                    startActivity(Intent(this, AuthActivity::class.java))
                    finish()
                }
                .setNegativeButton("Batal", null)
        ).show()
    }
}
