package app.karala.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Çizgileri ve sayfa zeminlerini Android Canvas'a çizer. Ekran, küçük resim ve dışa aktarma aynı kodu kullanır. */
class InkRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()

    fun drawStroke(c: Canvas, s: Stroke) {
        if (s.size == 0) return
        paint.color = s.color
        paint.strokeWidth = s.width
        paint.strokeCap = Paint.Cap.ROUND
        when (s.tool) {
            ToolType.HIGHLIGHTER -> {
                paint.alpha = HIGHLIGHT_ALPHA
                paint.strokeCap = Paint.Cap.SQUARE
                drawFreehand(c, s, false)
            }
            ToolType.PEN -> drawFreehand(c, s, s.pressures != null)
            ToolType.LINE -> if (s.size >= 2) c.drawLine(s.x(0), s.y(0), s.x(1), s.y(1), paint)
            ToolType.ARROW -> if (s.size >= 2) drawArrow(c, s)
            ToolType.RECT -> if (s.size >= 2) {
                rect.set(min(s.x(0), s.x(1)), min(s.y(0), s.y(1)), max(s.x(0), s.x(1)), max(s.y(0), s.y(1)))
                c.drawRect(rect, paint)
            }
            ToolType.ELLIPSE -> if (s.size >= 2) {
                rect.set(min(s.x(0), s.x(1)), min(s.y(0), s.y(1)), max(s.x(0), s.x(1)), max(s.y(0), s.y(1)))
                c.drawOval(rect, paint)
            }
            ToolType.LASER, ToolType.ERASER -> {}
        }
    }

    private fun drawFreehand(c: Canvas, s: Stroke, variable: Boolean) {
        val n = s.size
        if (n == 1) {
            fill.color = paint.color; fill.alpha = paint.alpha
            c.drawCircle(s.x(0), s.y(0), s.width / 2f, fill); return
        }
        if (variable) {
            val pr = s.pressures!!
            for (i in 0 until n - 1) {
                val p = (pr[i] + pr[i + 1]) / 2f
                paint.strokeWidth = s.width * (0.4f + 0.9f * p.coerceIn(0f, 1f))
                c.drawLine(s.x(i), s.y(i), s.x(i + 1), s.y(i + 1), paint)
            }
            return
        }
        buildSmoothPath(s.points, n, path)
        c.drawPath(path, paint)
    }

    private fun drawArrow(c: Canvas, s: Stroke) {
        val x0 = s.x(0); val y0 = s.y(0); val x1 = s.x(1); val y1 = s.y(1)
        c.drawLine(x0, y0, x1, y1, paint)
        val ang = atan2(y1 - y0, x1 - x0)
        val head = max(14f, s.width * 4f)
        val a = 0.45f
        path.rewind()
        path.moveTo(x1 - head * cos(ang - a), y1 - head * sin(ang - a))
        path.lineTo(x1, y1)
        path.lineTo(x1 - head * cos(ang + a), y1 - head * sin(ang + a))
        c.drawPath(path, paint)
    }

    fun drawStrokes(c: Canvas, strokes: List<Stroke>) { for (s in strokes) drawStroke(c, s) }

    /** Sayfa zeminini (0,0)-(w,h) alanına çizer. [unit] çizgi/kare aralığıdır. */
    fun drawBackground(c: Canvas, bg: PageBackground, w: Float, h: Float, unit: Float, alpha: Int = 255) {
        if (bg == PageBackground.NONE) return
        fill.color = if (bg == PageBackground.BLACK) BOARD_BLACK else Color.WHITE
        fill.alpha = alpha
        c.drawRect(0f, 0f, w, h, fill)
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeWidth = max(1f, unit / 28f)
        paint.color = RULE_COLOR
        paint.alpha = (alpha * 0.9f).toInt()
        when (bg) {
            PageBackground.GRID -> {
                var x = unit; while (x < w) { c.drawLine(x, 0f, x, h, paint); x += unit }
                var y = unit; while (y < h) { c.drawLine(0f, y, w, y, paint); y += unit }
            }
            PageBackground.LINED -> {
                val step = unit * 1.3f
                var y = step * 1.5f; while (y < h) { c.drawLine(0f, y, w, y, paint); y += step }
            }
            PageBackground.DOTS -> {
                fill.color = DOT_COLOR; fill.alpha = alpha
                var y = unit
                while (y < h) { var x = unit; while (x < w) { c.drawCircle(x, y, unit / 14f, fill); x += unit }; y += unit }
            }
            else -> {}
        }
        paint.strokeCap = Paint.Cap.ROUND
    }

    /**
     * Sayfayı (zemin + varsa görsel + mürekkep) bitmap'e çizer. Dışa aktarma ve küçük resimler için.
     * Şeffaf zeminli ve görselsiz sayfalar [emptyColor] üzerine çizilir.
     */
    fun renderPage(page: InkPage, bgImage: Bitmap?, targetWidth: Int, emptyColor: Int = BOARD_BLACK, withInk: Boolean = true): Bitmap {
        val pw = max(1, if (page.width > 0) page.width else bgImage?.width ?: 1080)
        val ph = max(1, if (page.height > 0) page.height else bgImage?.height ?: 1920)
        val scale = targetWidth / pw.toFloat()
        val out = Bitmap.createBitmap(targetWidth, max(1, (ph * scale).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(emptyColor)
        c.scale(scale, scale)
        if (bgImage != null) {
            rect.set(0f, 0f, pw.toFloat(), ph.toFloat())
            c.drawBitmap(bgImage, null, rect, bitmapPaint)
        }
        drawBackground(c, page.background, pw.toFloat(), ph.toFloat(), pw / 22f)
        if (withInk) drawStrokes(c, page.strokes)
        return out
    }

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    companion object {
        const val HIGHLIGHT_ALPHA = 96
        val BOARD_BLACK = Color.rgb(22, 24, 29)
        val RULE_COLOR = Color.rgb(150, 180, 215)
        val DOT_COLOR = Color.rgb(140, 150, 165)

        /** Noktaları orta noktalardan geçen ikinci dereceden eğrilerle yumuşatır. */
        fun buildSmoothPath(pts: FloatArray, n: Int, out: Path) {
            out.rewind()
            if (n == 0) return
            out.moveTo(pts[0], pts[1])
            if (n == 2) { out.lineTo(pts[2], pts[3]); return }
            for (i in 1 until n - 1) {
                val mx = (pts[i * 2] + pts[i * 2 + 2]) / 2f
                val my = (pts[i * 2 + 1] + pts[i * 2 + 3]) / 2f
                out.quadTo(pts[i * 2], pts[i * 2 + 1], mx, my)
            }
            out.lineTo(pts[(n - 1) * 2], pts[(n - 1) * 2 + 1])
        }

        /** Sayfayı görünüme sığdıran dönüşüm (ortalanmış, oran korunur). */
        fun fitMatrix(pageW: Int, pageH: Int, viewW: Int, viewH: Int, out: Matrix) {
            out.reset()
            if (pageW <= 0 || pageH <= 0 || viewW <= 0 || viewH <= 0) return
            if (pageW == viewW && pageH == viewH) return
            val s = min(viewW / pageW.toFloat(), viewH / pageH.toFloat())
            out.setScale(s, s)
            out.postTranslate((viewW - pageW * s) / 2f, (viewH - pageH * s) / 2f)
        }
    }
}
