package com.jarvis.asistan.device

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Kamerayı kısa süreliğine açıp bir kare çeker ve Jarvis'e döndürür. */
class CameraActivity : AppCompatActivity() {

    private lateinit var countdown: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val preview = PreviewView(this)
        countdown = TextView(this).apply {
            setTextColor(Color.rgb(0, 229, 255))
            textSize = 72f
            gravity = Gravity.CENTER
            setShadowLayer(18f, 0f, 0f, Color.rgb(0, 229, 255))
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(countdown, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(root)

        val back = intent.getBooleanExtra(EXTRA_BACK, false)
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val selector = if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            val p = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, p, capture)
            } catch (e: Exception) {
                deliver(null, "Kamera açılamadı: ${e.message}")
                return@addListener
            }
            lifecycleScope.launch {
                for (i in 3 downTo 1) {
                    countdown.text = i.toString()
                    delay(700)
                }
                countdown.text = ""
                capture.takePicture(ContextCompat.getMainExecutor(this@CameraActivity),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bmp = runCatching { toBitmap(image, false) }.getOrNull()
                            image.close()
                            deliver(bmp, if (bmp == null) "Görüntü işlenemedi." else null)
                        }
                        override fun onError(exception: ImageCaptureException) {
                            deliver(null, "Fotoğraf çekilemedi: ${exception.message}")
                        }
                    })
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toBitmap(image: ImageProxy, mirror: Boolean): Bitmap {
        val bmp = image.toBitmap()
        val m = Matrix().apply {
            postRotate(image.imageInfo.rotationDegrees.toFloat())
            if (mirror) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun deliver(bmp: Bitmap?, error: String?) {
        pending?.complete(CaptureResult(bmp, error))
        pending = null
        finish()
    }

    override fun onDestroy() {
        pending?.complete(CaptureResult(null, "Kamera kapatıldı."))
        pending = null
        super.onDestroy()
    }

    data class CaptureResult(val bitmap: Bitmap?, val error: String?)

    companion object {
        private const val EXTRA_BACK = "back"
        @Volatile private var pending: CompletableDeferred<CaptureResult>? = null

        suspend fun capture(context: Context, back: Boolean): CaptureResult {
            val d = CompletableDeferred<CaptureResult>()
            pending?.complete(CaptureResult(null, "İptal edildi"))
            pending = d
            val intent = Intent(context, CameraActivity::class.java)
                .putExtra(EXTRA_BACK, back)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                pending = null
                return CaptureResult(null, "Kamera ekranı açılamadı: ${e.message}")
            }
            return withTimeoutOrNull(20_000) { d.await() }
                ?: CaptureResult(null, "Kamera zaman aşımına uğradı (\"Diğer uygulamaların üzerinde göster\" izni gerekebilir).")
        }

        @Suppress("unused")
        fun decode(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
