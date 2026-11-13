package com.jarvis.asistan.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Ortak "HUD" görünümü için küçük yardımcılar. */
object Ui {
    val CYAN = Color.rgb(0, 229, 255)
    val BG = Color.rgb(4, 10, 18)
    val PANEL = Color.rgb(10, 24, 36)
    val TEXT = Color.rgb(205, 240, 255)
    val DIM = Color.rgb(110, 150, 170)
    val GOOD = Color.rgb(80, 230, 140)
    val BAD = Color.rgb(255, 120, 90)

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun panel(ctx: Context) = GradientDrawable().apply {
        setColor(PANEL)
        cornerRadius = dp(ctx, 14).toFloat()
        setStroke(dp(ctx, 1), Color.argb(90, 0, 229, 255))
    }

    fun text(ctx: Context, s: String, size: Float = 16f, color: Int = TEXT, bold: Boolean = false) = TextView(ctx).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    fun title(ctx: Context, s: String) = text(ctx, s, 13f, CYAN, true).apply {
        letterSpacing = 0.15f
        setPadding(0, dp(ctx, 18), 0, dp(ctx, 6))
    }

    fun button(ctx: Context, s: String, onClick: () -> Unit) = Button(ctx).apply {
        text = s
        isAllCaps = false
        setTextColor(CYAN)
        textSize = 15f
        background = GradientDrawable().apply {
            setColor(Color.argb(40, 0, 229, 255))
            cornerRadius = dp(ctx, 24).toFloat()
            setStroke(dp(ctx, 1), CYAN)
        }
        setPadding(dp(ctx, 18), dp(ctx, 8), dp(ctx, 18), dp(ctx, 8))
        setOnClickListener { onClick() }
    }

    fun vertical(ctx: Context, padDp: Int = 0) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(ctx, padDp)
        setPadding(p, p, p, p)
    }

    fun horizontal(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight)
}
