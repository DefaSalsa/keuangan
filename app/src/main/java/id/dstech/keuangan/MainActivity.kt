package id.dstech.keuangan

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONArray
import org.json.JSONObject

/**
 * Satu-satunya layar aplikasi: WebView yang memuat tampilan HTML di assets/www.
 * Data, login, dan sinkron Firestore tetap di Kotlin; HTML bicara lewat objek `native`.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var regDebts: ListenerRegistration? = null
    private var regGoals: ListenerRegistration? = null
    private var regCash: ListenerRegistration? = null

    @Volatile private var pendingTab = -1
    @Volatile private var pendingAdd = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.isVerticalScrollBarEnabled = false
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.setSupportZoom(false)
        web.settings.builtInZoomControls = false
        web.webViewClient = object : WebViewClient() {
            // Halaman hanya boleh memuat berkas dari assets; tautan lain diblokir.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
        web.addJavascriptInterface(Bridge(), "native")
        setContentView(web)
        readIntent(intent)
        web.loadUrl("file:///android_asset/www/index.html")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        val t = pendingTab
        if (t >= 0) {
            val add = pendingAdd
            pendingTab = -1
            pendingAdd = false
            js("window.openTab&&window.openTab($t,$add)")
        }
    }

    /** Intent dari widget: "tab" memilih tab, "add" membuka formulir tambah di tab itu. */
    private fun readIntent(i: Intent?) {
        if (i == null || !i.hasExtra("tab")) return
        pendingTab = i.getIntExtra("tab", 0)
        pendingAdd = i.getBooleanExtra("add", false)
        i.removeExtra("tab")
        i.removeExtra("add")
    }

    override fun onStart() {
        super.onStart()
        startListeners()
    }

    override fun onStop() {
        super.onStop()
        removeListeners()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        pushState()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        web.removeJavascriptInterface("native")
        web.destroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        web.evaluateJavascript("window.handleBack&&window.handleBack()") { r ->
            if (r != "true") super@MainActivity.onBackPressed()
        }
    }

    // ------------------------------------------------------------ Cloud

    private fun startListeners() {
        removeListeners()
        if (FirebaseAuth.getInstance().currentUser == null) return
        regDebts = CloudRepo.listen(this) { pushState() }
        regGoals = CloudRepo.listenGoals(this) { pushState() }
        regCash = CloudRepo.listenCash(this) { pushState() }
    }

    private fun removeListeners() {
        regDebts?.remove()
        regGoals?.remove()
        regCash?.remove()
        regDebts = null
        regGoals = null
        regCash = null
    }

    private fun js(code: String) {
        runOnUiThread { web.evaluateJavascript(code, null) }
    }

    private fun pushState() {
        if (FirebaseAuth.getInstance().currentUser == null) return
        js("window.onState&&window.onState(${stateJson()})")
    }

    // ------------------------------------------------------------ Data

    private fun stateJson(): String {
        val o = JSONObject()
        val email = FirebaseAuth.getInstance().currentUser?.email
        o.put("email", email ?: JSONObject.NULL)

        val debts = JSONArray()
        for (d in DebtStore.load(this)) {
            debts.put(
                JSONObject()
                    .put("id", d.id).put("name", d.name).put("amount", d.amount)
                    .put("note", d.note).put("createdAt", d.createdAt)
                    .put("paid", d.paid).put("iOwe", d.iOwe).put("dueAt", d.dueAt)
            )
        }
        o.put("debts", debts)

        val goals = JSONArray()
        for (g in GoalStore.load(this)) {
            val hist = JSONArray()
            for (h in g.history) {
                hist.put(JSONObject().put("amount", h.amount).put("note", h.note).put("at", h.at))
            }
            goals.put(
                JSONObject()
                    .put("id", g.id).put("name", g.name).put("target", g.target)
                    .put("createdAt", g.createdAt).put("daily", g.daily).put("icon", g.icon).put("history", hist)
            )
        }
        o.put("goals", goals)

        val cash = JSONArray()
        for (e in CashStore.load(this)) {
            cash.put(JSONObject().put("id", e.id).put("amount", e.amount).put("note", e.note).put("at", e.at))
        }
        o.put("cash", cash)
        return o.toString()
    }

    private fun friendly(e: Exception?): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "Password terlalu lemah."
        is FirebaseAuthUserCollisionException -> "Email sudah terdaftar. Coba masuk."
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> "Email atau password salah."
        is FirebaseNetworkException -> "Tidak ada koneksi internet."
        else -> e?.localizedMessage ?: "Gagal, coba lagi."
    }

    /** Jembatan yang dipanggil dari JavaScript. Semua metode ubah-data mengembalikan state terbaru. */
    private inner class Bridge {

        @JavascriptInterface
        fun appName(): String = getString(R.string.app_name)

        @JavascriptInterface
        fun state(): String = stateJson()

        @JavascriptInterface
        fun takePending(): String {
            val t = pendingTab
            if (t < 0) return ""
            val add = pendingAdd
            pendingTab = -1
            pendingAdd = false
            return "$t,$add"
        }

        @JavascriptInterface
        fun upsertDebt(json: String): String {
            val o = JSONObject(json)
            DebtStore.upsert(
                this@MainActivity,
                Debt(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    amount = o.getLong("amount"),
                    note = o.optString("note"),
                    createdAt = o.getLong("createdAt"),
                    paid = o.optBoolean("paid", false),
                    iOwe = o.optBoolean("iOwe", true),
                    dueAt = o.optLong("dueAt", 0L)
                )
            )
            return stateJson()
        }

        @JavascriptInterface
        fun deleteDebt(id: String): String {
            DebtStore.delete(this@MainActivity, id.toLong())
            return stateJson()
        }

        @JavascriptInterface
        fun upsertGoal(json: String): String {
            val o = JSONObject(json)
            val hist = ArrayList<Deposit>()
            val ha = o.optJSONArray("history")
            if (ha != null) {
                for (i in 0 until ha.length()) {
                    val h = ha.optJSONObject(i) ?: continue
                    hist.add(Deposit(h.optLong("amount"), h.optString("note"), h.optLong("at")))
                }
            }
            GoalStore.upsert(
                this@MainActivity,
                Goal(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    target = o.getLong("target"),
                    createdAt = o.getLong("createdAt"),
                    history = hist,
                    daily = o.optLong("daily"),
                    icon = o.optString("icon")
                )
            )
            return stateJson()
        }

        @JavascriptInterface
        fun deleteGoal(id: String): String {
            GoalStore.delete(this@MainActivity, id.toLong())
            return stateJson()
        }

        @JavascriptInterface
        fun upsertCash(json: String): String {
            val o = JSONObject(json)
            CashStore.upsert(
                this@MainActivity,
                CashEntry(o.getLong("id"), o.getLong("amount"), o.optString("note"), o.getLong("at"))
            )
            return stateJson()
        }

        @JavascriptInterface
        fun deleteCash(id: String): String {
            CashStore.delete(this@MainActivity, id.toLong())
            return stateJson()
        }

        // ---- akun ----

        @JavascriptInterface
        fun login(email: String, pass: String) {
            runOnUiThread {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(this@MainActivity) { t ->
                        if (t.isSuccessful) startListeners()
                        js("window.onAuth('login',${t.isSuccessful},${JSONObject.quote(if (t.isSuccessful) "" else friendly(t.exception))})")
                    }
            }
        }

        @JavascriptInterface
        fun register(email: String, pass: String) {
            runOnUiThread {
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(this@MainActivity) { t ->
                        if (t.isSuccessful) startListeners()
                        js("window.onAuth('register',${t.isSuccessful},${JSONObject.quote(if (t.isSuccessful) "" else friendly(t.exception))})")
                    }
            }
        }

        @JavascriptInterface
        fun reset(email: String) {
            runOnUiThread {
                FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnCompleteListener(this@MainActivity) { t ->
                        val msg = if (t.isSuccessful) "Link reset password dikirim ke $email." else friendly(t.exception)
                        js("window.onAuth('reset',${t.isSuccessful},${JSONObject.quote(msg)})")
                    }
            }
        }

        @JavascriptInterface
        fun sync() {
            runOnUiThread {
                CloudRepo.syncNow(this@MainActivity) { ok ->
                    if (ok) pushState()
                    js("window.onSync&&window.onSync($ok)")
                }
            }
        }

        @JavascriptInterface
        fun logout() {
            runOnUiThread {
                removeListeners()
                FirebaseAuth.getInstance().signOut()
                DebtStore.clear(this@MainActivity)
                GoalStore.clear(this@MainActivity)
                CashStore.clear(this@MainActivity)
            }
        }
    }
}
