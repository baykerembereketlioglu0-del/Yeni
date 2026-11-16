package app.karala

import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.karala.data.ExamResult
import app.karala.data.ScheduleEntry
import app.karala.data.StudySession
import app.karala.data.Task
import app.karala.ink.InkPage
import app.karala.ink.InkView
import app.karala.ink.PageBackground
import app.karala.ink.Stroke
import app.karala.ink.ToolType
import app.karala.overlay.CalcWidget
import app.karala.overlay.CounterWidget
import app.karala.overlay.PomodoroWidget
import app.karala.overlay.StopwatchWidget
import app.karala.ui.*
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * Ekranları JVM üzerinde çizdirip build/shots altına PNG olarak kaydeder.
 * Amaç görsel doğrulama: ekranların çökmeden, okunaklı ve taşmadan çizildiğini görmek.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { KaralaTheme(2) { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    private fun seed() = runBlocking {
        val dao = App.instance.repo.dao
        val now = System.currentTimeMillis()
        val subjects = dao.subjectsNow()
        val mat = subjects[0].id; val fiz = subjects[2].id
        dao.insert(Task(title = "TYT Deneme Sınavı", type = 1, dueAt = now + 23 * 86_400_000L, subjectId = null, remindMinutes = 1440))
        dao.insert(Task(title = "Türev test 4-5-6", type = 0, dueAt = now + 3 * 3_600_000L, subjectId = mat, remindMinutes = 60))
        dao.insert(Task(title = "Optik konu tekrarı", type = 2, dueAt = now - 86_400_000L, subjectId = fiz))
        val dow = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        dao.insert(ScheduleEntry(dayOfWeek = dow, startMinute = 540, endMinute = 580, subjectId = mat, place = "12-A"))
        dao.insert(ScheduleEntry(dayOfWeek = dow, startMinute = 590, endMinute = 630, subjectId = fiz, place = "Lab"))
        for (d in 0..5) {
            dao.insert(StudySession(subjectId = if (d % 2 == 0) mat else fiz, startAt = now - d * 86_400_000L - 3_600_000L, durationSec = (25 + d * 10) * 60))
            App.instance.repo.logQuestions(if (d % 2 == 0) mat else fiz, 30 - d * 3, 6 + d, 2)
        }
        listOf(61 to 20, 68 to 17, 66 to 14, 74 to 12, 81 to 9).forEachIndexed { i, (c, w) ->
            dao.insert(ExamResult(name = "TYT-${i + 1}", at = now - (5 - i) * 7 * 86_400_000L, correct = c, wrong = w, blank = 120 - c - w, divisor = 4))
        }
    }

    @Test fun screens() {
        seed()
        shot("01-home") { HomeScreen(rememberNavController()) }
    }
    @Test fun homeLight() {
        seed()
        compose.setContent { KaralaTheme(1) { HomeScreen(rememberNavController()) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/01-home-light.png")
    }
    @Test fun modeSheet() {
        var picked: Pair<Boolean, Boolean>? = null
        shot("15-mode-sheet") { androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow) {
            ModeSheetContent(initialStylus = true, initialDontAsk = false) { s, d -> picked = s to d } } }
        compose.onNode(androidx.compose.ui.test.hasText("El")).performClick()
        compose.onNode(androidx.compose.ui.test.hasText("Ekran kalemini başlat")).performClick()
        org.junit.Assert.assertEquals(false to false, picked)
    }
    @Test fun permissionSheet() {
        shot("16-permission-sheet") { androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow) {
            PermissionSheetContent({}, {}) } }
    }
    @Test fun plan() { seed(); shot("02-plan") { PlanScreen() } }
    @Test fun tools() { shot("03-tools") { ToolsScreen(rememberNavController()) } }
    @Test fun focus() { shot("04-focus") { FocusScreen {} } }
    @Test fun calculator() { shot("05-calc") { CalculatorScreen {} } }
    @Test fun formulas() { shot("06-formulas") { FormulasScreen {} } }
    @Test fun periodic() { shot("07-periodic") { PeriodicScreen {} } }
    @Test fun stats() { seed(); shot("08-stats") { StatsScreen {} } }
    @Test fun exams() { seed(); shot("09-exams") { ExamsScreen {} } }
    @Test fun settings() { shot("10-settings") { SettingsScreen({}, {}) } }
    @Test fun review() { shot("11-review") { ReviewScreen(rememberNavController()) } }
    @Test fun converter() { shot("12-convert") { ConverterScreen {} } }
    @Test fun notes() { shot("13-notes") { NotesScreen(rememberNavController()) } }

    /** View'ı ölçüp bitmap'e çizer ve PNG olarak kaydeder (pencereye bağlı olmayan katman görünümleri için). */
    private fun snap(v: View, name: String, w: Int = 0, h: Int = 0) {
        val ws = if (w > 0) View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY) else View.MeasureSpec.makeMeasureSpec(1233, View.MeasureSpec.AT_MOST)
        val hs = if (h > 0) View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY) else View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        v.measure(ws, hs); v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val bmp = android.graphics.Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp); c.drawColor(Color.rgb(60, 90, 120)); v.draw(c)
        java.io.File("build/shots").mkdirs()
        java.io.File("build/shots/$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun host(): androidx.activity.ComponentActivity =
        org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()

    private fun strokes(page: InkPage) {
        val wave = FloatArray(160) { i -> if (i % 2 == 0) 80f + i * 6f else 200f + 60f * Math.sin(i / 9.0).toFloat() }
        page.strokes += Stroke(ToolType.HIGHLIGHTER, 0xFFFFF176.toInt(), 46f, floatArrayOf(90f, 120f, 700f, 120f))
        page.strokes += Stroke(ToolType.PEN, 0xFF3D7BFF.toInt(), 8f, wave)
        page.strokes += Stroke(ToolType.PEN, 0xFFFF5252.toInt(), 10f, wave.copyOf().also { for (i in it.indices) if (i % 2 == 1) it[i] += 120f },
            FloatArray(80) { 0.2f + 0.8f * (it / 80f) })
        page.strokes += Stroke(ToolType.ARROW, 0xFF16181D.toInt(), 8f, floatArrayOf(150f, 650f, 450f, 480f))
        page.strokes += Stroke(ToolType.RECT, 0xFF2E9E5B.toInt(), 8f, floatArrayOf(560f, 460f, 800f, 660f))
        page.strokes += Stroke(ToolType.ELLIPSE, 0xFFFF8A3D.toInt(), 8f, floatArrayOf(860f, 440f, 1120f, 680f))
        page.strokes += Stroke(ToolType.LINE, 0xFFB388FF.toInt(), 8f, floatArrayOf(860f, 120f, 1120f, 320f))
    }

    @Test fun inkCanvas() {
        val act = host()
        val view = InkView(act, editorMode = true)
        snap(view, "20-ink", 1200, 800)
        val page = InkPage(1200, 800, background = PageBackground.GRID)
        strokes(page)
        view.setPage(page)
        snap(view, "20-ink", 1200, 800)
    }

    @Test fun overlayWidgets() {
        val act = host()
        val col = android.widget.LinearLayout(act).apply { orientation = android.widget.LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(60, 90, 120)); setPadding(30, 30, 30, 30) }
        val widgets = listOf(StopwatchWidget(act) {}, PomodoroWidget(act) {}, CalcWidget(act) {}, CounterWidget(act, App.instance.repo) {})
        widgets.forEach { w -> col.addView(w.root, android.widget.LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = 24 }) }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        snap(col, "21-widgets")
    }

    private fun wmViews(ctx: android.content.Context): List<View> =
        org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowWindowManagerImpl>(ctx.getSystemService(android.view.WindowManager::class.java)).views

    private fun clickIn(root: View, desc: String) {
        val out = ArrayList<View>(); root.findViewsWithText(out, desc as CharSequence, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        org.junit.Assert.assertTrue("button: $desc", out.isNotEmpty()); out[0].performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    private fun down(v: View, toolType: Int) {
        val t = android.os.SystemClock.uptimeMillis()
        val props = arrayOf(android.view.MotionEvent.PointerProperties().apply { id = 0; this.toolType = toolType })
        fun ev(action: Int, x: Float, y: Float) = android.view.MotionEvent.obtain(t, t, action, 1, props,
            arrayOf(android.view.MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 0.6f; size = 1f }), 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        v.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_DOWN, 100f, 100f))
        v.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_MOVE, 300f, 260f))
        v.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_UP, 300f, 260f))
    }

    /** Katman servisini gerçekten başlatır: pencereler eklenir, araç çubuğu ve paneller çizilir. */
    @Test fun overlayService() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        val Svc = app.karala.overlay.OverlayService::class.java
        App.instance.prefs.askInputMode = true; App.instance.prefs.stylusOnly = false
        java.io.File(App.instance.filesDir, "overlay_draft.json").delete()
        val controller = org.robolectric.Robolectric.buildService(Svc, android.content.Intent(App.instance, Svc).setAction(app.karala.overlay.OverlayService.ACTION_START))
        val svc = controller.create().startCommand(0, 1).get()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        org.junit.Assert.assertTrue(app.karala.overlay.OverlayService.running.value)
        val inkWindow = wmViews(svc).single { it is InkView } as InkView
        val bar = wmViews(svc).first { it is android.widget.LinearLayout }

        // Başlangıçta "El mi, Kalem mi?" penceresi en üstte olmalı.
        val chooser = wmViews(svc).last()
        org.junit.Assert.assertTrue(chooser is android.widget.FrameLayout)
        snap(chooser, "28-mode-chooser", 1233, 1500)
        clickIn(chooser, "Seçimimi hatırla")
        snap(chooser, "29-mode-chooser-remember", 1233, 1500)
        clickIn(chooser, "Kalem modu")
        org.junit.Assert.assertTrue(App.instance.prefs.stylusOnly)
        org.junit.Assert.assertFalse(App.instance.prefs.askInputMode)
        org.junit.Assert.assertFalse(wmViews(svc).contains(chooser))

        // Kalem modu: parmak çizmez, kalem ucu çizer.
        inkWindow.layout(0, 0, 1200, 800)
        down(inkWindow, android.view.MotionEvent.TOOL_TYPE_FINGER)
        org.junit.Assert.assertEquals(0, inkWindow.page.strokes.size)
        down(inkWindow, android.view.MotionEvent.TOOL_TYPE_STYLUS)
        org.junit.Assert.assertEquals(1, inkWindow.page.strokes.size)
        // Araç çubuğundan el moduna geç: artık parmak da çizer.
        snap(bar, "22-toolbar")
        clickIn(bar, "Kalem modu açık: yalnızca kalem ucu çizer")
        org.junit.Assert.assertFalse(App.instance.prefs.stylusOnly)
        down(inkWindow, android.view.MotionEvent.TOOL_TYPE_FINGER)
        org.junit.Assert.assertEquals(2, inkWindow.page.strokes.size)
        clickIn(bar, "Geri al"); clickIn(bar, "Geri al")
        org.junit.Assert.assertEquals(0, inkWindow.page.strokes.size)

        // Uygulamadan seçimle başlatma: servis sormaz, modu uygular.
        controller.withIntent(android.content.Intent(App.instance, Svc).setAction(app.karala.overlay.OverlayService.ACTION_START)
            .putExtra(app.karala.overlay.OverlayService.EXTRA_INPUT, app.karala.overlay.OverlayService.INPUT_STYLUS)).startCommand(0, 2)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        org.junit.Assert.assertTrue(App.instance.prefs.stylusOnly)
        org.junit.Assert.assertTrue(inkWindow.stylusOnly)
        clickIn(bar, "Kalem modu açık: yalnızca kalem ucu çizer")

        fun click(desc: String) = clickIn(bar, desc)
        click("Kalem"); snap(bar, "23-toolbar-pen")
        click("Silgi"); click("Silgi"); snap(bar, "24-toolbar-eraser")
        click("Tahta / zemin"); snap(bar, "25-toolbar-board")
        click("Kaydet"); snap(bar, "26-toolbar-save")
        click("Araçlar"); snap(bar, "27-toolbar-more")
        click("Dokunma modu: alttaki uygulamayı kullan")
        org.junit.Assert.assertTrue(svc.passThrough)
        val p = inkWindow.layoutParams as android.view.WindowManager.LayoutParams
        org.junit.Assert.assertTrue(p.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        click("Kalem")
        org.junit.Assert.assertFalse(svc.passThrough)
        org.junit.Assert.assertEquals(0, (inkWindow.layoutParams as android.view.WindowManager.LayoutParams).flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        // Sayfa ekleme: boş sayfadan ileri gidilmez.
        click("Sonraki sayfa"); org.junit.Assert.assertEquals(1, svc.pageCount)
        controller.destroy()
        org.junit.Assert.assertFalse(app.karala.overlay.OverlayService.running.value)
        org.junit.Assert.assertTrue(wmViews(App.instance).none { it is InkView || it === bar })
        App.instance.prefs.askInputMode = true
    }

    /** Katmandan kaydetme hattı: defter + sayfa + dosyalar oluşur, hatalı soru ertesi güne planlanır, düzenleyici açılır. */
    @Test fun savePipelineAndEditor() {
        val repo = App.instance.repo
        val page = InkPage(1200, 800); strokes(page)
        val shotBmp = android.graphics.Bitmap.createBitmap(1200, 800, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(235, 235, 225)) }
        val (noteId, mistakeId) = runBlocking {
            val mat = repo.dao.subjectsNow()[0].id
            val a = repo.saveCaptureAsNote(mat, page, shotBmp)
            val b = repo.saveCaptureAsNote(mat, page, null)          // aynı gün, aynı ders: aynı deftere eklenir
            org.junit.Assert.assertEquals(a, b)
            a to repo.saveCaptureAsMistake(mat, "Türev", 2, page, shotBmp)
        }
        runBlocking {
            val pages = repo.dao.pagesNow(noteId)
            org.junit.Assert.assertEquals(2, pages.size)
            org.junit.Assert.assertTrue(pages[0].hasBackgroundImage && !pages[1].hasBackgroundImage)
            pages.forEach { org.junit.Assert.assertTrue(repo.files.ink(it.id).exists() && repo.files.thumb(it.id).length() > 0) }
            org.junit.Assert.assertTrue(repo.files.bg(pages[0].id).exists())
            org.junit.Assert.assertEquals(7, repo.loadInk(pages[0].id).strokes.size)
            val m = repo.dao.mistakeNow(mistakeId)!!
            org.junit.Assert.assertTrue(m.dueAt > System.currentTimeMillis() + 23 * 3_600_000L)
            org.junit.Assert.assertEquals(0, repo.dao.dueMistakes(System.currentTimeMillis()).size)
            org.junit.Assert.assertEquals(1, repo.dao.dueMistakes(System.currentTimeMillis() + 2 * 86_400_000L).size)
            repo.reviewMistake(m, app.karala.study.Srs.Grade.GOOD)
            org.junit.Assert.assertEquals(1, repo.dao.mistakeNow(mistakeId)!!.reps)
            // Silme dosyaları da temizler.
            repo.deletePage(pages[1].id)
            org.junit.Assert.assertFalse(repo.files.ink(pages[1].id).exists())
        }
        compose.setContent { KaralaTheme(2) { NoteEditorScreen(noteId) {} } }
        compose.waitForIdle()
        Thread.sleep(300); org.robolectric.shadows.ShadowLooper.idleMainLooper(); compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/14-editor.png")
    }
}
