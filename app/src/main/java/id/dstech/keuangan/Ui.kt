package id.dstech.keuangan

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import java.util.WeakHashMap

/** Palet warna aplikasi: hitam pekat dengan aksen biru. */
object C {
    val BLACK = Color.parseColor("#000000")
    val WHITE = Color.parseColor("#FFFFFF")
    val BLUE = Color.parseColor("#2979FF")
    val BLUE_LIGHT = Color.parseColor("#5C9DFF")
    val BLUE_DIM = Color.parseColor("#1F4FB8")
    val MUTED = Color.parseColor("#8A94A8")
    val SURFACE = Color.parseColor("#0D0D12")
    val STROKE = Color.parseColor("#1B2744")
    val NOTE = Color.parseColor("#B8C2D6")
    val RED = Color.parseColor("#FF5252")
    val GREEN = Color.parseColor("#2ECC71")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.tv(s: String, size: Float, color: Int, bold: Boolean = false): TextView {
    val t = TextView(this)
    t.text = s
    t.textSize = size
    t.setTextColor(color)
    if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
    return t
}

fun Context.round(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(fill)
    g.cornerRadius = dp(radiusDp).toFloat()
    if (stroke != null) g.setStroke(dp(1), stroke)
    return g
}

fun Context.lp(
    w: Int, h: Int, weight: Float = 0f,
    l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0
): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(w, h, weight)
    p.setMargins(dp(l), dp(t), dp(r), dp(b))
    return p
}

fun Context.field(hintText: String, type: Int): EditText {
    val e = EditText(this)
    e.hint = hintText
    e.inputType = type
    e.setTextColor(C.WHITE)
    e.setHintTextColor(C.MUTED)
    e.backgroundTintList = ColorStateList.valueOf(C.BLUE)
    return e
}

fun Context.radio(label: String): RadioButton {
    val r = RadioButton(this)
    r.id = View.generateViewId()
    r.text = label
    r.setTextColor(C.WHITE)
    r.buttonTintList = ColorStateList.valueOf(C.BLUE)
    return r
}

/** Tombol bulat "+" yang melayang di pojok kanan bawah. */
fun Context.fab(onClick: () -> Unit): TextView {
    val f = TextView(this)
    f.text = "+"
    f.textSize = 30f
    f.setTextColor(C.WHITE)
    f.gravity = Gravity.CENTER
    f.background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(C.BLUE)
    }
    f.elevation = dp(6).toFloat()
    f.setOnClickListener { onClick() }
    f.pressFeedback()
    return f
}

fun Context.fabParams(): FrameLayout.LayoutParams {
    val p = FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM or Gravity.END)
    p.setMargins(0, 0, dp(22), dp(92))
    return p
}

/** Kartu ringkasan kecil: label di atas, nilai di bawah. */
fun Context.summaryCard(label: String, value: TextView): View {
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.setPadding(dp(14), dp(12), dp(14), dp(12))
    c.background = round(C.SURFACE, 16, C.STROKE)
    c.addView(tv(label, 12f, C.MUTED))
    c.addView(value, lp(WRAP_CONTENT, WRAP_CONTENT, t = 4))
    return c
}

/** Pernah tampil di berapa persen, per kunci. Dipakai supaya bilah mengalir dari nilai lama ke baru. */
private val lastPercent = HashMap<Long, Int>()

/**
 * Bilah progres (0..100). Dengan [key], bilah mengalir dari persen terakhir yang tampil
 * (pertama kali dari 0) ke persen sekarang.
 */
fun Context.progressBar(percent: Int, color: Int = C.BLUE, key: Long? = null): View {
    val p = percent.coerceIn(0, 100)
    val bar = LinearLayout(this)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.weightSum = 100f
    bar.background = round(C.STROKE, 4)
    val fill = View(this)
    fill.background = round(color, 4)
    val fillLp = LinearLayout.LayoutParams(0, MATCH_PARENT, 0f)
    bar.addView(fill, fillLp)
    val from = if (key != null) (lastPercent[key] ?: 0) else p
    if (key != null) lastPercent[key] = p
    if (from == p) {
        fillLp.weight = p.toFloat()
    } else {
        fillLp.weight = from.toFloat()
        val a = ValueAnimator.ofFloat(from.toFloat(), p.toFloat())
        a.duration = 700
        a.startDelay = 120
        a.interpolator = DecelerateInterpolator(2f)
        a.addUpdateListener {
            fillLp.weight = it.animatedValue as Float
            fill.requestLayout()
        }
        a.start()
    }
    return bar
}

private val lastShown = WeakHashMap<TextView, Long>()
private val countAnim = WeakHashMap<TextView, ValueAnimator>()

/** Menampilkan nominal rupiah dengan hitungan naik/turun yang halus dari nilai sebelumnya. */
fun TextView.animateRupiah(to: Long) {
    val from = lastShown[this] ?: 0L
    lastShown[this] = to
    countAnim.remove(this)?.cancel()
    if (from == to) {
        text = Fmt.rupiah(to)
        return
    }
    val a = ValueAnimator.ofFloat(0f, 1f)
    a.duration = 650
    a.interpolator = DecelerateInterpolator(2f)
    a.addUpdateListener {
        text = Fmt.rupiah(from + ((to - from) * it.animatedFraction).toLong())
    }
    a.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            text = Fmt.rupiah(to)
        }
    })
    countAnim[this] = a
    a.start()
}

/** Penanda "layar baru saja ditampilkan": item daftar yang dibuat dalam jendela ini muncul bergantian. */
object Motion {
    var enterUntil = 0L
    fun startEnter() {
        enterUntil = SystemClock.uptimeMillis() + 500
    }
}

/** Item daftar naik sambil memudar masuk, berurutan sesuai posisinya. */
fun View.rise(position: Int) {
    if (SystemClock.uptimeMillis() > Motion.enterUntil) return
    alpha = 0f
    translationY = resources.displayMetrics.density * 18f
    animate()
        .alpha(1f)
        .translationY(0f)
        .setStartDelay(minOf(position, 8) * 45L)
        .setDuration(340)
        .setInterpolator(DecelerateInterpolator(2f))
        .start()
}

/** Mengecil sedikit saat ditekan lalu membal kembali. Tidak menelan klik. */
@SuppressLint("ClickableViewAccessibility")
fun View.pressFeedback() {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90)
                    .setInterpolator(DecelerateInterpolator()).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(OvershootInterpolator(2.5f)).start()
        }
        false
    }
}

fun Activity.dialogBuilder(): AlertDialog.Builder =
    AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)

fun Activity.darkDialog(builder: AlertDialog.Builder): AlertDialog {
    val dlg = builder.create()
    dlg.setOnShowListener {
        dlg.window?.setBackgroundDrawable(round(Color.parseColor("#0A0A0F"), 20, C.STROKE))
        dlg.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(C.BLUE_LIGHT)
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(C.MUTED)
    }
    return dlg
}
