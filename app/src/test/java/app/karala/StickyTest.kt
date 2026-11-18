package app.karala

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.karala.ink.InkView
import app.karala.ink.ToolType
import app.karala.overlay.StickyService
import app.karala.overlay.StickyStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.io.File
import java.time.Duration
import kotlinx.coroutines.flow.first

/** Ekran notları: kalem yaklaşınca açılma, yazma/silme, küçültme, kaldırma ve kalıcılık. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-xhdpi")
class StickyTest {
    private val app get() = App.instance
    private lateinit var controller: ServiceController<StickyService>

    @Before fun setUp() {
        ShadowSettings.setCanDrawOverlays(true)
        File(app.filesDir, "sticky_notes.json").delete()
        app.prefs.stylusOnly = false
    }

    @After fun tearDown() { runCatching { controller.destroy() } }

    private fun views(): List<View> = Shadow.extract<ShadowWindowManagerImpl>(app.getSystemService(WindowManager::class.java)).views
    private fun idle() = ShadowLooper.idleMainLooper()

    private fun start(action: String): StickyService {
        controller = Robolectric.buildService(StickyService::class.java, Intent(app, StickyService::class.java).setAction(action))
        return controller.create().startCommand(0, 1).get().also { idle() }
    }

    private fun command(action: String) {
        controller.withIntent(Intent(app, StickyService::class.java).setAction(action)).startCommand(0, 2); idle()
    }

    private fun noteRoots() = views().filter { it.findInk() != null }
    private fun View.findInk(): InkView? = when (this) {
        is InkView -> this
        is android.view.ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findInk() }
        else -> null
    }

    private fun layout(v: View) {
        v.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private fun find(root: View, desc: String): View {
        val out = ArrayList<View>(); root.findViewsWithText(out, desc, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        assertTrue("bulunamadı: $desc", out.isNotEmpty()); return out[0]
    }
    private fun visible(root: View, desc: String): Boolean {
        val out = ArrayList<View>(); root.findViewsWithText(out, desc, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        return out.isNotEmpty() && out[0].isShown
    }
    private fun click(root: View, desc: String) { find(root, desc).performClick(); idle() }

    private fun props(tool: Int) = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = tool })
    private fun coords(x: Float, y: Float) = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 0.6f; size = 1f })

    private fun hover(root: View, action: Int, x: Float = 100f, y: Float = 150f) {
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, action, 1, props(MotionEvent.TOOL_TYPE_STYLUS), coords(x, y), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        root.dispatchGenericMotionEvent(e); idle()
    }

    /** Notun yazı alanında (başlığın altında) bir çizgi çizer. */
    private fun stroke(root: View, tool: Int, y: Float = 160f) {
        val t = SystemClock.uptimeMillis()
        fun ev(a: Int, x: Float) = MotionEvent.obtain(t, t, a, 1, props(tool), coords(x, y), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, 60f))
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, 200f))
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, 340f))
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, 340f))
        idle()
    }

    private fun snap(v: View, name: String) {
        layout(v)
        val bmp = Bitmap.createBitmap(v.width + 60, v.height + 60, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.rgb(32, 38, 52)); c.translate(30f, 30f); v.draw(c)
        File("build/shots").mkdirs()
        File("build/shots/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun penHoverActivatesWriteEraseAndPersist() {
        val svc = start(StickyService.ACTION_ADD)
        assertTrue(StickyService.running.value)
        assertEquals(1, noteRoots().size)
        val root = noteRoots()[0]; layout(root)
        val ink = root.findInk()!!

        // Yeni not açık (etkin) gelir; birkaç saniye sonra sakinleşir.
        assertTrue(visible(root, "Not silgisi"))
        ShadowLooper.idleMainLooper(7, java.util.concurrent.TimeUnit.SECONDS)
        assertFalse("sakin hâlde araçlar gizli", visible(root, "Not silgisi"))
        snap(root, "40-note-idle-empty")

        // Kalem havada notun üstüne geldi: kendiliğinden yazma hâli, kalem seçili.
        hover(root, MotionEvent.ACTION_HOVER_ENTER)
        assertTrue(visible(root, "Not silgisi"))
        assertEquals(ToolType.PEN, ink.tool)
        assertTrue(svc.stylusSeen)

        // Yaz.
        hover(root, MotionEvent.ACTION_HOVER_EXIT)
        stroke(root, MotionEvent.TOOL_TYPE_STYLUS, 160f)
        stroke(root, MotionEvent.TOOL_TYPE_STYLUS, 230f)
        assertEquals(2, ink.page.strokes.size)
        snap(root, "41-note-active")

        // Sil (parça silgi): üstteki çizgiyi tamamen sil.
        click(root, "Not silgisi")
        assertEquals(ToolType.ERASER, ink.tool)
        for (x in listOf(60f, 100f, 140f, 180f, 220f, 260f, 300f, 340f)) {
            val t = SystemClock.uptimeMillis()
            fun ev(a: Int) = MotionEvent.obtain(t, t, a, 1, props(MotionEvent.TOOL_TYPE_STYLUS), coords(x, 160f), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            root.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN)); root.dispatchTouchEvent(ev(MotionEvent.ACTION_UP))
        }
        idle()
        assertEquals("yalnızca alttaki çizgi kaldı", 1, ink.page.strokes.size)
        click(root, "Notta geri al")
        assertTrue(ink.page.strokes.size > 1)
        click(root, "Not kalemi")
        assertEquals(ToolType.PEN, ink.tool)

        // Kalem uzaklaştı: kısa süre sonra sakin hâl; yazı yerinde.
        hover(root, MotionEvent.ACTION_HOVER_ENTER); hover(root, MotionEvent.ACTION_HOVER_EXIT)
        ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS)
        assertFalse(visible(root, "Not silgisi"))
        snap(root, "42-note-idle")

        // Servis kapanıp açılsa da not ve yazısı geri gelir ("kaldırana kadar kalır").
        val strokes = ink.page.strokes.size
        controller.destroy(); idle()
        assertFalse(StickyService.running.value)
        assertTrue(noteRoots().isEmpty())
        assertTrue(StickyStore.hasNotes(app))
        start(StickyService.ACTION_SHOW)
        assertEquals(1, noteRoots().size)
        assertEquals(strokes, noteRoots()[0].findInk()!!.page.strokes.size)
    }

    @Test fun fingerWritesUntilPenSeen_thenPenModeRejectsPalm() {
        app.prefs.stylusOnly = true
        start(StickyService.ACTION_ADD)
        val root = noteRoots()[0]; layout(root)
        val ink = root.findInk()!!
        // Bu oturumda kalem algılanmadı: kalem modunda bile parmak yazar (kalemsiz cihazda kilitlenmesin).
        stroke(root, MotionEvent.TOOL_TYPE_FINGER)
        assertEquals(1, ink.page.strokes.size)
        // Kalem algılandıktan sonra avuç içi/parmak yok sayılır, kalem yazar.
        hover(root, MotionEvent.ACTION_HOVER_ENTER); hover(root, MotionEvent.ACTION_HOVER_EXIT)
        stroke(root, MotionEvent.TOOL_TYPE_FINGER, 230f)
        assertEquals(1, ink.page.strokes.size)
        stroke(root, MotionEvent.TOOL_TYPE_STYLUS, 230f)
        assertEquals(2, ink.page.strokes.size)
        app.prefs.stylusOnly = false
    }

    @Test fun collapseColorsRemoveAndArchive() {
        val svc = start(StickyService.ACTION_ADD)
        command(StickyService.ACTION_ADD)
        assertEquals(2, noteRoots().size)
        assertEquals(2, StickyService.count.value)
        val (a, b) = noteRoots()
        layout(a); layout(b)

        // Kâğıt rengi değişir.
        click(b, "Kâğıt rengini değiştir")
        // Küçült → rozet; rozete dokununca geri açılır.
        click(a, "Notu küçült")
        assertTrue(visible(a, "Notu aç")); assertFalse(visible(a, "Notu küçült"))
        snap(a, "43-note-chip")
        click(a, "Notu aç")
        assertTrue(visible(a, "Notu küçült"))

        // Kaldır: önce onay sorulur; "Vazgeç" not'u bırakır.
        click(a, "Notu kaldır")
        snap(a, "44-note-confirm")
        val vazgec = ArrayList<View>(); a.findViewsWithText(vazgec, "Vazgeç", View.FIND_VIEWS_WITH_TEXT)
        vazgec[0].performClick(); idle()
        assertEquals(2, noteRoots().size)

        // Yaz, sonra "Deftere kaydet ve kaldır": not ekrandan kalkar, deftere sayfa olarak girer.
        stroke(b, MotionEvent.TOOL_TYPE_STYLUS)
        click(b, "Notu kaldır")
        val save = ArrayList<View>(); b.findViewsWithText(save, "Deftere kaydet ve kaldır", View.FIND_VIEWS_WITH_TEXT)
        save[0].performClick(); idle()
        assertEquals(1, noteRoots().size)
        assertEquals(1, StickyService.count.value)
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.delay(300)
            val notes = app.repo.dao.notes().first()
            assertTrue("deftere kaydedildi", notes.any { it.pageCount >= 1 })
        }

        // Son notu kaldır: servis kendini kapatır, kayıt boşalır.
        click(a, "Notu kaldır")
        val remove = ArrayList<View>(); a.findViewsWithText(remove, "Kaldır", View.FIND_VIEWS_WITH_TEXT)
        remove.first { (it as android.widget.TextView).text == "Kaldır" }.performClick(); idle()
        assertTrue(noteRoots().isEmpty())
        assertFalse(StickyStore.hasNotes(app))
        assertTrue(org.robolectric.Shadows.shadowOf(svc).isStoppedBySelf)
    }

    private fun drag(root: View, tool: Int, fromX: Float, fromY: Float, dx: Float, dy: Float) {
        val t = SystemClock.uptimeMillis()
        fun ev(a: Int, x: Float, y: Float) = MotionEvent.obtain(t, t, a, 1, props(tool), coords(x, y), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, fromX, fromY))
        for (i in 1..5) root.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, fromX + dx * i / 5, fromY + dy * i / 5))
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, fromX + dx, fromY + dy))
        idle()
    }

    /** Kalem yaklaşınca not etkinleşir; etkinken de (kalemle) tutamaçtan ve başlığın boş yerinden taşınabilmeli. */
    @Test fun dragWhileActiveWithPen() {
        start(StickyService.ACTION_ADD)
        val root = noteRoots()[0]; layout(root)
        val params = root.layoutParams as WindowManager.LayoutParams
        hover(root, MotionEvent.ACTION_HOVER_ENTER, 30f, 30f)
        assertTrue(visible(root, "Not silgisi"))

        // Tutamaçtan sürükle.
        val gripView = find(root, "Notu taşı")
        val x0 = params.x; val y0 = params.y
        drag(root, MotionEvent.TOOL_TYPE_STYLUS, gripView.width / 2f, gripView.height / 2f, -120f, 90f)
        assertEquals(x0 - 120, params.x); assertEquals(y0 + 90, params.y)

        // Bir aracın (silgi) üstünden başlayan sürükleme de notu taşır ve silgiyi seçmez.
        val er = find(root, "Not silgisi")
        val loc = IntArray(2); er.getLocationInWindow(loc)
        val x1 = params.x; val y1 = params.y
        drag(root, MotionEvent.TOOL_TYPE_STYLUS, loc[0] + er.width / 2f, loc[1] + er.height / 2f, 80f, 60f)
        assertEquals(x1 + 80, params.x); assertEquals(y1 + 60, params.y)
        assertEquals(ToolType.PEN, root.findInk()!!.tool)
        // Silgiye dokunup bırakmak ise silgiyi seçer.
        val t = SystemClock.uptimeMillis()
        fun ev(a: Int) = MotionEvent.obtain(t, t, a, 1, props(MotionEvent.TOOL_TYPE_STYLUS), coords(loc[0] + er.width / 2f, loc[1] + er.height / 2f), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN)); root.dispatchTouchEvent(ev(MotionEvent.ACTION_UP)); idle()
        assertEquals(ToolType.ERASER, root.findInk()!!.tool)
        snap(root, "45-note-grip")

        // Konum kaydedilir: servis yeniden açılınca not aynı yerde.
        controller.destroy(); idle()
        start(StickyService.ACTION_SHOW)
        val again = noteRoots()[0].layoutParams as WindowManager.LayoutParams
        assertEquals(params.x, again.x); assertEquals(params.y, again.y)
    }

    @Test fun hideAndShowFromNotification() {
        start(StickyService.ACTION_ADD)
        val root = noteRoots()[0]
        command(StickyService.ACTION_TOGGLE)
        assertEquals(View.GONE, root.visibility)
        command(StickyService.ACTION_TOGGLE)
        assertEquals(View.VISIBLE, root.visibility)
    }
}
