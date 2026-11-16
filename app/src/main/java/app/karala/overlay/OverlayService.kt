package app.karala.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.karala.App
import app.karala.MainActivity
import app.karala.R
import app.karala.data.Prefs
import app.karala.data.Repo
import app.karala.data.Subject
import app.karala.ink.InkJson
import app.karala.ink.InkPage
import app.karala.ink.InkView
import app.karala.ink.PageBackground
import app.karala.ink.ToolType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ekran kalemi: diğer uygulamaların üzerinde çizim katmanı ve yüzen araç çubuğu gösteren ön plan servisi.
 *
 * Pencereler:
 *  - çizim katmanı (tam ekran [InkView])
 *  - araç çubuğu / baloncuk ([Toolbar])
 *  - isteğe bağlı küçük araçlar ([OverlayWidget])
 *
 * "Dokunma modu"nda çizim katmanı FLAG_NOT_TOUCHABLE alır; çizimler görünür kalır ama dokunuşlar alttaki
 * uygulamaya geçer. Android 12+ güvenlik kuralı gereği bu modda katmanın opaklığı sistemin izin verdiği
 * üst sınıra (genelde %80) indirilir, aksi hâlde sistem dokunuşları engeller.
 */
class OverlayService : Service() {
    lateinit var prefs: Prefs; private set
    private lateinit var repo: Repo
    private lateinit var wm: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    internal lateinit var ink: InkView; private set
    private lateinit var inkParams: WindowManager.LayoutParams
    private lateinit var toolbar: Toolbar
    private val widgets = HashMap<Int, OverlayWidget>()
    private lateinit var grabber: ScreenGrabber

    private var pages: MutableList<InkPage> = mutableListOf(InkPage())
    var pageIndex = 0; private set
    val pageCount get() = pages.size
    var tool: ToolType = ToolType.PEN; private set
    var passThrough = false; private set
    var inkHidden = false; private set
    private var allHidden = false
    internal var subjects: List<Subject> = emptyList(); private set

    private var pendingSaveAsMistake: Boolean? = null
    private var attached = false
    private val draftFile by lazy { File(filesDir, "overlay_draft.json") }
    private val saveDraft = Runnable { writeDraft() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = App.instance.prefs; repo = App.instance.repo
        wm = getSystemService(WindowManager::class.java)
        grabber = ScreenGrabber(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (!Settings.canDrawOverlays(this)) {
            // Ön plan sözünü yerine getirip hemen çık.
            foreground(false); stopSelf(); return START_NOT_STICKY
        }
        when (action) {
            ACTION_PROJECTION -> {
                foreground(true)
                ensureAttached()
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
                val code = intent.getIntExtra(EXTRA_CODE, 0)
                val ok = data != null && grabber.start(code, data) { foreground(false) }
                if (!ok) { foreground(false); toast("Ekran görüntüsü izni alınamadı; yalnızca çizim kaydedilecek") }
                continuePendingSave()
            }
            ACTION_PROJECTION_DENIED -> {
                foreground(grabber.ready); ensureAttached()
                toast("Ekran görüntüsü izni verilmedi; yalnızca çizim kaydedilecek")
                continuePendingSave()
            }
            ACTION_TOGGLE -> { foreground(grabber.ready); ensureAttached(); setAllHidden(!allHidden) }
            ACTION_STOP -> { foreground(grabber.ready); shutdown() }
            else -> {
                foreground(grabber.ready)
                // Uygulama içinden başlatıldıysa seçim zaten yapılmıştır; kutucuk/kısayoldan geldiyse burada sorulur.
                val input = intent?.getStringExtra(EXTRA_INPUT)
                if (input != null) prefs.stylusOnly = input == INPUT_STYLUS
                val fresh = ensureAttached()
                if (allHidden) setAllHidden(false)
                if (input != null && attached) { applyTool(); toolbar.refresh() }
                else if (fresh && prefs.askInputMode) showModeChooser()
            }
        }
        return START_NOT_STICKY
    }

    // ---- Ön plan bildirimi ----

    private fun foreground(withProjection: Boolean) {
        val n = buildNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                if (withProjection) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                startForeground(NOTIF_ID, n, type)
            } else if (Build.VERSION.SDK_INT >= 29 && withProjection) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else if (Build.VERSION.SDK_INT >= 29) {
                // 34 öncesinde "özel kullanım" türü yok; türsüz başlat.
                startForeground(NOTIF_ID, n, 0)
            } else startForeground(NOTIF_ID, n)
        }.onFailure { runCatching { startForeground(NOTIF_ID, n) } }
    }

    private fun servicePi(action: String, code: Int) = PendingIntent.getService(this, code,
        Intent(this, OverlayService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, App.CH_OVERLAY)
            .setSmallIcon(R.drawable.ic_stat_pen)
            .setContentTitle("Ekran kalemi açık")
            .setContentText(if (allHidden) "Gizli · göstermek için dokun" else "Her uygulamanın üzerine yazabilirsin")
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(servicePi(ACTION_TOGGLE, 1))
            .addAction(0, if (allHidden) "Göster" else "Gizle", servicePi(ACTION_TOGGLE, 1))
            .addAction(0, "Kapat", servicePi(ACTION_STOP, 2))
            .build()

    // ---- Pencereler ----

    /** @return pencereler bu çağrıda eklendiyse true */
    private fun ensureAttached(): Boolean {
        if (attached) return false
        loadDraft()
        tool = prefs.lastTool.takeIf { it != ToolType.ERASER } ?: ToolType.PEN

        ink = InkView(this, editorMode = false)
        ink.onChanged = { handler.removeCallbacks(saveDraft); handler.postDelayed(saveDraft, 800) }
        ink.onHistoryChanged = { toolbar.refresh() }
        inkParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, inkFlags(false), android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (Build.VERSION.SDK_INT >= 30) { fitInsetsTypes = 0 }
        }
        toolbar = Toolbar(this)
        val (sw, _) = WindowDragger.screenSize(wm)
        toolbar.params.x = if (prefs.bubbleX >= 0) prefs.bubbleX else (sw * 0.06f).toInt()
        toolbar.params.y = if (prefs.bubbleY >= 0) prefs.bubbleY else Ui.dp(this, 48)

        try {
            wm.addView(ink, inkParams)
            wm.addView(toolbar.root, toolbar.params)
        } catch (e: Exception) {
            toast("Ekran kalemi açılamadı: ${e.message}")
            stopSelf(); return false
        }
        attached = true
        ink.setPage(pages[pageIndex])
        applyTool(); applyBoardOpacity()
        toolbar.refresh()
        ink.onRejectedTouch = { hintStylusMode() }
        running.value = true
        refreshSubjects {}
        return true
    }

    // ---- Çizim girişi: el / kalem ----

    private var chooser: ModeChooser? = null
    private var lastHintAt = 0L
    private var hintCount = 0

    internal fun showModeChooser() {
        if (!attached || chooser?.shown == true) return
        toolbar.closePanel()
        chooser = ModeChooser(this, prefs.stylusOnly,
            onChosen = { stylus, dontAsk -> if (dontAsk) prefs.askInputMode = false; setInputMode(stylus, announce = true) },
            onDismissed = {}).also { it.show() }
    }

    internal fun setInputMode(stylus: Boolean, announce: Boolean) {
        prefs.stylusOnly = stylus
        hintCount = 0; rejectedTouches = 0
        dismissBanner()
        applyTool(); toolbar.refresh()
        if (announce) toast(if (stylus) "Kalem modu: yalnızca kalem ucu çizer" else "El modu: parmağınla çizebilirsin")
    }

    /**
     * Kalem modunda parmak dokunuşu yok sayıldı.
     * Bu oturumda hiç kalem ucu görülmediyse (kalemsiz cihaz, ya da parmak gibi algılanan pasif kalem)
     * kullanıcı hiçbir şey çizemez; ikinci denemede "El moduna geç" düğmeli bir şerit gösterilir.
     * Kalem görüldüyse bunlar avuç içi dokunuşlarıdır: yalnızca seyrek bir hatırlatma yeter.
     */
    private fun hintStylusMode() {
        rejectedTouches++
        if (!ink.stylusEverSeen) { if (rejectedTouches >= 2) showSwitchBanner(); return }
        dismissBanner()
        val now = SystemClock.uptimeMillis()
        if (hintCount >= 3 || now - lastHintAt < 6000) return
        lastHintAt = now; hintCount++
        toast("Kalem modundasın: parmak çizmez. Değiştirmek için araç çubuğundaki kalem düğmesine bas.")
    }

    private var rejectedTouches = 0
    private var banner: View? = null
    private val bannerTimeout = Runnable { dismissBanner() }

    private fun showSwitchBanner() {
        if (banner != null) return
        val row = Ui.row(this).apply {
            background = Ui.bg(Ui.SURFACE, Ui.dp(this@OverlayService, 22).toFloat(), Ui.ACCENT, Ui.dp(this@OverlayService, 2))
            val p = Ui.dp(this@OverlayService, 14); setPadding(p, Ui.dp(this@OverlayService, 10), Ui.dp(this@OverlayService, 10), Ui.dp(this@OverlayService, 10))
        }
        row.addView(Ui.label(this, "Kalem algılanmadı", 14f, Ui.TEXT).apply { maxLines = 1 },
            android.widget.LinearLayout.LayoutParams(-2, -2).apply { marginEnd = Ui.dp(this@OverlayService, 12) })
        val go = Ui.pill(this, "El moduna geç") { setInputMode(false, announce = true) }
        Ui.setPillActive(go, true)
        go.contentDescription = "El moduna geç"; go.maxLines = 1
        row.addView(go)
        val params = Ui.overlayParams().apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = Ui.dp(this@OverlayService, 72)
        }
        runCatching { wm.addView(row, params); banner = row }
        handler.removeCallbacks(bannerTimeout); handler.postDelayed(bannerTimeout, 12_000)
    }

    private fun dismissBanner() {
        handler.removeCallbacks(bannerTimeout)
        banner?.let { runCatching { wm.removeView(it) } }
        banner = null
    }

    private fun inkFlags(pass: Boolean): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (pass) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return f
    }

    private fun updateInkWindow() {
        if (!attached) return
        val pass = passThrough || toolbar.collapsed
        inkParams.flags = inkFlags(pass)
        inkParams.alpha = if (pass && Build.VERSION.SDK_INT >= 31)
            getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch else 1f
        ink.visibility = if (inkHidden || allHidden) View.GONE else View.VISIBLE
        runCatching { wm.updateViewLayout(ink, inkParams) }
    }

    // ---- Araç çubuğundan gelen eylemler ----

    internal fun selectTool(t: ToolType) {
        tool = t
        if (t.isShape) prefs.lastShape = t
        prefs.lastTool = t
        applyTool(); toolbar.refresh()
    }

    /** Ayarlardaki renk/kalınlığı çizim yüzeyine uygular. */
    internal fun applyTool() {
        val d = resources.displayMetrics.density
        ink.tool = tool
        ink.color = prefs.colorFor(tool)
        ink.strokeWidth = prefs.widthFor(tool) * d
        ink.eraserRadius = prefs.eraserSize * d / 2f
        ink.eraserMode = prefs.eraserMode
        ink.stylusOnly = prefs.stylusOnly
        ink.shapeAssist = prefs.shapeAssist
    }

    internal fun applyBoardOpacity() { ink.boardAlpha = prefs.boardOpacity * 255 / 100 }

    /** Tahta zeminini değiştirir. Zemin açıkken alttaki ekran görünmez; bunu kullanıcıya açıkça söyler. */
    internal fun setBoard(bg: PageBackground) {
        val was = ink.page.background
        ink.setPageBackground(bg)
        toolbar.refresh()
        if (bg != PageBackground.NONE && was == PageBackground.NONE)
            toast("Tahta açık: alttaki ekran gizlendi. Ekrana yazmak için Tahta → Şeffaf")
        else if (bg == PageBackground.NONE && was != PageBackground.NONE) toast("Tahta kapandı: doğrudan ekranın üzerine yazıyorsun")
    }

    internal fun setPassThrough(p: Boolean) {
        passThrough = p
        if (p) toolbar.closePanel()
        updateInkWindow(); toolbar.refresh()
    }

    internal fun setCollapsed(c: Boolean) {
        toolbar.setCollapsed(c)
        updateInkWindow(); toolbar.refresh()
        toolbar.root.post { toolbar.clamp() }
    }

    internal fun setInkHidden(h: Boolean) { inkHidden = h; updateInkWindow(); toolbar.refresh() }

    private fun setAllHidden(h: Boolean) {
        allHidden = h
        toolbar.root.visibility = if (h) View.GONE else View.VISIBLE
        widgets.values.forEach { it.setVisible(!h) }
        if (h) { chooser?.dismiss(); dismissBanner() }
        updateInkWindow()
        foreground(grabber.ready)
    }

    internal fun savePosition() { prefs.bubbleX = toolbar.params.x; prefs.bubbleY = toolbar.params.y }

    internal fun goToPage(index: Int) {
        if (index < 0) return
        if (index >= pages.size) {
            // Son sayfadayken ileri: boş değilse yeni sayfa aç.
            if (pages.last().isEmpty) { toast("Bu sayfa zaten boş"); return }
            pages.add(InkPage(background = pages.last().background))
        }
        pageIndex = index.coerceIn(0, pages.size - 1)
        ink.setPage(pages[pageIndex])
        toolbar.refresh(); scheduleDraft()
    }

    internal fun deletePage() {
        if (pages.size <= 1) { ink.clearAll(); return }
        pages.removeAt(pageIndex)
        pageIndex = pageIndex.coerceAtMost(pages.size - 1)
        ink.setPage(pages[pageIndex])
        toolbar.refresh(); scheduleDraft()
        toast("Sayfa silindi")
    }

    internal fun refreshSubjects(then: () -> Unit) {
        scope.launch { subjects = repo.dao.subjectsNow(); then() }
    }

    internal fun openApp() {
        setCollapsed(true)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    internal fun toggleWidget(kind: Int) {
        val existing = widgets[kind]
        if (existing != null && existing.shown) { existing.dismiss(); return }
        val w = existing ?: when (kind) {
            W_STOPWATCH -> StopwatchWidget(this) {}
            W_POMODORO -> PomodoroWidget(this) {}
            W_CALC -> CalcWidget(this) {}
            else -> CounterWidget(this, repo) {}
        }.also { widgets[kind] = it }
        val (sw, sh) = WindowDragger.screenSize(wm)
        w.show((sw * (0.55f + 0.05f * kind)).toInt().coerceAtMost(sw - Ui.dp(this, 260)).coerceAtLeast(0),
            (sh * 0.25f).toInt() + Ui.dp(this, 30) * kind)
        w.root.post { w.clamp() }
        toolbar.closePanel()
    }

    // ---- Kaydetme ----

    internal fun save(asMistake: Boolean) {
        if (prefs.captureWithScreenshot && !grabber.ready) {
            pendingSaveAsMistake = asMistake
            toolbar.closePanel()
            startActivity(Intent(this, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            return
        }
        doSave(asMistake)
    }

    private fun continuePendingSave() {
        val m = pendingSaveAsMistake ?: return
        pendingSaveAsMistake = null
        // İzin penceresi kapanıp alttaki uygulama yeniden çizilsin.
        handler.postDelayed({ if (attached) doSave(m) }, 450)
    }

    private fun doSave(asMistake: Boolean) {
        toolbar.closePanel()
        val page = ink.page.copy()
        val subjectId = prefs.lastSubjectId.takeIf { id -> subjects.any { it.id == id } }
        val wantShot = prefs.captureWithScreenshot && grabber.ready
        if (!wantShot) {
            if (page.isEmpty) { toast("Kaydedilecek çizim yok"); return }
            persist(asMistake, subjectId, page, null); return
        }
        // Katmanı gizle, temiz ekranın karesini bekle, sonra geri göster.
        val hiddenAt = SystemClock.uptimeMillis()
        setCaptureHidden(true)
        val poll = object : Runnable {
            override fun run() {
                val waited = SystemClock.uptimeMillis() - hiddenAt
                val fresh = grabber.lastFrameAt >= hiddenAt + 120
                if (!fresh && waited < 700) { handler.postDelayed(this, 40); return }
                val shot = if (waited >= 120) grabber.grab() else null
                if (shot == null && waited < 700) { handler.postDelayed(this, 40); return }
                setCaptureHidden(false)
                val cropped = shot?.let { cropToInk(it) }
                if (cropped == null && page.isEmpty) { toast("Ekran görüntüsü alınamadı"); return }
                persist(asMistake, subjectId, page, cropped)
            }
        }
        handler.postDelayed(poll, 120)
    }

    private fun setCaptureHidden(hidden: Boolean) {
        val v = if (hidden) View.INVISIBLE else View.VISIBLE
        toolbar.root.visibility = v
        widgets.values.forEach { it.setVisible(!hidden) }
        ink.alpha = if (hidden) 0f else 1f
    }

    /** Ekran görüntüsünü çizim katmanının kapladığı alana kırpar; böylece mürekkep koordinatlarıyla birebir örtüşür. */
    private fun cropToInk(shot: Bitmap): Bitmap {
        val loc = IntArray(2); ink.getLocationOnScreen(loc)
        val x = loc[0].coerceIn(0, shot.width - 1); val y = loc[1].coerceIn(0, shot.height - 1)
        val w = ink.width.coerceAtMost(shot.width - x); val h = ink.height.coerceAtMost(shot.height - y)
        if (w <= 0 || h <= 0 || (x == 0 && y == 0 && w == shot.width && h == shot.height)) return shot
        return Bitmap.createBitmap(shot, x, y, w, h).also { if (it !== shot) shot.recycle() }
    }

    private fun persist(asMistake: Boolean, subjectId: Long?, page: InkPage, shot: Bitmap?) {
        scope.launch {
            try {
                // Zeminli sayfalar ekran görüntüsünün üstünü kapatır; kayıtta zemin korunur, görüntü altta kalır.
                if (asMistake) repo.saveCaptureAsMistake(subjectId, "", 0, page, shot)
                else repo.saveCaptureAsNote(subjectId, page, shot)
                val where = subjects.firstOrNull { it.id == subjectId }?.name ?: "Genel"
                toast(if (asMistake) "Hatalı sorulara eklendi · $where" else "Deftere kaydedildi · $where")
            } catch (e: Exception) {
                toast("Kaydedilemedi: ${e.message}")
            }
        }
    }

    // ---- Taslak (oturumlar arasında çizimler kaybolmasın) ----

    private fun scheduleDraft() { handler.removeCallbacks(saveDraft); handler.postDelayed(saveDraft, 800) }

    private fun loadDraft() {
        val f = draftFile
        if (!f.exists()) return
        runCatching { InkJson.decodePages(f.readText()) }.onSuccess { (p, cur) ->
            // Çizimler geri gelir ama tahta zemini gelmez: ekran kalemi her zaman şeffaf başlar ve
            // doğrudan ekranın üzerine yazılır. (Açık unutulan beyaz tahta ekranı tümüyle kapatıyordu.)
            p.forEach { it.background = PageBackground.NONE }
            pages = p; pageIndex = cur
        }
    }

    private fun writeDraft() {
        val text = InkJson.encodePages(pages, pageIndex)
        scope.launch(Dispatchers.IO) { runCatching { draftFile.writeText(text) } }
    }

    private fun toast(msg: String) = Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()

    internal fun shutdown() { stopSelf() }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!attached) return
        grabber.onConfigurationChanged()
        toolbar.root.post { toolbar.clamp(); widgets.values.forEach { if (it.shown) it.clamp() } }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (attached) {
            // Senkron yaz: servis kapanırken son çizim kaybolmasın.
            runCatching { draftFile.writeText(InkJson.encodePages(pages, pageIndex)) }
            chooser?.dismiss(); dismissBanner()
            widgets.values.toList().forEach { it.dismiss() }
            runCatching { wm.removeView(toolbar.root) }
            runCatching { wm.removeView(ink) }
            attached = false
        }
        grabber.stop()
        running.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.karala.overlay.START"
        const val ACTION_STOP = "app.karala.overlay.STOP"
        const val ACTION_TOGGLE = "app.karala.overlay.TOGGLE"
        const val ACTION_PROJECTION = "app.karala.overlay.PROJECTION"
        const val ACTION_PROJECTION_DENIED = "app.karala.overlay.PROJECTION_DENIED"
        const val EXTRA_INPUT = "input"
        const val INPUT_HAND = "hand"
        const val INPUT_STYLUS = "stylus"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val NOTIF_ID = 1
        const val W_STOPWATCH = 0
        const val W_POMODORO = 1
        const val W_CALC = 2
        const val W_COUNTER = 3

        /** Servis çalışıyor mu? Arayüz bunu gözlemler. */
        val running = MutableStateFlow(false)

        /**
         * @param stylus çizim girişi önceden seçildiyse (true = kalem, false = el); null ise servis,
         *   "her seferinde sor" ayarı açıksa kendi seçim penceresini gösterir.
         */
        fun start(ctx: Context, stylus: Boolean? = null) {
            val i = Intent(ctx, OverlayService::class.java).setAction(ACTION_START)
            if (stylus != null) i.putExtra(EXTRA_INPUT, if (stylus) INPUT_STYLUS else INPUT_HAND)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, OverlayService::class.java)) }
    }
}
