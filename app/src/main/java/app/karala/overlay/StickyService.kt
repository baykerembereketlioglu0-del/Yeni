package app.karala.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.karala.App
import app.karala.R
import app.karala.ink.InkJson
import app.karala.ink.InkPage
import app.karala.ink.PageBackground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Ekranda duran yapışkan not. Konum ve boyut piksel cinsindendir; [w]×[h] yazı alanının boyutudur. */
class StickyNote(
    val id: Long,
    var x: Int, var y: Int,
    var w: Int, var h: Int,
    var color: Int,
    var collapsed: Boolean,
    val page: InkPage,
)

/** Yapışkan notların kalıcı kaydı: tek bir JSON dosyası. Notlar ancak kullanıcı kaldırınca silinir. */
object StickyStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "sticky_notes.json")

    fun load(ctx: Context): MutableList<StickyNote> = runCatching {
        val f = file(ctx)
        if (!f.exists()) return mutableListOf()
        val arr = JSONObject(f.readText()).optJSONArray("notes") ?: JSONArray()
        MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            StickyNote(o.getLong("id"), o.getInt("x"), o.getInt("y"), o.getInt("w"), o.getInt("h"),
                o.getInt("c"), o.optBoolean("col"), InkJson.fromJson(o.getJSONObject("ink")).also { it.background = PageBackground.NONE })
        }
    }.getOrElse { mutableListOf() }

    fun encode(notes: List<StickyNote>): String {
        val arr = JSONArray()
        notes.forEach { n ->
            arr.put(JSONObject().put("id", n.id).put("x", n.x).put("y", n.y).put("w", n.w).put("h", n.h)
                .put("c", n.color).put("col", n.collapsed).put("ink", InkJson.toJson(n.page)))
        }
        return JSONObject().put("v", 1).put("notes", arr).toString()
    }

    fun write(ctx: Context, text: String) {
        val f = file(ctx)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
    }

    fun hasNotes(ctx: Context) = file(ctx).let { it.exists() && load(ctx).isNotEmpty() }
}

/**
 * Ekran notları: her uygulamanın üstünde duran, kalem yaklaşınca yazılabilir hâle gelen yapışkan notlar.
 * Her not ayrı ve küçük bir penceredir; ekranın geri kalanı normal kullanılmaya devam eder.
 * Notlar dosyaya yazılır; servis kapanıp açılsa da, cihaz yeniden başlasa da geri gelirler.
 */
class StickyService : Service() {
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val notes = mutableListOf<StickyNote>()
    private val windows = LinkedHashMap<Long, NoteWindow>()
    private var loaded = false
    var hidden = false; private set
    /** Bu oturumda bir notta kalem ucu algılandı mı? Algılanana kadar parmak da yazar (kalemsiz cihazda kilitlenmesin). */
    var stylusSeen = false; private set
    private val saveRunnable = Runnable { saveNow(async = true) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        if (!loaded) {
            loaded = true
            notes += StickyStore.load(this)
            notes.forEach { showWindow(it) }
            running.value = true
        }
        when (intent?.action) {
            ACTION_ADD -> { if (hidden) setHidden(false); addNote() }
            ACTION_TOGGLE -> setHidden(!hidden)
            else -> if (hidden) setHidden(false)
        }
        count.value = notes.size
        if (notes.isEmpty()) stopSelf()
        return START_NOT_STICKY
    }

    private fun foreground() {
        val n = buildNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(NOTIF_ID, n)
        }.onFailure { runCatching { startForeground(NOTIF_ID, n) } }
    }

    private fun pi(action: String, code: Int) = PendingIntent.getService(this, code,
        Intent(this, StickyService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, App.CH_OVERLAY)
        .setSmallIcon(R.drawable.ic_stat_pen)
        .setContentTitle(if (notes.size == 1) "Ekranda 1 not" else "Ekranda ${notes.size} not")
        .setContentText(if (hidden) "Gizli · göstermek için dokun" else "Kalemi notun üstüne getir, yazmaya başla")
        .setOngoing(true).setOnlyAlertOnce(true)
        .setContentIntent(pi(ACTION_TOGGLE, 11))
        .addAction(0, "Yeni not", pi(ACTION_ADD, 12))
        .addAction(0, if (hidden) "Göster" else "Gizle", pi(ACTION_TOGGLE, 11))
        .build()

    // ---- Notlar ----

    private fun addNote() {
        if (notes.size >= MAX_NOTES) { toast("En fazla $MAX_NOTES not açık olabilir"); return }
        val d = resources.displayMetrics.density
        val (sw, sh) = WindowDragger.screenSize(wm)
        val w = (260 * d).toInt().coerceAtMost(sw - (32 * d).toInt())
        val h = (200 * d).toInt()
        val k = notes.size % 6
        val note = StickyNote(
            id = (notes.maxOfOrNull { it.id } ?: 0L).coerceAtLeast(System.currentTimeMillis()) + 1,
            x = (sw - w) / 2 + (26 * d * k).toInt(), y = (sh * 0.18f).toInt() + (26 * d * k).toInt(),
            w = w, h = h, color = PAPERS[notes.size % PAPERS.size], collapsed = false, page = InkPage(),
        )
        notes += note
        showWindow(note)?.activate(fresh = true)
        count.value = notes.size
        foreground(); scheduleSave()
    }

    private fun showWindow(n: StickyNote): NoteWindow? {
        val w = NoteWindow(this, n)
        return if (w.show()) { windows[n.id] = w; if (hidden) w.root.visibility = View.GONE; w } else null
    }

    /** Notu ekrandan ve kayıttan kaldırır. Son not da kalkınca servis kapanır. */
    internal fun remove(n: StickyNote) {
        windows.remove(n.id)?.dismiss()
        notes.remove(n)
        count.value = notes.size
        saveNow(async = false)
        if (notes.isEmpty()) stopSelf() else foreground()
    }

    /** Notu bugünün "Ekran notları" defterine sayfa olarak kaydeder, sonra kaldırır. */
    internal fun archive(n: StickyNote) {
        val page = n.page.copy().also { it.background = PageBackground.WHITE }
        remove(n)
        if (page.isEmpty) return
        App.instance.scope.launch {
            runCatching { App.instance.repo.saveCaptureAsNote(null, page, null) }
                .onSuccess { toast("Not deftere kaydedildi") }
                .onFailure { toast("Kaydedilemedi: ${it.message}") }
        }
    }

    internal fun onStylusSeen() {
        if (stylusSeen) return
        stylusSeen = true
        windows.values.forEach { it.applyTool() }
    }

    private fun setHidden(h: Boolean) {
        hidden = h
        windows.values.forEach { it.root.visibility = if (h) View.GONE else View.VISIBLE }
        foreground()
    }

    /** Ekran kalemi katmanı sonradan açıldıysa notları onun üstüne taşır (aksi hâlde katman notları örter). */
    fun raise() { windows.values.forEach { it.raise() } }

    internal fun scheduleSave() { handler.removeCallbacks(saveRunnable); handler.postDelayed(saveRunnable, 700) }

    private fun saveNow(async: Boolean) {
        handler.removeCallbacks(saveRunnable)
        val text = StickyStore.encode(notes)
        if (async) App.instance.scope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { StickyStore.write(this@StickyService, text) } }
        else runCatching { StickyStore.write(this, text) }
    }

    internal fun toast(msg: String) = Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handler.post { windows.values.forEach { it.clamp() } }
    }

    override fun onDestroy() {
        if (loaded) saveNow(async = false)
        handler.removeCallbacksAndMessages(null)
        windows.values.toList().forEach { it.dismiss() }
        windows.clear()
        running.value = false
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_ADD = "app.karala.sticky.ADD"
        const val ACTION_SHOW = "app.karala.sticky.SHOW"
        const val ACTION_TOGGLE = "app.karala.sticky.TOGGLE"
        private const val NOTIF_ID = 3
        const val MAX_NOTES = 12

        /** Kâğıt renkleri: sarı, pembe, mavi, yeşil, mor, turuncu. */
        val PAPERS = intArrayOf(0xFFFFE97A.toInt(), 0xFFFFC2D4.toInt(), 0xFFB8DEFF.toInt(), 0xFFC4F2CC.toInt(), 0xFFDCCEFF.toInt(), 0xFFFFD3A8.toInt())

        val running = MutableStateFlow(false)
        /** Ekrandaki not sayısı (arayüz için). */
        val count = MutableStateFlow(0)
        var instance: StickyService? = null; private set

        fun add(ctx: Context) = start(ctx, ACTION_ADD)
        fun show(ctx: Context) = start(ctx, ACTION_SHOW)

        private fun start(ctx: Context, action: String) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, StickyService::class.java).setAction(action)) }
        }

        /** Kayıtlı not varsa ve servis çalışmıyorsa notları ekrana geri getirir. */
        fun restoreIfNeeded(ctx: Context) {
            if (!running.value && Settings.canDrawOverlays(ctx) && StickyStore.hasNotes(ctx)) show(ctx)
        }
    }
}
