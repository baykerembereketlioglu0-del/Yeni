package com.jarvis.asistan.ui

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.jarvis.asistan.Mode
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Duruma göre renk ve hız değiştiren animasyonlu ark reaktörü. */
class ArcReactorView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var mode: Mode = Mode.OFF
        set(value) { field = value; invalidate() }
    var level: Float = 0f

    private var t = 0f
    private var smoothLevel = 0f
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    private fun color(): Int = when (mode) {
        Mode.OFF -> Color.rgb(70, 90, 100)
        Mode.IDLE -> Color.rgb(0, 200, 255)
        Mode.LISTENING -> Color.rgb(0, 255, 230)
        Mode.THINKING -> Color.rgb(255, 176, 0)
        Mode.SPEAKING -> Color.rgb(120, 230, 255)
        Mode.ERROR -> Color.rgb(255, 60, 60)
    }

    private fun speed(): Float = when (mode) {
        Mode.OFF -> 0.1f; Mode.IDLE -> 0.5f; Mode.LISTENING -> 1.2f
        Mode.THINKING -> 3f; Mode.SPEAKING -> 1.6f; Mode.ERROR -> 0.3f
    }

    private fun withAlpha(c: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        t += 0.016f * speed()
        val target = when (mode) {
            Mode.LISTENING -> level
            Mode.SPEAKING -> 0.35f + 0.25f * sin(t * 9f).coerceAtLeast(0f)
            Mode.THINKING -> 0.25f
            else -> 0.1f + 0.05f * sin(t * 2f)
        }
        smoothLevel += (target - smoothLevel) * 0.2f

        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f * 0.9f
        val c = color()

        // Arka plan parlaması
        fill.shader = RadialGradient(cx, cy, r * (1.1f + smoothLevel * 0.3f),
            intArrayOf(withAlpha(c, 90), withAlpha(c, 25), Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.2f, fill)
        fill.shader = null

        // Dış halka: dönen parçalar
        ring(canvas, cx, cy, r, 12, t * 40f, r * 0.03f, c, 200, 18f)
        // Orta halka: ters yönde
        ring(canvas, cx, cy, r * 0.78f, 8, -t * 70f, r * 0.05f, c, 230, 30f)
        // İç ince halka
        stroke.strokeWidth = r * 0.012f
        stroke.color = withAlpha(c, 160)
        canvas.drawCircle(cx, cy, r * 0.6f, stroke)

        // Üçgen bobinler (ark reaktörü)
        stroke.strokeWidth = r * 0.06f
        for (i in 0 until 10) {
            val a = Math.toRadians((i * 36.0) + t * 15)
            val x1 = cx + cos(a).toFloat() * r * 0.42f
            val y1 = cy + sin(a).toFloat() * r * 0.42f
            val x2 = cx + cos(a).toFloat() * r * 0.55f
            val y2 = cy + sin(a).toFloat() * r * 0.55f
            stroke.color = withAlpha(c, 150 + (60 * sin(t * 4 + i)).toInt())
            canvas.drawLine(x1, y1, x2, y2, stroke)
        }

        // Çekirdek
        val core = r * (0.28f + smoothLevel * 0.12f)
        fill.shader = RadialGradient(cx, cy, core,
            intArrayOf(Color.WHITE, withAlpha(c, 255), withAlpha(c, 0)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, core, fill)
        fill.shader = null
        glow.color = withAlpha(c, 200)
        glow.strokeWidth = r * 0.04f
        canvas.drawCircle(cx, cy, r * 0.33f, glow)

        postInvalidateOnAnimation()
    }

    private fun ring(canvas: Canvas, cx: Float, cy: Float, radius: Float, segments: Int, rot: Float, width: Float, c: Int, alpha: Int, gap: Float) {
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val sweep = 360f / segments - gap
        glow.color = withAlpha(c, alpha / 2)
        glow.strokeWidth = width * 2
        stroke.strokeWidth = width
        stroke.color = withAlpha(c, alpha)
        for (i in 0 until segments) {
            val start = rot + i * 360f / segments
            canvas.drawArc(oval, start, sweep, false, glow)
            canvas.drawArc(oval, start, sweep, false, stroke)
        }
    }
}
