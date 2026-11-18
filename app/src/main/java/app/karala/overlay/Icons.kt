package app.karala.overlay

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser

/** Ekran üstü araç çubuğu için çizgi ikonlar (24×24 görünüm alanı, SVG yol verisi). */
enum class Icon(val d: String) {
    PEN("M4 20l1-4L16.5 4.5a2.1 2.1 0 0 1 3 3L8 19l-4 1z M14.5 6.5l3 3"),
    HIGHLIGHTER("M10 13L17.5 5.5a2.1 2.1 0 0 1 3 3L13 16z M10 13l-3 3 1 3h3l2-3 M4 21.5h9"),
    ERASER("M20 20H9l-5-5a1.5 1.5 0 0 1 0-2.1l9.4-9.4a1.5 1.5 0 0 1 2.1 0l5 5a1.5 1.5 0 0 1 0 2.1L11 20 M8 9l7 7"),
    LINE("M5 19L19 5"),
    ARROW("M5 19L19 5 M10 5h9v9"),
    RECT("M4 6h16v12H4z"),
    ELLIPSE("M4 12a8 6.5 0 1 0 16 0a8 6.5 0 1 0 -16 0z"),
    SHAPES("M3 13h8v8H3z M13.5 17a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0z M12 3l4.5 7.5h-9z"),
    LASER("M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z M12 3v3 M12 18v3 M3 12h3 M18 12h3 M5.6 5.6l2.1 2.1 M16.3 16.3l2.1 2.1 M5.6 18.4l2.1-2.1 M16.3 7.7l2.1-2.1"),
    UNDO("M9 14L4 9l5-5 M4 9h10a6 6 0 0 1 0 12h-3"),
    REDO("M15 14l5-5-5-5 M20 9H10a6 6 0 0 0 0 12h3"),
    TRASH("M4 7h16 M10 11v6 M14 11v6 M6 7l1 13h10l1-13 M9 7V4h6v3"),
    PLUS("M12 5v14 M5 12h14"),
    LEFT("M15 6l-6 6 6 6"),
    RIGHT("M9 6l6 6-6 6"),
    POINTER("M5 3l14 8-6 2-2 6z"),
    EYE("M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z"),
    EYE_OFF("M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z M4 4l16 16"),
    SAVE("M6 3h12v18l-6-4-6 4z"),
    BOARD("M3 5h18v12H3z M8 21l4-4 4 4"),
    TIMER("M4 13a8 8 0 1 0 16 0a8 8 0 1 0 -16 0z M12 9v4l2.5 2.5 M9 2h6"),
    CALC("M5 3h14v18H5z M8 7h8 M8 12h.01 M12 12h.01 M16 12h.01 M8 16h.01 M12 16h.01 M16 16h.01"),
    CLOSE("M6 6l12 12 M18 6L6 18"),
    COLLAPSE("M8 3v5H3 M21 8h-5V3 M3 16h5v5 M16 21v-5h5"),
    CAMERA("M4 8h3l2-3h6l2 3h3v11H4z M8.5 13.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0z"),
    WIDGETS("M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z"),
    CHECK("M5 12.5l4.5 4.5L19 7.5"),
    TALLY("M6 5v14 M10 5v14 M14 5v14 M3 16L18 8"),
    PLAY("M7 4l13 8-13 8z"),
    PAUSE("M8 5v14 M16 5v14"),
    FLAG("M5 21V4 M5 4h13l-3 4.5 3 4.5H5"),
    RESET("M4 5v5h5 M5 10a8 8 0 1 1 -1 4"),
    SKIP("M6 5l9 7-9 7z M18 5v14"),
    HOME("M4 11l8-7 8 7 M6 10v10h12V10"),
    HAND("M9 11.5V5.5a1.5 1.5 0 0 1 3 0V10 M12 10V4.5a1.5 1.5 0 0 1 3 0V10.5 M15 10.5V6.5a1.5 1.5 0 0 1 3 0V15a6 6 0 0 1-6 6h-.8a6 6 0 0 1-4.7-2.3L4.4 14.6a1.5 1.5 0 0 1 2.3-1.9L9 15V11.5"),
    STYLUS("M5 19l1.2-4.2L16.5 4.5a2.1 2.1 0 0 1 3 3L9.2 17.8 5 19z M14.5 6.5l3 3 M12 21h.01 M15.5 21h.01 M19 21h.01"),
    NOTE("M5 4h14v10.5L13.5 20H5z M13 20v-5.5a.5.5 0 0 1 .5-.5H19 M8.5 8.5h7 M8.5 12h4"),
    GRIP("M9 6h.01 M15 6h.01 M9 12h.01 M15 12h.01 M9 18h.01 M15 18h.01"),
    MINUS("M6 12h12"),
    RESIZE("M20 11l-9 9 M20 16.5l-3.5 3.5"),
    PALETTE("M12 3a9 9 0 1 0 0 18c1 0 1.6-.7 1.6-1.5 0-.4-.2-.8-.4-1.1-.3-.3-.4-.7-.4-1.1 0-.8.7-1.5 1.5-1.5H16a5 5 0 0 0 5-5c0-4.3-4-7.8-9-7.8z M7.5 11h.01 M10 7.5h.01 M14.5 7.5h.01"),
    TOMATO("M5 13a7 7 0 1 0 14 0a7 7 0 1 0 -14 0z M12 6V3 M12 6l-3-2 M12 6l3-2"),
}

class IconDrawable(icon: Icon, color: Int, private val strokeDp: Float = 1.8f) : Drawable() {
    private val src: Path = PathParser.createPathFromPathData(icon.d)
    private val path = Path()
    private val m = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = color
    }

    fun setColor(c: Int) { paint.color = c; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()) / 24f
        m.setScale(s, s)
        m.postTranslate(b.left + (b.width() - 24f * s) / 2f, b.top + (b.height() - 24f * s) / 2f)
        src.transform(m, path)
        paint.strokeWidth = strokeDp * s
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = -1
    override fun getIntrinsicHeight() = -1
}
