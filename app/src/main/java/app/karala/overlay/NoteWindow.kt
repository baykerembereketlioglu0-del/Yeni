package app.karala.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.karala.App
import app.karala.ink.EraserMode
import app.karala.ink.InkView
import app.karala.ink.ToolType
import kotlin.math.abs

/**
 * Tek bir yapışkan notun penceresi.
 *
 * İki hâli vardır:
 *  - **Sakin**: yalnızca kâğıt ve yazı; başlık şeridi boş, tutup sürüklenir.
 *  - **Etkin**: kalem notun üstüne geldiğinde (havada bile) ya da nota dokunulduğunda kendiliğinden açılır.
 *    Kalem seçili gelir; silgi, mürekkep rengi, geri al, temizle ve kâğıt rengi görünür.
 *    Kalem uzaklaşınca ya da notun dışına dokunulunca birkaç saniye içinde sakin hâle döner.
 *
 * Küçültülünce kâğıt renginde küçük bir rozete dönüşür; rozete dokununca geri açılır.
 */
@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
internal class NoteWindow(private val svc: StickyService, val note: StickyNote) {
    private val ctx: Context = svc
    private val wm: WindowManager = svc.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Float) = (v * d + 0.5f).toInt()

    val params: WindowManager.LayoutParams = Ui.overlayParams().apply {
        flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        x = note.x; y = note.y
    }

    var active = false; private set
    private var eraser = false
    private var inkColor = INKS[0]
    private var confirming = false
    private var attached = false
    private val idle = Runnable { if (!confirming) deactivate() }

    val root: FrameLayout = object : FrameLayout(ctx) {
        override fun dispatchHoverEvent(e: MotionEvent): Boolean {
            if (isPen(e)) {
                svc.onStylusSeen()
                when (e.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> if (!note.collapsed) activate()
                    MotionEvent.ACTION_HOVER_EXIT -> scheduleIdle(IDLE_AFTER_HOVER)
                }
            }
            return super.dispatchHoverEvent(e)
        }

        override fun dispatchTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                // Notun dışına dokunuldu: kalem havada değilse sakin hâle dön.
                if (active && !confirming) scheduleIdle(400)
                return false
            }
            if (e.actionMasked == MotionEvent.ACTION_DOWN && isPen(e)) svc.onStylusSeen()
            return super.dispatchTouchEvent(e)
        }
    }

    private val card = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    /**
     * Başlık şeridi: her yerinden sürüklenir. Bir düğmenin üstünden başlayan hareket de eşiği aşınca
     * düğmeden alınıp sürüklemeye dönüşür; dokunup bırakmak düğmeyi çalıştırır. Boş yere dokunmak
     * notu açar/kapatır.
     */
    private val header = object : FrameLayout(ctx) {
        private val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
        private var downX = 0f; private var downY = 0f; private var startX = 0; private var startY = 0
        private var dragging = false; private var childTook = false

        override fun dispatchTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; dragging = false
                    handler.removeCallbacks(idle)
                    childTook = super.dispatchTouchEvent(e)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        // Düğmeye giden dokunuşu iptal et: sürükleme bir tıklamaya dönüşmesin.
                        if (childTook) MotionEvent.obtain(e).also { it.action = MotionEvent.ACTION_CANCEL; super.dispatchTouchEvent(it); it.recycle() }
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                        WindowDragger.clamp(wm, root, params)
                        runCatching { wm.updateViewLayout(root, params) }
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) { dragging = false; savePosition(); scheduleIdle(IDLE_AFTER_TOUCH); return true }
                    if (!childTook && e.actionMasked == MotionEvent.ACTION_UP) { if (active) deactivate() else activate() }
                }
            }
            return if (childTook) super.dispatchTouchEvent(e) else true
        }
    }
    private val tools = Ui.row(ctx)
    /** Her zaman görünen tutamaç: notu buradan sürükle (kalemle ya da parmakla). */
    private val grip = ImageView(ctx).apply {
        setImageDrawable(IconDrawable(Icon.GRIP, 0x99000000.toInt(), 3.2f))
        val p = dp(7f); setPadding(p, p, p, p)
        contentDescription = "Notu taşı"
    }
    private val penBtn = btn(Icon.PEN, "Not kalemi") { eraser = false; applyTool(); refresh() }
    private val eraserBtn = btn(Icon.ERASER, "Not silgisi") { eraser = !eraser; applyTool(); refresh() }
    /** Tek düğme: dokundukça mürekkep rengi değişir (koyu → mavi → kırmızı). */
    private val inkBtn = dot { inkColor = INKS[(INKS.indexOf(inkColor) + 1) % INKS.size]; eraser = false; applyTool(); refresh() }
    private val undoBtn = btn(Icon.UNDO, "Notta geri al") { ink.undo() }
    private val clearBtn = btn(Icon.TRASH, "Notu temizle") { ink.clearAll() }
    private val paperBtn = btn(Icon.PALETTE, "Kâğıt rengini değiştir") { nextPaper() }
    private val collapseBtn = btn(Icon.MINUS, "Notu küçült") { setCollapsed(true) }
    private val closeBtn = btn(Icon.CLOSE, "Notu kaldır") { showConfirm(true) }
    private val body = object : FrameLayout(ctx) {
        override fun dispatchTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { handler.removeCallbacks(idle); activate() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scheduleIdle(IDLE_AFTER_TOUCH)
            }
            return super.dispatchTouchEvent(e)
        }
    }
    val ink = InkView(ctx, editorMode = false).apply { followViewSize = true }
    private val hint = TextView(ctx).apply {
        text = "Kalemi yaklaştır\nya da dokunup yaz"
        setTextColor(0x66000000); textSize = 13f; gravity = Gravity.CENTER
        typeface = Ui.font(ctx, false)
    }
    private val resize = ImageView(ctx).apply {
        setImageDrawable(IconDrawable(Icon.RESIZE, 0x66000000, 2f))
        val p = dp(4f); setPadding(p, p, p, p)
        contentDescription = "Notu yeniden boyutlandır"
    }
    private val confirm = Ui.col(ctx)
    private val chip = ImageView(ctx).apply {
        val p = dp(13f); setPadding(p, p, p, p)
        setImageDrawable(IconDrawable(Icon.NOTE, INK_DARK, 2f))
        contentDescription = "Notu aç"
    }

    init {
        // ---- Başlık şeridi: sakinken boş (tutup sürükle), etkinken araçlar ----
        tools.setPadding(dp(3f), 0, 0, 0)
        tools.addView(penBtn); tools.addView(inkBtn); tools.addView(eraserBtn)
        tools.addView(undoBtn); tools.addView(clearBtn); tools.addView(paperBtn)
        val headRow = Ui.row(ctx)
        headRow.addView(grip, LinearLayout.LayoutParams(dp(30f), dp(36f)))
        // Araçlar düz bir satırdadır (kaydırma yok): boş kalan başlık alanı her hâlde sürüklemeye açık kalır.
        tools.clipChildren = true
        headRow.addView(tools, LinearLayout.LayoutParams(0, -1, 1f))
        headRow.addView(collapseBtn); headRow.addView(closeBtn)
        header.addView(headRow, FrameLayout.LayoutParams(-1, -1))
        // Sürükleme başlığın kendisinde (bkz. header); tutamaç yalnızca görsel ipucu.
        card.addView(header, LinearLayout.LayoutParams(-1, dp(36f)))

        // ---- Yazı alanı ----
        body.addView(ink, FrameLayout.LayoutParams(-1, -1))
        body.addView(hint, FrameLayout.LayoutParams(-1, -1))
        body.addView(resize, FrameLayout.LayoutParams(dp(26f), dp(26f), Gravity.BOTTOM or Gravity.END))
        buildConfirm()
        body.addView(confirm, FrameLayout.LayoutParams(-1, -1))
        card.addView(body, LinearLayout.LayoutParams(note.w, note.h))
        resize.setOnTouchListener(Resizer())

        ink.setPage(note.page)
        ink.onChanged = { svc.scheduleSave(); updateHint() }
        ink.onHistoryChanged = { refresh() }

        root.addView(card, FrameLayout.LayoutParams(-2, -2))
        chip.setOnTouchListener(WindowDragger(wm, root, params, onClick = { setCollapsed(false); activate() }, onDropped = { savePosition() }))
        // Dokunuşlar sürükleyiciden geçer; bu, erişilebilirlik (TalkBack) ve klavye tıklaması için.
        chip.setOnClickListener { setCollapsed(false); activate() }
        root.addView(chip, FrameLayout.LayoutParams(dp(52f), dp(52f)))

        paint(); applyTool(); deactivate()
        setCollapsed(note.collapsed, save = false)
        updateHint()
    }

    private fun isPen(e: MotionEvent): Boolean {
        val t = e.getToolType(e.actionIndex.coerceAtLeast(0))
        return t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER
    }

    // ---- Görünüm ----

    private fun btn(icon: Icon, desc: String, onClick: () -> Unit) = ImageView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(30f), dp(30f))
        val p = dp(6f); setPadding(p, p, p, p)
        setImageDrawable(IconDrawable(icon, INK_DARK, 2f))
        contentDescription = desc
        setOnClickListener { handler.removeCallbacks(idle); onClick(); scheduleIdle(IDLE_AFTER_TOUCH) }
    }

    private fun dot(onClick: () -> Unit) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { setMargins(dp(4f), 0, dp(4f), 0) }
        contentDescription = "Mürekkep rengini değiştir"
        setOnClickListener { handler.removeCallbacks(idle); onClick(); scheduleIdle(IDLE_AFTER_TOUCH) }
    }

    private fun buildConfirm() {
        confirm.gravity = Gravity.CENTER
        confirm.visibility = View.GONE
        confirm.isClickable = true // alttaki yazı alanına dokunuş geçmesin
        confirm.setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
        confirm.addView(TextView(ctx).apply {
            text = "Not kaldırılsın mı?"; setTextColor(INK_DARK); textSize = 15f; gravity = Gravity.CENTER
            typeface = Ui.font(ctx, true)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10f) })
        fun action(text: String, color: Int, fg: Int, onClick: () -> Unit) = TextView(ctx).apply {
            this.text = text; setTextColor(fg); textSize = 13f; gravity = Gravity.CENTER; maxLines = 1
            typeface = Ui.font(ctx, true)
            background = Ui.bg(color, dp(14f).toFloat())
            setPadding(dp(12f), dp(9f), dp(12f), dp(9f))
            setOnClickListener { onClick() }
        }
        confirm.addView(action("Deftere kaydet ve kaldır", INK_DARK, Color.WHITE) { svc.archive(note) },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6f) })
        confirm.addView(action("Kaldır", 0xFFD7263D.toInt(), Color.WHITE) { svc.remove(note) },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6f) })
        confirm.addView(action("Vazgeç", 0x1F000000, INK_DARK) { showConfirm(false) }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun paint() {
        val r = dp(16f).toFloat()
        val shade = ColorUtils.blendARGB(note.color, Color.BLACK, 0.08f)
        card.background = GradientDrawable().apply {
            setColor(note.color); cornerRadius = r
            setStroke(if (active) dp(2f) else dp(1f), if (active) 0x99000000.toInt() else 0x22000000)
        }
        header.background = GradientDrawable().apply { setColor(shade); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
        confirm.background = GradientDrawable().apply { setColor(ColorUtils.setAlphaComponent(note.color, 0xF2)); cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r) }
        chip.background = GradientDrawable().apply { setColor(note.color); cornerRadius = dp(16f).toFloat(); setStroke(dp(1f), 0x33000000) }
        card.elevation = if (active) dp(10f).toFloat() else dp(4f).toFloat()
        chip.elevation = dp(6f).toFloat()
    }

    /** Araç düğmelerinin seçili/etkin görünümü. */
    private fun refresh() {
        fun mark(v: View, on: Boolean) { v.background = if (on) Ui.bg(0x2A000000, dp(10f).toFloat()) else null }
        mark(penBtn, !eraser); mark(eraserBtn, eraser)
        inkBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(inkColor); setStroke(dp(2.5f), ColorUtils.setAlphaComponent(Color.WHITE, 220))
        }
        fitTools()
        undoBtn.alpha = if (ink.editor.canUndo) 1f else 0.35f
        clearBtn.alpha = if (ink.page.isEmpty) 0.35f else 1f
    }

    /** Not genişliğine göre başlıkta gösterilecek araçlar: taşan düğme olmasın, tutamaç ve ✕ hep görünsün. */
    private fun fitTools() {
        val wDp = note.w / d
        paperBtn.visibility = if (wDp >= 274) View.VISIBLE else View.GONE
        clearBtn.visibility = if (wDp >= 244) View.VISIBLE else View.GONE
        undoBtn.visibility = if (wDp >= 214) View.VISIBLE else View.GONE
    }

    private fun updateHint() { hint.visibility = if (ink.page.isEmpty && !active) View.VISIBLE else View.GONE }

    /** Kalem/silgi ayarlarını yazı alanına uygular. */
    fun applyTool() {
        val p = App.instance.prefs
        ink.tool = if (eraser) ToolType.ERASER else ToolType.PEN
        ink.color = inkColor
        ink.strokeWidth = 2.6f * d
        ink.eraserRadius = 9f * d
        ink.eraserMode = EraserMode.PARTIAL
        // Kalem modu, ancak bir kalem ucu gerçekten algılandıktan sonra parmağı engeller.
        ink.stylusOnly = p.stylusOnly && svc.stylusSeen
        ink.shapeAssist = p.shapeAssist
    }

    // ---- Hâl değişimleri ----

    /** Kalem yaklaştı ya da nota dokunuldu: yazma hâline geç; kalem seçili gelir. */
    fun activate(fresh: Boolean = false) {
        handler.removeCallbacks(idle)
        if (active) return
        active = true
        eraser = false
        applyTool()
        tools.visibility = View.VISIBLE
        paint(); refresh(); updateHint()
        if (fresh) scheduleIdle(IDLE_FRESH)
    }

    fun deactivate() {
        handler.removeCallbacks(idle)
        active = false
        tools.visibility = View.INVISIBLE
        paint(); updateHint()
    }

    private fun scheduleIdle(ms: Long) { handler.removeCallbacks(idle); handler.postDelayed(idle, ms) }

    private fun showConfirm(show: Boolean) {
        confirming = show
        confirm.visibility = if (show) View.VISIBLE else View.GONE
        if (show) activate() else scheduleIdle(IDLE_AFTER_TOUCH)
    }

    private fun nextPaper() {
        val i = StickyService.PAPERS.indexOf(note.color)
        note.color = StickyService.PAPERS[(i + 1) % StickyService.PAPERS.size]
        paint(); svc.scheduleSave()
    }

    fun setCollapsed(c: Boolean, save: Boolean = true) {
        note.collapsed = c
        card.visibility = if (c) View.GONE else View.VISIBLE
        chip.visibility = if (c) View.VISIBLE else View.GONE
        if (c) { showConfirm(false); deactivate() }
        root.post { clamp() }
        if (save) svc.scheduleSave()
    }

    private fun savePosition() { note.x = params.x; note.y = params.y; svc.scheduleSave() }

    // ---- Pencere ----

    fun show(): Boolean {
        if (attached) return true
        attached = runCatching { wm.addView(root, params); true }.getOrDefault(false)
        if (attached) root.post { clamp() }
        return attached
    }

    fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        if (!attached) return
        attached = false
        runCatching { wm.removeView(root) }
    }

    /** Pencereyi diğer katmanların üstüne taşır. */
    fun raise() {
        if (!attached) return
        runCatching { wm.removeView(root); wm.addView(root, params) }
    }

    fun clamp() {
        if (!attached) return
        val x = params.x; val y = params.y
        WindowDragger.clamp(wm, root, params)
        if (x != params.x || y != params.y) { runCatching { wm.updateViewLayout(root, params) }; savePosition() }
    }

    /** Sağ alt köşeden sürükleyerek yazı alanını büyütür/küçültür. Yazı yerinde kalır. */
    private inner class Resizer : View.OnTouchListener {
        private var sx = 0f; private var sy = 0f; private var sw = 0; private var sh = 0
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; sw = note.w; sh = note.h; activate() }
                MotionEvent.ACTION_MOVE -> {
                    val (scrW, scrH) = WindowDragger.screenSize(wm)
                    val w = (sw + (e.rawX - sx)).toInt().coerceIn(dp(MIN_W), scrW - params.x)
                    val h = (sh + (e.rawY - sy)).toInt().coerceIn(dp(MIN_H), scrH - params.y - dp(36f))
                    if (abs(w - note.w) + abs(h - note.h) < 2) return true
                    note.w = w; note.h = h
                    fitTools()
                    body.layoutParams = (body.layoutParams as LinearLayout.LayoutParams).apply { width = w; height = h }
                    runCatching { wm.updateViewLayout(root, params) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { svc.scheduleSave(); scheduleIdle(IDLE_AFTER_TOUCH) }
            }
            return true
        }
    }

    companion object {
        const val INK_DARK = 0xFF1B1E26.toInt()
        /** Mürekkep: koyu, mavi, kırmızı. */
        val INKS = intArrayOf(INK_DARK, 0xFF1E5BD8.toInt(), 0xFFD7263D.toInt())
        const val IDLE_AFTER_HOVER = 2500L
        const val IDLE_AFTER_TOUCH = 4000L
        const val IDLE_FRESH = 6000L
        const val MIN_W = 150f
        const val MIN_H = 110f
    }
}
