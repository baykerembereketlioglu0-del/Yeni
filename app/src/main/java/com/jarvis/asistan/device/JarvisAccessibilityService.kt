package com.jarvis.asistan.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.jarvis.asistan.service.JarvisService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Jarvis'in tabletin arayüzünü kullanabilmesini sağlar: güç menüsü, ekranı okuma,
 * dokunma, yazı yazma, kaydırma ve ekran görüntüsü alma.
 */
class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Erişilebilirlik açıldığında Jarvis'i de başlat (ör. yeniden başlatmadan sonra).
        JarvisService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    // ---------------- Yardımcılar ----------------

    private fun roots(): List<AccessibilityNodeInfo> {
        val list = ArrayList<AccessibilityNodeInfo>()
        runCatching { windows?.forEach { w -> w.root?.let { list.add(it) } } }
        if (list.isEmpty()) rootInActiveWindow?.let { list.add(it) }
        return list
    }

    private fun walk(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>, depth: Int = 0) {
        if (node == null || depth > 60) return
        out.add(node)
        for (i in 0 until node.childCount) walk(node.getChild(i), out, depth + 1)
    }

    private fun allNodes(): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        roots().forEach { walk(it, out) }
        return out
    }

    private fun label(n: AccessibilityNodeInfo): String =
        listOfNotNull(n.text?.toString(), n.contentDescription?.toString(),
            if (Build.VERSION.SDK_INT >= 26) n.hintText?.toString() else null)
            .firstOrNull { it.isNotBlank() }?.trim() ?: ""

    private fun norm(s: String) = s.lowercase(Locale("tr")).replace('ı', 'i').replace('İ', 'i').trim()

    private fun clickableParent(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = n
        var hops = 0
        while (cur != null && hops < 8) {
            if (cur.isClickable) return cur
            cur = cur.parent
            hops++
        }
        return null
    }

    /** Ekrandaki görünür yazıları döndürür (Claude'un ekranı "görmesi" için). */
    fun screenText(limit: Int = 120): String {
        val pkg = rootInActiveWindow?.packageName ?: "?"
        val lines = LinkedHashSet<String>()
        for (n in allNodes()) {
            if (!n.isVisibleToUser) continue
            val l = label(n)
            if (l.isEmpty()) continue
            val tags = buildList {
                if (n.isClickable || clickableParent(n) != null) add("tıklanabilir")
                if (n.isEditable) add("yazı alanı")
                if (n.isCheckable) add(if (n.isChecked) "açık" else "kapalı")
            }
            lines.add(if (tags.isEmpty()) l else "$l [${tags.joinToString()}]")
            if (lines.size >= limit) break
        }
        return "Ön plandaki uygulama: $pkg\n" + lines.joinToString("\n")
    }

    fun findNodes(query: String, exact: Boolean = false): List<AccessibilityNodeInfo> {
        val q = norm(query)
        val nodes = allNodes().filter { it.isVisibleToUser && label(it).isNotEmpty() }
        val exactHits = nodes.filter { norm(label(it)) == q }
        if (exact || exactHits.isNotEmpty()) return exactHits
        return nodes.filter { norm(label(it)).contains(q) }
            .sortedBy { label(it).length }
    }

    /** Metni/etiketi eşleşen öğeye dokunur. */
    suspend fun clickText(query: String, exact: Boolean = false): Boolean {
        val node = findNodes(query, exact).firstOrNull() ?: return false
        val target = clickableParent(node)
        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val r = Rect()
        node.getBoundsInScreen(r)
        return tap(r.exactCenterX(), r.exactCenterY())
    }

    suspend fun tap(x: Float, y: Float): Boolean = gesture(Path().apply { moveTo(x, y) }, 60)

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long = 400): Boolean =
        gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, ms)

    private suspend fun gesture(path: Path, ms: Long): Boolean {
        val done = CompletableDeferred<Boolean>()
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build()
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { done.complete(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { done.complete(false) }
        }, null)
        if (!ok) return false
        return withTimeoutOrNull(ms + 2000) { done.await() } ?: false
    }

    /** Ekranı belirtilen yöne kaydırır. */
    suspend fun scroll(direction: String): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        return when (direction) {
            "asagi" -> swipe(w / 2, h * 0.75f, w / 2, h * 0.25f)
            "yukari" -> swipe(w / 2, h * 0.25f, w / 2, h * 0.75f)
            "sola" -> swipe(w * 0.8f, h / 2, w * 0.2f, h / 2)
            "saga" -> swipe(w * 0.2f, h / 2, w * 0.8f, h / 2)
            else -> false
        }
    }

    /** Odaklı (veya ilk) yazı alanına metin yazar. */
    fun typeText(text: String): Boolean {
        val nodes = allNodes()
        val target = nodes.firstOrNull { it.isEditable && it.isFocused }
            ?: nodes.firstOrNull { it.isEditable && it.isVisibleToUser }
            ?: return false
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Güç menüsünü açıp "Kapat" / "Yeniden başlat" seçeneğine basar. */
    suspend fun powerMenu(restart: Boolean): String {
        if (!performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)) return "Güç menüsü açılamadı."
        delay(1300)
        val words = if (restart)
            listOf("Yeniden başlat", "Yeniden Başlat", "Restart", "Reboot", "Yeniden başlatın")
        else
            listOf("Kapat", "Gücü kapat", "Cihazı kapat", "Power off", "Kapatın", "Turn off", "Shut down")
        for (attempt in 0 until 3) {
            val node = words.firstNotNullOfOrNull { w -> findNodes(w, exact = true).firstOrNull() }
                ?: words.firstNotNullOfOrNull { w -> findNodes(w).firstOrNull() }
            if (node == null) {
                if (attempt == 0) return "Güç menüsü açıldı ama ${if (restart) "yeniden başlat" else "kapat"} düğmesi bulunamadı. Ekrandakiler:\n" + screenText(30)
                break
            }
            // Bazı arayüzler kaydırma ister ("Kapatmak için kaydırın").
            val lbl = norm(label(node))
            val r = Rect().also { node.getBoundsInScreen(it) }
            if (lbl.contains("kaydir") || lbl.contains("slide") || lbl.contains("swipe")) {
                val dm = resources.displayMetrics
                when {
                    lbl.contains("asagi") || lbl.contains("down") -> swipe(r.exactCenterX(), r.top.toFloat(), r.exactCenterX(), dm.heightPixels * 0.9f, 500)
                    lbl.contains("yukari") || lbl.contains("up") -> swipe(r.exactCenterX(), r.bottom.toFloat(), r.exactCenterX(), dm.heightPixels * 0.1f, 500)
                    else -> swipe(r.left.toFloat() + 10, r.exactCenterY(), dm.widthPixels * 0.95f, r.exactCenterY(), 500)
                }
            } else {
                val target = clickableParent(node)
                if (target == null || !target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    tap(r.exactCenterX(), r.exactCenterY())
                }
            }
            delay(1200)
            // Onay adımı varsa tekrar dene; yoksa döngü kendiliğinden biter.
        }
        return if (restart) "Yeniden başlatma komutu verildi." else "Kapatma komutu verildi."
    }

    @RequiresApi(30)
    suspend fun screenshot(): Bitmap? {
        val result = CompletableDeferred<Bitmap?>()
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                screenshot.hardwareBuffer.close()
                result.complete(soft)
            }
            override fun onFailure(errorCode: Int) { result.complete(null) }
        })
        return withTimeoutOrNull(5000) { result.await() }
    }

    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
            private set
    }
}
