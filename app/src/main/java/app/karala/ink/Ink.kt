package app.karala.ink

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Çizim araçları. LASER kalıcı değildir, sayfaya kaydedilmez. */
enum class ToolType { PEN, HIGHLIGHTER, ERASER, LINE, ARROW, RECT, ELLIPSE, LASER;
    val isShape get() = this == LINE || this == ARROW || this == RECT || this == ELLIPSE
    val persists get() = this != ERASER && this != LASER
}

/** Sayfa zemini. NONE = şeffaf (alttaki uygulama görünür). */
enum class PageBackground { NONE, WHITE, BLACK, GRID, LINED, DOTS }

enum class EraserMode { STROKE, PARTIAL }

/**
 * Tek bir çizgi. [points] x,y çiftleridir (sayfa koordinatı).
 * Şekil araçlarında tam 2 nokta bulunur: başlangıç ve bitiş.
 * [pressures] varsa nokta başına 0..1 basınç değeridir (kalem uçlu cihazlar).
 */
class Stroke(
    val tool: ToolType,
    val color: Int,
    val width: Float,
    val points: FloatArray,
    val pressures: FloatArray? = null,
) {
    val size: Int get() = points.size / 2
    fun x(i: Int) = points[i * 2]
    fun y(i: Int) = points[i * 2 + 1]

    /** [left, top, right, bottom] – kalınlık dahil. */
    fun bounds(): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until size) {
            l = min(l, x(i)); r = max(r, x(i)); t = min(t, y(i)); b = max(b, y(i))
        }
        val h = width / 2f
        return floatArrayOf(l - h, t - h, r + h, b + h)
    }
}

class InkPage(
    var width: Int = 0,
    var height: Int = 0,
    val strokes: MutableList<Stroke> = mutableListOf(),
    var background: PageBackground = PageBackground.NONE,
) {
    val isEmpty get() = strokes.isEmpty()
    fun copy() = InkPage(width, height, strokes.toMutableList(), background)
}

object Geometry {
    /** p noktasının a-b doğru parçasına uzaklığı. */
    fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** Şekil çizgilerini (dikdörtgen/elips/ok) parça parça çizgi listesine açar: x,y çiftleri, kapalı yol. */
    fun outline(s: Stroke): FloatArray {
        if (s.size < 2) return s.points
        val x0 = s.x(0); val y0 = s.y(0); val x1 = s.x(1); val y1 = s.y(1)
        return when (s.tool) {
            ToolType.RECT -> floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1, x0, y0)
            ToolType.ELLIPSE -> {
                val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2
                val rx = abs(x1 - x0) / 2; val ry = abs(y1 - y0) / 2
                val n = 36
                FloatArray((n + 1) * 2) { i ->
                    val a = (i / 2) * (2 * Math.PI / n)
                    if (i % 2 == 0) (cx + rx * Math.cos(a)).toFloat() else (cy + ry * Math.sin(a)).toFloat()
                }
            }
            else -> s.points
        }
    }

    /** (x,y) merkezli r yarıçaplı silgi bu çizgiye dokunuyor mu? */
    fun hits(s: Stroke, x: Float, y: Float, r: Float): Boolean {
        val reach = r + s.width / 2f
        val b = s.bounds()
        if (x < b[0] - r || x > b[2] + r || y < b[1] - r || y > b[3] + r) return false
        val pts = if (s.tool == ToolType.RECT || s.tool == ToolType.ELLIPSE) outline(s) else s.points
        val n = pts.size / 2
        if (n == 1) return hypot(x - pts[0], y - pts[1]) <= reach
        for (i in 0 until n - 1) {
            if (distToSegment(x, y, pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3]) <= reach) return true
        }
        return false
    }

    /**
     * Parçalı silgi: serbest çizgiden silginin değdiği noktaları çıkarır ve kalan parçaları döndürür.
     * Çizgi etkilenmediyse null döner. Şekiller bütün olarak silinir (boş liste).
     */
    fun erasePartial(s: Stroke, x: Float, y: Float, r: Float): List<Stroke>? {
        if (!hits(s, x, y, r)) return null
        if (s.tool != ToolType.PEN && s.tool != ToolType.HIGHLIGHTER) return emptyList()
        val reach = r + s.width / 2f
        val dense = densify(s, max(2f, r / 2f))
        val out = ArrayList<Stroke>()
        var curP = ArrayList<Float>(); var curPr = ArrayList<Float>()
        fun flush() {
            if (curP.size >= 4) out.add(
                Stroke(s.tool, s.color, s.width, curP.toFloatArray(), if (s.pressures != null) curPr.toFloatArray() else null)
            )
            curP = ArrayList(); curPr = ArrayList()
        }
        for (i in 0 until dense.size) {
            val px = dense.x(i); val py = dense.y(i)
            if (hypot(px - x, py - y) <= reach) flush()
            else { curP.add(px); curP.add(py); curPr.add(dense.pressures?.get(i) ?: 1f) }
        }
        flush()
        return out
    }

    /** Noktalar arası mesafe [step]'i aşmayacak şekilde ara nokta ekler. */
    fun densify(s: Stroke, step: Float): Stroke {
        if (s.size < 2) return s
        val p = ArrayList<Float>(); val pr = ArrayList<Float>()
        for (i in 0 until s.size - 1) {
            val ax = s.x(i); val ay = s.y(i); val bx = s.x(i + 1); val by = s.y(i + 1)
            val pa = s.pressures?.get(i) ?: 1f; val pb = s.pressures?.get(i + 1) ?: 1f
            val d = hypot(bx - ax, by - ay)
            val n = max(1, Math.ceil((d / step).toDouble()).toInt())
            for (k in 0 until n) {
                val t = k / n.toFloat()
                p.add(ax + (bx - ax) * t); p.add(ay + (by - ay) * t); pr.add(pa + (pb - pa) * t)
            }
        }
        p.add(s.x(s.size - 1)); p.add(s.y(s.size - 1)); pr.add(s.pressures?.get(s.size - 1) ?: 1f)
        return Stroke(s.tool, s.color, s.width, p.toFloatArray(), if (s.pressures != null) pr.toFloatArray() else null)
    }

    fun pathLength(pts: FloatArray): Float {
        var len = 0f
        for (i in 0 until pts.size / 2 - 1) len += hypot(pts[i * 2 + 2] - pts[i * 2], pts[i * 2 + 3] - pts[i * 2 + 1])
        return len
    }

    /**
     * "Basılı tut → düzelt": serbest el çizimini doğru, dikdörtgen veya elipse çevirir.
     * Tanınamazsa null döner.
     */
    fun recognize(s: Stroke): Stroke? {
        if (s.tool != ToolType.PEN && s.tool != ToolType.HIGHLIGHTER) return null
        val n = s.size
        if (n < 3) return null
        val x0 = s.x(0); val y0 = s.y(0); val x1 = s.x(n - 1); val y1 = s.y(n - 1)
        val len = pathLength(s.points)
        if (len < 24f) return null
        val chord = hypot(x1 - x0, y1 - y0)

        // Doğru: yol uzunluğu kirişe yakın ve noktalar kirişten çok sapmıyor.
        if (chord > 0f && len / chord < 1.12f) {
            var maxDev = 0f
            for (i in 0 until n) maxDev = max(maxDev, distToSegment(s.x(i), s.y(i), x0, y0, x1, y1))
            if (maxDev < max(10f, chord * 0.06f)) {
                var ex = x1; var ey = y1
                // Yataya/dikeye çok yakınsa tam hizala.
                if (abs(y1 - y0) < chord * 0.05f) ey = y0
                if (abs(x1 - x0) < chord * 0.05f) ex = x0
                return Stroke(ToolType.LINE, s.color, s.width, floatArrayOf(x0, y0, ex, ey))
            }
        }

        // Kapalı şekil: uçlar birbirine yakın.
        val b = s.bounds(); val h = s.width / 2f
        val l = b[0] + h; val t = b[1] + h; val r = b[2] - h; val bt = b[3] - h
        val w = r - l; val ht = bt - t
        if (w < 16f || ht < 16f) return null
        if (chord > 0.25f * max(w, ht)) return null
        // Shoelace alanı / sınır kutusu alanı: dikdörtgen ≈ 1, elips ≈ π/4.
        var area = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += s.x(i) * s.y(j) - s.x(j) * s.y(i)
        }
        val ratio = abs(area) / 2f / (w * ht)
        return when {
            ratio > 0.88f -> Stroke(ToolType.RECT, s.color, s.width, floatArrayOf(l, t, r, bt))
            ratio > 0.62f -> Stroke(ToolType.ELLIPSE, s.color, s.width, floatArrayOf(l, t, r, bt))
            else -> null
        }
    }
}
