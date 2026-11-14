package app.karala.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max

/**
 * Çizim yüzeyi. Hem ekran üstü katmanda (overlay) hem de uygulama içi defter düzenleyicide kullanılır.
 *
 * - İşlenmiş çizgiler bir bitmap önbelleğinde tutulur; çizim sırasında yalnızca aktif çizgi yeniden çizilir.
 * - Dokunuş koordinatları sayfa koordinatına çevrilir; sayfa, görünüme oran korunarak sığdırılır.
 */
@SuppressLint("ViewConstructor")
class InkView(context: Context, private val editorMode: Boolean) : View(context) {

    val editor = InkEditor()
    private val renderer = InkRenderer()
    private val density = resources.displayMetrics.density

    // ---- Araç durumu ----
    var tool: ToolType = ToolType.PEN
    var color: Int = Color.WHITE
    /** Aktif aracın kalınlığı (sayfa pikseli). */
    var strokeWidth: Float = 4f * density
    var eraserRadius: Float = 14f * density
    var eraserMode: EraserMode = EraserMode.STROKE
    /** Açıkken yalnızca kalem ucu çizer; parmak/avuç içi yok sayılır. */
    var stylusOnly: Boolean = false
    /** "Basılı tut → şekle çevir" */
    var shapeAssist: Boolean = true
    /** Katmanda tahta zeminlerinin opaklığı (0..255). */
    var boardAlpha: Int = 255
        set(v) { field = v; invalidate() }
    var inkVisible: Boolean = true
        set(v) { field = v; invalidate() }
    var backgroundBitmap: Bitmap? = null
        set(v) { field = v; invalidate() }

    /** İçerik değiştiğinde (kaydetme tetiklemek için). */
    var onChanged: (() -> Unit)? = null
    /** Geri al/yinele durumu değiştiğinde. */
    var onHistoryChanged: (() -> Unit)? = null

    // ---- İç durum ----
    private var cache: Bitmap? = null
    private var cacheCanvas: Canvas? = null
    private var cacheDirty = true
    private val toView = Matrix()
    private val toPage = Matrix()
    private val pageRect = RectF()
    private val tmp = FloatArray(2)

    private var activePointer = -1
    private var livePts = FloatArray(256)
    private var livePr = FloatArray(128)
    private var liveN = 0
    private var liveTool: ToolType? = null
    private var liveHasPressure = false
    private var eraseChanged = false
    private var eraserX = 0f; private var eraserY = 0f; private var erasing = false
    private val livePath = Path()
    private val livePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val letterboxColor = Color.rgb(12, 13, 16)

    private class Laser(val pts: FloatArray, val n: Int, val color: Int, val width: Float, var endedAt: Long)
    private val lasers = ArrayList<Laser>()

    private var holdX = 0f; private var holdY = 0f
    private val holdRunnable = Runnable { tryShapeAssist() }

    fun setPage(page: InkPage) {
        editor.load(page)
        adoptViewSizeIfNeeded()
        updateMatrix(); cacheDirty = true; invalidate()
        onHistoryChanged?.invoke()
    }

    val page: InkPage get() = editor.page

    fun undo() { if (editor.undo()) contentChanged() }
    fun redo() { if (editor.redo()) contentChanged() }
    fun clearAll() { editor.clear(); contentChanged() }
    fun setPageBackground(bg: PageBackground) { editor.page.background = bg; invalidate(); onChanged?.invoke() }

    private fun contentChanged() {
        cacheDirty = true; invalidate(); onChanged?.invoke(); onHistoryChanged?.invoke()
    }

    private fun adoptViewSizeIfNeeded() {
        val p = editor.page
        if (width > 0 && height > 0 && (p.width <= 0 || p.height <= 0 || (!editorMode && p.isEmpty))) {
            p.width = width; p.height = height
        }
    }

    private fun updateMatrix() {
        val p = editor.page
        InkRenderer.fitMatrix(p.width, p.height, width, height, toView)
        toView.invert(toPage)
        pageRect.set(0f, 0f, p.width.toFloat(), p.height.toFloat())
        toView.mapRect(pageRect)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cache?.recycle(); cache = null; cacheCanvas = null
        if (w > 0 && h > 0) {
            adoptViewSizeIfNeeded()
            cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { cacheCanvas = Canvas(it) }
        }
        updateMatrix(); cacheDirty = true
    }

    private fun rebuildCache() {
        val c = cacheCanvas ?: return
        cache?.eraseColor(Color.TRANSPARENT)
        c.save(); c.concat(toView)
        renderer.drawStrokes(c, editor.page.strokes)
        c.restore()
        cacheDirty = false
    }

    override fun onDraw(canvas: Canvas) {
        val p = editor.page
        if (editorMode) canvas.drawColor(letterboxColor)

        canvas.save(); canvas.concat(toView)
        if (editorMode) canvas.clipRect(0f, 0f, p.width.toFloat(), p.height.toFloat())
        backgroundBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, null, RectF(0f, 0f, p.width.toFloat(), p.height.toFloat()), bitmapPaint)
        }
        if (p.background != PageBackground.NONE) {
            renderer.drawBackground(canvas, p.background, p.width.toFloat(), p.height.toFloat(),
                max(p.width, p.height) / 36f, boardAlpha)
        } else if (editorMode && backgroundBitmap == null) {
            canvas.drawColor(InkRenderer.BOARD_BLACK)
        }
        canvas.restore()

        if (inkVisible) {
            if (cacheDirty) rebuildCache()
            cache?.let { canvas.drawBitmap(it, 0f, 0f, null) }
            drawLive(canvas)
        }
        drawLasers(canvas)
        if (erasing) {
            cursorPaint.color = Color.WHITE
            canvas.drawCircle(eraserX, eraserY, eraserRadius * currentScale(), cursorPaint)
            cursorPaint.color = Color.BLACK
            canvas.drawCircle(eraserX, eraserY, eraserRadius * currentScale() + cursorPaint.strokeWidth, cursorPaint)
        }
    }

    private fun currentScale(): Float { tmp[0] = 1f; tmp[1] = 0f; toView.mapVectors(tmp); return hypot(tmp[0], tmp[1]) }

    private fun drawLive(canvas: Canvas) {
        val t = liveTool ?: return
        if (liveN == 0 || t == ToolType.ERASER || t == ToolType.LASER) return
        canvas.save(); canvas.concat(toView)
        renderer.drawStroke(canvas, snapshotLive(t))
        canvas.restore()
    }

    private fun snapshotLive(t: ToolType): Stroke {
        return if (t.isShape) {
            Stroke(t, color, strokeWidth, floatArrayOf(livePts[0], livePts[1], livePts[(liveN - 1) * 2], livePts[(liveN - 1) * 2 + 1]))
        } else {
            Stroke(t, color, strokeWidth, livePts.copyOf(liveN * 2), if (liveHasPressure && t == ToolType.PEN) livePr.copyOf(liveN) else null)
        }
    }

    private fun drawLasers(canvas: Canvas) {
        val live = liveTool == ToolType.LASER && liveN > 0
        if (lasers.isEmpty() && !live) return
        val now = SystemClock.uptimeMillis()
        val it = lasers.iterator()
        canvas.save(); canvas.concat(toView)
        var alive = false
        if (live) {
            InkRenderer.buildSmoothPath(livePts, liveN, livePath)
            livePaint.color = color; livePaint.alpha = 110; livePaint.strokeWidth = strokeWidth * 2.6f
            canvas.drawPath(livePath, livePaint)
            livePaint.color = Color.WHITE; livePaint.alpha = 255; livePaint.strokeWidth = strokeWidth * 0.8f
            canvas.drawPath(livePath, livePaint)
        }
        while (it.hasNext()) {
            val l = it.next()
            val a = if (l.endedAt == 0L) 1f else 1f - (now - l.endedAt) / LASER_FADE_MS
            if (a <= 0f) { it.remove(); continue }
            alive = true
            InkRenderer.buildSmoothPath(l.pts, l.n, livePath)
            livePaint.color = l.color; livePaint.alpha = (110 * a).toInt(); livePaint.strokeWidth = l.width * 2.6f
            canvas.drawPath(livePath, livePaint)
            livePaint.color = Color.WHITE; livePaint.alpha = (255 * a).toInt(); livePaint.strokeWidth = l.width * 0.8f
            canvas.drawPath(livePath, livePaint)
        }
        canvas.restore()
        if (alive) postInvalidateOnAnimation()
    }

    // ---- Dokunma ----

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val type = e.getToolType(0)
                val isStylus = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
                if (stylusOnly && !isStylus) return true
                activePointer = e.getPointerId(0)
                val stylusErase = type == MotionEvent.TOOL_TYPE_ERASER ||
                    (isStylus && (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0)
                liveTool = if (stylusErase) ToolType.ERASER else tool
                liveHasPressure = isStylus
                liveN = 0
                parent?.requestDisallowInterceptTouchEvent(true)
                if (liveTool == ToolType.ERASER) { editor.beginErase(); erasing = true }
                addPoint(e.x, e.y, e.pressure, e.x, e.y)
                armHold(e.x, e.y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = e.findPointerIndex(activePointer)
                if (idx < 0 || liveTool == null) return true
                for (h in 0 until e.historySize) {
                    addPoint(e.getHistoricalX(idx, h), e.getHistoricalY(idx, h), e.getHistoricalPressure(idx, h), e.getX(idx), e.getY(idx))
                }
                addPoint(e.getX(idx), e.getY(idx), e.getPressure(idx), e.getX(idx), e.getY(idx))
                if (hypot(e.getX(idx) - holdX, e.getY(idx) - holdY) > HOLD_SLOP_DP * density) armHold(e.getX(idx), e.getY(idx))
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finishStroke()
            MotionEvent.ACTION_POINTER_UP -> if (e.getPointerId(e.actionIndex) == activePointer) finishStroke()
        }
        return true
    }

    private fun armHold(x: Float, y: Float) {
        holdX = x; holdY = y
        removeCallbacks(holdRunnable)
        if (shapeAssist && (liveTool == ToolType.PEN || liveTool == ToolType.HIGHLIGHTER)) postDelayed(holdRunnable, HOLD_MS)
    }

    private fun addPoint(vx: Float, vy: Float, pressure: Float, cursorX: Float, cursorY: Float) {
        tmp[0] = vx; tmp[1] = vy; toPage.mapPoints(tmp)
        val x = tmp[0]; val y = tmp[1]
        if (liveTool == ToolType.ERASER) {
            eraserX = cursorX; eraserY = cursorY
            // Hızlı harekette boşluk kalmasın diye önceki noktadan bu noktaya ara adımlarla sil.
            var changed = false
            if (liveN > 0) {
                val lx = livePts[0]; val ly = livePts[1]
                val d = hypot(x - lx, y - ly)
                val steps = max(1, (d / max(1f, eraserRadius * 0.6f)).toInt())
                for (i in 1..steps) {
                    val t = i / steps.toFloat()
                    if (editor.eraseAt(lx + (x - lx) * t, ly + (y - ly) * t, eraserRadius, eraserMode)) changed = true
                }
            } else changed = editor.eraseAt(x, y, eraserRadius, eraserMode)
            livePts[0] = x; livePts[1] = y; liveN = 1
            if (changed) { cacheDirty = true; eraseChanged = true }
            return
        }
        if (liveN > 0) {
            val lx = livePts[(liveN - 1) * 2]; val ly = livePts[(liveN - 1) * 2 + 1]
            if (hypot(x - lx, y - ly) < MIN_DIST) return
        }
        if ((liveN + 1) * 2 > livePts.size) { livePts = livePts.copyOf(livePts.size * 2); livePr = livePr.copyOf(livePr.size * 2) }
        livePts[liveN * 2] = x; livePts[liveN * 2 + 1] = y
        livePr[liveN] = pressure.coerceIn(0f, 1f)
        liveN++
    }

    private fun tryShapeAssist() {
        val t = liveTool ?: return
        if (liveN < 3 || (t != ToolType.PEN && t != ToolType.HIGHLIGHTER)) return
        val shape = Geometry.recognize(Stroke(t, color, strokeWidth, livePts.copyOf(liveN * 2))) ?: return
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        editor.add(shape)
        liveTool = null; liveN = 0; activePointer = -1
        commitToCache(shape)
        onChanged?.invoke(); onHistoryChanged?.invoke()
        invalidate()
    }

    private fun finishStroke() {
        removeCallbacks(holdRunnable)
        val t = liveTool
        activePointer = -1; liveTool = null
        if (t == null) { invalidate(); return }
        when {
            t == ToolType.ERASER -> {
                erasing = false
                editor.endErase()
                if (eraseChanged) { onChanged?.invoke(); onHistoryChanged?.invoke() }
                eraseChanged = false
            }
            t == ToolType.LASER -> {
                if (liveN > 0) lasers.add(Laser(livePts.copyOf(liveN * 2), liveN, color, strokeWidth, SystemClock.uptimeMillis()))
            }
            liveN > 0 -> {
                // Şekil araçlarında çok küçük sürüklemeler yanlışlıkla dokunuştur; yok say.
                val tooSmall = t.isShape && hypot(livePts[(liveN - 1) * 2] - livePts[0], livePts[(liveN - 1) * 2 + 1] - livePts[1]) < 4f * density
                if (!tooSmall) {
                    val s = snapshotLive(t)
                    editor.add(s); commitToCache(s)
                    onChanged?.invoke(); onHistoryChanged?.invoke()
                }
            }
        }
        liveN = 0
        invalidate()
    }

    private fun commitToCache(s: Stroke) {
        val c = cacheCanvas
        if (c == null || cacheDirty) { cacheDirty = true; return }
        c.save(); c.concat(toView); renderer.drawStroke(c, s); c.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(holdRunnable)
    }

    companion object {
        private const val MIN_DIST = 1.2f
        private const val HOLD_MS = 650L
        private const val HOLD_SLOP_DP = 7f
        private const val LASER_FADE_MS = 1100f
    }
}
