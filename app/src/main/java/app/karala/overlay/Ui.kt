package app.karala.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Ekran üstü pencereler için küçük görünüm yardımcıları (Compose yerine klasik View: servis içinde daha hafif ve sağlam). */
internal object Ui {
    const val SURFACE = 0xF41B1E26.toInt()
    const val SURFACE_HI = 0xFF2A2F3B.toInt()
    const val ACCENT = 0xFFFFD54A.toInt()
    const val ON_ACCENT = 0xFF1B1E26.toInt()
    const val TEXT = 0xFFF2F4F8.toInt()
    const val DIM = 0xFF9AA3B2.toInt()
    const val GOOD = 0xFF4CD08A.toInt()
    const val BAD = 0xFFFF6B6B.toInt()
    const val OUTLINE = 0x33FFFFFF

    val PALETTE = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFF16181D.toInt(), 0xFFFFD54A.toInt(), 0xFFFF8A3D.toInt(), 0xFFFF5252.toInt(), 0xFFFF6FB5.toInt(),
        0xFFB388FF.toInt(), 0xFF4FC3F7.toInt(), 0xFF3D7BFF.toInt(), 0xFF69F0AE.toInt(), 0xFF2E9E5B.toInt(), 0xFF9AA3B2.toInt(),
    )

    private var regular: Typeface? = null
    private var strong: Typeface? = null
    /** Uygulamanın yazı tipi (Plus Jakarta Sans); yüklenemezse sistem yazı tipi. */
    fun font(c: Context, bold: Boolean): Typeface {
        if (regular == null) {
            regular = runCatching { androidx.core.content.res.ResourcesCompat.getFont(c, app.karala.R.font.jakarta_medium) }.getOrNull() ?: Typeface.DEFAULT
            strong = runCatching { androidx.core.content.res.ResourcesCompat.getFont(c, app.karala.R.font.jakarta_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        }
        return (if (bold) strong else regular)!!
    }

    fun dp(c: Context, v: Float): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()
    fun dp(c: Context, v: Int): Int = dp(c, v.toFloat())

    fun bg(color: Int, radius: Float, stroke: Int = 0, strokeW: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (strokeW > 0) setStroke(strokeW, stroke)
    }

    fun iconBtn(c: Context, icon: Icon, sizeDp: Int = 42, desc: String): ImageView = ImageView(c).apply {
        layoutParams = LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp))
        val p = dp(c, sizeDp * 0.24f); setPadding(p, p, p, p)
        setImageDrawable(IconDrawable(icon, TEXT))
        contentDescription = desc
        isClickable = true; isFocusable = false
        tag = icon
    }

    /** Düğmeyi "seçili" olarak boyar. */
    fun setActive(v: ImageView, active: Boolean, tint: Int = ACCENT) {
        (v.drawable as? IconDrawable)?.setColor(if (active) tint else TEXT)
        v.background = if (active) bg((tint and 0x00FFFFFF) or 0x33000000, dp(v.context, 12).toFloat()) else null
    }

    fun setEnabledLook(v: ImageView, enabled: Boolean) { v.alpha = if (enabled) 1f else 0.35f }

    fun setIcon(v: ImageView, icon: Icon, color: Int = TEXT) { v.setImageDrawable(IconDrawable(icon, color)); v.tag = icon }

    fun label(c: Context, text: String, sp: Float = 13f, color: Int = TEXT, bold: Boolean = false) = TextView(c).apply {
        this.text = text; setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = font(c, bold)
        includeFontPadding = false
    }

    fun pill(c: Context, text: String, onClick: () -> Unit): TextView = label(c, text, 13f).apply {
        gravity = Gravity.CENTER
        setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(c, 6) }
        setPillActive(this, false)
        setOnClickListener { onClick() }
    }

    fun setPillActive(v: TextView, active: Boolean, tint: Int = ACCENT) {
        val r = dp(v.context, 18).toFloat()
        v.background = if (active) bg(tint, r) else bg(SURFACE_HI, r, OUTLINE, dp(v.context, 1))
        v.setTextColor(if (active) ON_ACCENT else TEXT)
    }

    fun sep(c: Context): View = View(c).apply {
        layoutParams = LinearLayout.LayoutParams(dp(c, 1), dp(c, 22)).apply { marginStart = dp(c, 4); marginEnd = dp(c, 4) }
        setBackgroundColor(OUTLINE)
    }

    // Taban çizgisi hizalaması kapalı: karışık içerikli satırlarda yüksekliği öngörülemez biçimde büyütüyor.
    fun row(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false
    }
    fun col(c: Context): LinearLayout = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    fun overlayParams(w: Int = -2, h: Int = -2): WindowManager.LayoutParams = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        android.graphics.PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    fun isDark(color: Int): Boolean =
        (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000 < 140
}

/**
 * Bir pencereyi tutamaçtan sürükler. Hareket eşiği aşılmazsa dokunuş "tık" sayılır.
 * Konum her harekette ekran sınırlarına kıstırılır.
 */
internal class WindowDragger(
    private val wm: WindowManager,
    private val window: View,
    private val params: WindowManager.LayoutParams,
    private val onClick: (() -> Unit)? = null,
    private val onDropped: (() -> Unit)? = null,
) : View.OnTouchListener {
    private var downX = 0f; private var downY = 0f; private var startX = 0; private var startY = 0
    private var dragging = false
    private val slop = ViewConfiguration.get(window.context).scaledTouchSlop

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX; val dy = e.rawY - downY
                if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                if (dragging) {
                    params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                    clamp(wm, window, params)
                    runCatching { wm.updateViewLayout(window, params) }
                }
            }
            MotionEvent.ACTION_UP -> if (dragging) onDropped?.invoke() else onClick?.invoke()
            MotionEvent.ACTION_CANCEL -> if (dragging) onDropped?.invoke()
        }
        return true
    }

    companion object {
        fun screenSize(wm: WindowManager): Pair<Int, Int> {
            val (w, h) = if (android.os.Build.VERSION.SDK_INT >= 30) {
                val b = wm.maximumWindowMetrics.bounds; b.width() to b.height()
            } else {
                val p = android.graphics.Point()
                @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(p); p.x to p.y
            }
            // Bazı ortamlarda pencere ölçüsü eksik gelir; ekran ölçüsünden küçük olamaz.
            val dm = android.content.res.Resources.getSystem().displayMetrics
            return maxOf(w, dm.widthPixels) to maxOf(h, dm.heightPixels)
        }

        fun clamp(wm: WindowManager, window: View, params: WindowManager.LayoutParams) {
            val (sw, sh) = screenSize(wm)
            val w = window.width.coerceAtLeast(1); val h = window.height.coerceAtLeast(1)
            params.x = params.x.coerceIn(0, (sw - w).coerceAtLeast(0))
            params.y = params.y.coerceIn(0, (sh - h).coerceAtLeast(0))
        }
    }
}
