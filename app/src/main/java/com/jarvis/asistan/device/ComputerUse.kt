package com.jarvis.asistan.device

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Claude'un `computer_toolset_20260801` araç setini Android dokunmatik ekranına uygular.
 * Koordinatlar, Claude'a gönderilen (küçültülmüş) ekran görüntüsünün piksel alanındadır.
 */
class ComputerUse(private val ctx: Context) {

    private val a11y get() = JarvisAccessibilityService.instance
    private var lastFull: Bitmap? = null
    private var cursorX = 0f
    private var cursorY = 0f
    private var downX: Float? = null
    private var downY: Float? = null

    /** Gerçek piksel = model pikseli / scale */
    private fun scale(): Float {
        val (w, h) = a11y?.realSize() ?: (1600 to 2560)
        return minOf(1f, 1568f / maxOf(w, h), sqrt(1_150_000f / (w.toFloat() * h)))
    }

    private fun real(v: JSONArray?, idx: Int): Float? = v?.optDouble(idx)?.takeIf { !it.isNaN() }?.toFloat()?.div(scale())

    private fun point(input: JSONObject, key: String = "coordinate"): Pair<Float, Float>? {
        val a = input.optJSONArray(key) ?: return null
        val x = real(a, 0) ?: return null
        val y = real(a, 1) ?: return null
        return x to y
    }

    private fun err(msg: String) = ToolOutput(msg, isError = true)

    private fun screenState(): String? {
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (!pm.isInteractive) return "Ekran kapalı. Ekran görevi için kullanıcının ekranı açıp kilidi açması gerekiyor."
        val km = ctx.getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) return "Ekran kilitli. Kullanıcının kilidi açması gerekiyor."
        return null
    }

    suspend fun execute(name: String, input: JSONObject): ToolOutput {
        val s = a11y ?: return err("Erişilebilirlik izni kapalı; ekran kontrol edilemiyor (Kurulum > Erişilebilirlik).")
        return when (name) {
            "screenshot" -> {
                screenState()?.let { return err(it) }
                if (Build.VERSION.SDK_INT < 30) return err("Ekran görüntüsü Android 11+ gerektirir.")
                val full = s.screenshot() ?: return err("Ekran görüntüsü alınamadı (bazı uygulamalar ekran görüntüsünü engeller).")
                lastFull = full
                val sc = scale()
                val w = (full.width * sc).toInt()
                val h = (full.height * sc).toInt()
                val small = if (sc < 1f) Bitmap.createScaledBitmap(full, w, h, true) else full
                ToolOutput("Ekran ${w}x$h", DeviceController.toJpegBase64(small, maxOf(w, h)))
            }
            "zoom" -> {
                val full = lastFull ?: return err("Önce screenshot al.")
                val r = input.optJSONArray("region") ?: return err("region gerekli.")
                val sc = scale()
                val x0 = (r.optDouble(0) / sc).toInt().coerceIn(0, full.width - 1)
                val y0 = (r.optDouble(1) / sc).toInt().coerceIn(0, full.height - 1)
                val x1 = (r.optDouble(2) / sc).toInt().coerceIn(x0 + 1, full.width)
                val y1 = (r.optDouble(3) / sc).toInt().coerceIn(y0 + 1, full.height)
                val crop = Bitmap.createBitmap(full, x0, y0, x1 - x0, y1 - y0)
                ToolOutput("Yakınlaştırılmış bölge", DeviceController.toJpegBase64(crop, 1280))
            }
            "left_click", "middle_click" -> {
                val (x, y) = point(input) ?: (cursorX to cursorY)
                cursorX = x; cursorY = y
                if (s.tap(x, y)) ToolOutput("OK") else err("Dokunulamadı.")
            }
            "double_click" -> {
                val (x, y) = point(input) ?: (cursorX to cursorY)
                cursorX = x; cursorY = y
                s.tap(x, y); delay(90); s.tap(x, y)
                ToolOutput("OK")
            }
            "triple_click" -> {
                val (x, y) = point(input) ?: (cursorX to cursorY)
                cursorX = x; cursorY = y
                repeat(3) { s.tap(x, y); delay(80) }
                ToolOutput("OK")
            }
            "right_click" -> {
                val (x, y) = point(input) ?: (cursorX to cursorY)
                cursorX = x; cursorY = y
                if (s.longPress(x, y)) ToolOutput("OK (uzun basıldı)") else err("Uzun basılamadı.")
            }
            "left_click_drag" -> {
                val (x0, y0) = point(input, "start_coordinate") ?: (cursorX to cursorY)
                val (x1, y1) = point(input) ?: return err("coordinate gerekli.")
                cursorX = x1; cursorY = y1
                if (s.swipe(x0, y0, x1, y1, 600)) ToolOutput("OK") else err("Sürüklenemedi.")
            }
            "mouse_move" -> {
                val (x, y) = point(input) ?: return err("coordinate gerekli.")
                cursorX = x; cursorY = y
                ToolOutput("OK")
            }
            "left_mouse_down" -> { downX = cursorX; downY = cursorY; ToolOutput("OK") }
            "left_mouse_up" -> {
                val dx = downX ?: cursorX
                val dy = downY ?: cursorY
                downX = null; downY = null
                val moved = kotlin.math.abs(dx - cursorX) + kotlin.math.abs(dy - cursorY) > 10
                val ok = if (moved) s.swipe(dx, dy, cursorX, cursorY, 600) else s.tap(cursorX, cursorY)
                if (ok) ToolOutput("OK") else err("Hareket başarısız.")
            }
            "cursor_position" -> {
                val sc = scale()
                ToolOutput("X=${(cursorX * sc).toInt()}, Y=${(cursorY * sc).toInt()}")
            }
            "scroll" -> {
                val (w, h) = s.realSize()
                val (x, y) = point(input) ?: (w / 2f to h / 2f)
                val amount = input.optInt("scroll_amount", 3).coerceIn(1, 15)
                val dist = (if (input.optString("scroll_direction") in listOf("left", "right")) w else h) * 0.08f * amount
                val ok = when (input.optString("scroll_direction")) {
                    "down" -> s.swipe(x, y, x, (y - dist).coerceAtLeast(5f), 450)
                    "up" -> s.swipe(x, y, x, (y + dist).coerceAtMost(h - 5f), 450)
                    "left" -> s.swipe(x, y, (x + dist).coerceAtMost(w - 5f), y, 450)
                    "right" -> s.swipe(x, y, (x - dist).coerceAtLeast(5f), y, 450)
                    else -> return err("scroll_direction up/down/left/right olmalı.")
                }
                delay(350)
                if (ok) ToolOutput("OK") else err("Kaydırılamadı.")
            }
            "type" -> {
                val text = input.optString("text")
                val endsWithEnter = text.endsWith("\n")
                val ok = s.typeAppend(text.trimEnd('\n'))
                if (endsWithEnter) s.imeEnter()
                if (ok) ToolOutput("OK") else err("Odaklanmış bir yazı alanı yok; önce yazı alanına dokun.")
            }
            "key" -> {
                val repeat = input.optInt("repeat", 1).coerceIn(1, 100)
                var res = ToolOutput("OK")
                repeat(repeat) { res = key(s, input.optString("text")) }
                res
            }
            "wait" -> {
                delay((input.optDouble("duration", 1.0).coerceIn(0.0, 30.0) * 1000).toLong())
                ToolOutput("OK")
            }
            "hold_key" -> err("Dokunmatik ekranda tuş basılı tutma desteklenmiyor.")
            else -> err("Desteklenmeyen eylem: $name")
        }
    }

    private suspend fun key(s: JarvisAccessibilityService, combo: String): ToolOutput {
        val k = combo.lowercase().trim()
        val ok = when (k) {
            "return", "enter", "kp_enter" -> s.imeEnter()
            "backspace" -> s.backspace(1)
            "ctrl+a" -> s.selectAll()
            "delete", "ctrl+a+backspace" -> s.clearInput()
            "escape", "esc", "back", "alt+left", "browser_back" -> s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "home", "super", "meta" -> s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "alt+tab", "recents", "app_switch" -> s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "page_down", "pagedown" -> { val (w, h) = s.realSize(); s.swipe(w / 2f, h * 0.8f, w / 2f, h * 0.2f) }
            "page_up", "pageup" -> { val (w, h) = s.realSize(); s.swipe(w / 2f, h * 0.2f, w / 2f, h * 0.8f) }
            "space" -> s.typeAppend(" ")
            "tab" -> return err("Tab desteklenmiyor; bir sonraki alana dokun.")
            else -> return err("Bu tuş Android'de desteklenmiyor: $combo. Kullanılabilenler: Return, BackSpace, ctrl+a, Delete, Back/Escape, Home, alt+Tab, Page_Down, Page_Up, space.")
        }
        delay(150)
        return if (ok) ToolOutput("OK") else err("Tuş uygulanamadı: $combo")
    }
}
