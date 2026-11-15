package id.dstech.keuangan

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

private object AC {
    val BLACK = Color.parseColor("#000000")
    val WHITE = Color.parseColor("#FFFFFF")
    val BLUE = Color.parseColor("#2979FF")
    val BLUE_LIGHT = Color.parseColor("#5C9DFF")
    val MUTED = Color.parseColor("#8A94A8")
    val SURFACE = Color.parseColor("#0D0D12")
    val STROKE = Color.parseColor("#1B2744")
    val ERROR = Color.parseColor("#FF6B6B")
}

/** Layar masuk / daftar (email + password) supaya data terikat ke akun, bukan ke HP. */
class AuthActivity : Activity() {

    private lateinit var etEmail: EditText
    private lateinit var etPass: EditText
    private lateinit var tvMsg: TextView
    private lateinit var btnLogin: TextView
    private lateinit var btnRegister: TextView
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseAuth.getInstance().currentUser != null) {
            goMain()
            return
        }
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(AC.BLACK)
        scroll.isFillViewport = true

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_VERTICAL
        col.setPadding(dp(28), dp(24), dp(28), dp(24))
        scroll.addView(col, MATCH_PARENT, MATCH_PARENT)

        val title = LinearLayout(this)
        title.orientation = LinearLayout.HORIZONTAL
        title.addView(label(getString(R.string.app_name), 32f, AC.WHITE, true))
        col.addView(title)
        col.addView(
            label("Masuk agar datamu tersimpan aman di cloud, bukan hanya di HP.", 14f, AC.MUTED, false),
            lp(t = 6, b = 28)
        )

        etEmail = field("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        etPass = field("Password (min. 6 karakter)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        col.addView(etEmail, lp())
        col.addView(etPass, lp(t = 10))

        tvMsg = label("", 13f, AC.ERROR, false)
        col.addView(tvMsg, lp(t = 10))

        btnLogin = button("Masuk", true) { submit(false) }
        btnRegister = button("Daftar akun baru", false) { submit(true) }
        col.addView(btnLogin, lp(t = 18))
        col.addView(btnRegister, lp(t = 10))

        val forgot = label("Lupa password?", 13f, AC.BLUE_LIGHT, true)
        forgot.gravity = Gravity.CENTER
        forgot.setPadding(0, dp(14), 0, dp(14))
        forgot.setOnClickListener { resetPassword() }
        col.addView(forgot, lp(t = 6))

        return scroll
    }

    private fun submit(register: Boolean) {
        if (busy) return
        val email = etEmail.text.toString().trim()
        val pass = etPass.text.toString()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            msg("Email tidak valid.")
            return
        }
        if (pass.length < 6) {
            msg("Password minimal 6 karakter.")
            return
        }
        setBusy(true)
        val auth = FirebaseAuth.getInstance()
        val task = if (register) {
            auth.createUserWithEmailAndPassword(email, pass)
        } else {
            auth.signInWithEmailAndPassword(email, pass)
        }
        task.addOnCompleteListener(this) { t ->
            setBusy(false)
            if (t.isSuccessful) goMain() else msg(friendly(t.exception))
        }
    }

    private fun resetPassword() {
        if (busy) return
        val email = etEmail.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            msg("Isi email dulu, lalu ketuk \"Lupa password?\".")
            return
        }
        setBusy(true)
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnCompleteListener(this) { t ->
                setBusy(false)
                if (t.isSuccessful) msg("Link reset password dikirim ke $email.", true)
                else msg(friendly(t.exception))
            }
    }

    private fun friendly(e: Exception?): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "Password terlalu lemah."
        is FirebaseAuthUserCollisionException -> "Email sudah terdaftar. Coba masuk."
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> "Email atau password salah."
        is FirebaseNetworkException -> "Tidak ada koneksi internet."
        else -> e?.localizedMessage ?: "Gagal, coba lagi."
    }

    private fun goMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun setBusy(b: Boolean) {
        busy = b
        val a = if (b) 0.5f else 1f
        btnLogin.alpha = a
        btnRegister.alpha = a
        if (b) msg("", true)
    }

    private fun msg(s: String, ok: Boolean = false) {
        tvMsg.setTextColor(if (ok) AC.BLUE_LIGHT else AC.ERROR)
        tvMsg.text = s
    }

    // ---- helper UI ----

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun lp(t: Int = 0, b: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        p.setMargins(0, dp(t), 0, dp(b))
        return p
    }

    private fun label(s: String, size: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun field(hintText: String, type: Int): EditText {
        val e = EditText(this)
        e.hint = hintText
        e.inputType = type
        e.setSingleLine()
        e.setTextColor(AC.WHITE)
        e.setHintTextColor(AC.MUTED)
        e.setPadding(dp(16), dp(14), dp(16), dp(14))
        e.background = round(AC.SURFACE, 14, AC.STROKE)
        return e
    }

    private fun button(text: String, filled: Boolean, onClick: () -> Unit): TextView {
        val b = label(text, 15f, AC.WHITE, true)
        b.gravity = Gravity.CENTER
        b.setPadding(0, dp(14), 0, dp(14))
        b.background = if (filled) round(AC.BLUE, 14, null) else round(AC.BLACK, 14, AC.BLUE)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun round(fill: Int, radiusDp: Int, stroke: Int?): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) g.setStroke(dp(1), stroke)
        return g
    }
}
