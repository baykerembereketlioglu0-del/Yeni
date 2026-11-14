package app.karala.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.WindowManager

/**
 * MediaProjection ile ekran görüntüsü alır. Kullanıcı izni bir kez alınır ve katman oturumu boyunca
 * açık tutulur (Android 14+ her izinle yalnızca tek sanal ekrana izin verir).
 */
internal class ScreenGrabber(private val ctx: Context) {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var width = 0; private var height = 0
    private val lock = Any()
    private var latest: Image? = null

    /** Son karenin geldiği an (SystemClock.uptimeMillis). */
    @Volatile var lastFrameAt = 0L; private set

    val ready get() = projection != null

    fun start(resultCode: Int, data: Intent, onStopped: () -> Unit): Boolean {
        stop()
        val mgr = ctx.getSystemService(MediaProjectionManager::class.java)
        val p = runCatching { mgr.getMediaProjection(resultCode, data) }.getOrNull() ?: return false
        val t = HandlerThread("karala-grab").also { it.start() }
        val handler = Handler(t.looper)
        thread = t
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { release(); Handler(ctx.mainLooper).post(onStopped) }
        }, handler)
        projection = p
        val (w, h) = WindowDragger.screenSize(ctx.getSystemService(WindowManager::class.java))
        width = w; height = h
        return runCatching {
            val r = newReader(w, h, handler)
            reader = r
            display = p.createVirtualDisplay("karala-capture", w, h, ctx.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler)
            true
        }.getOrElse { stop(); false }
    }

    private fun newReader(w: Int, h: Int, handler: Handler): ImageReader =
        ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3).apply {
            setOnImageAvailableListener({ r ->
                val img = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                synchronized(lock) { latest?.close(); latest = img }
                lastFrameAt = SystemClock.uptimeMillis()
            }, handler)
        }

    /** Ekran döndüyse sanal ekranı yeni boyuta uyarlar. */
    fun onConfigurationChanged() {
        val d = display ?: return
        val t = thread ?: return
        val (w, h) = WindowDragger.screenSize(ctx.getSystemService(WindowManager::class.java))
        if (w == width && h == height) return
        width = w; height = h
        runCatching {
            val old = reader
            val r = newReader(w, h, Handler(t.looper))
            d.resize(w, h, ctx.resources.displayMetrics.densityDpi)
            d.surface = r.surface
            reader = r
            synchronized(lock) { latest?.close(); latest = null }
            old?.close()
        }
    }

    /** En son kareyi bitmap olarak döndürür; henüz kare yoksa null. */
    fun grab(): Bitmap? = synchronized(lock) {
        val img = latest ?: return null
        runCatching {
            val plane = img.planes[0]
            val rowPixels = plane.rowStride / plane.pixelStride
            val full = Bitmap.createBitmap(rowPixels, img.height, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(plane.buffer.rewind())
            if (rowPixels == img.width) full
            else Bitmap.createBitmap(full, 0, 0, img.width, img.height).also { full.recycle() }
        }.getOrNull()
    }

    private fun release() {
        runCatching { display?.release() }; display = null
        synchronized(lock) { runCatching { latest?.close() }; latest = null }
        runCatching { reader?.close() }; reader = null
        projection = null
        thread?.quitSafely(); thread = null
    }

    fun stop() {
        val p = projection
        release()
        runCatching { p?.stop() }
    }
}
