package com.jarvis.asistan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.asistan.JarvisApp
import com.jarvis.asistan.service.JarvisService
import com.jarvis.asistan.wake.SpeakerVerifier
import com.jarvis.asistan.wake.VoiceLock
import com.jarvis.asistan.wake.WakeWordListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sahibin sesini tanıtma: 5 kez "Hey Jarvis" dedirtip ortalama ses izini kaydeder. */
class VoiceEnrollActivity : AppCompatActivity() {

    private lateinit var info: TextView
    private lateinit var progress: TextView
    private var listener: WakeWordListener? = null
    private val samples = ArrayList<FloatArray>()
    private val needed = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        val root = Ui.vertical(this, 24).apply { setBackgroundColor(Ui.BG); gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(Ui.text(this, "SESİNİ TANIT", 22f, Ui.CYAN, true).apply { letterSpacing = 0.3f })
        info = Ui.text(this, "Sessiz bir ortamda, normal ses tonunla ve tablete yaklaşık bir kol mesafesinden " +
            "5 kez \"Hey Jarvis\" de. Her seferinde ekrandaki sayaç ilerleyecek.", 16f, Ui.TEXT).apply {
            setPadding(0, 24, 0, 24)
        }
        progress = Ui.text(this, "", 40f, Ui.CYAN, true).apply { gravity = Gravity.CENTER }
        root.addView(info, Ui.lp())
        root.addView(progress, Ui.lp())
        root.addView(Ui.button(this, "Baştan başla") { restart() }, Ui.lp(-2, -2).apply { topMargin = 40 })
        root.addView(Ui.button(this, "Kapat") { finish() }, Ui.lp(-2, -2).apply { topMargin = 20 })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            info.text = "Önce mikrofon izni vermelisin (Kurulum ekranı)."
            return
        }
        JarvisService.command(this, JarvisService.ACTION_PAUSE_WAKE)
        restart()
    }

    override fun onPause() {
        super.onPause()
        listener?.release()
        listener = null
        JarvisService.command(this, JarvisService.ACTION_RESUME_WAKE)
    }

    private fun restart() {
        samples.clear()
        updateProgress()
        listen()
    }

    private fun updateProgress() {
        progress.text = "${samples.size} / $needed"
    }

    private fun listen() {
        lifecycleScope.launch {
            delay(600) // servis mikrofonu bıraksın
            val l = listener ?: WakeWordListener(this@VoiceEnrollActivity, thresholdOverride = 0.3f) { audio ->
                lifecycleScope.launch { onUtterance(audio) }
            }.also { listener = it }
            withContext(Dispatchers.IO) { l.start() }
        }
    }

    private suspend fun onUtterance(audio: ShortArray) {
        val emb = withContext(Dispatchers.Default) {
            runCatching { VoiceLock.verifier(this@VoiceEnrollActivity).embed(audio) }.getOrNull()
        }
        if (emb == null) {
            info.text = "Bu kayıt çok kısa ya da sessizdi. Tekrar \"Hey Jarvis\" de."
            listen()
            return
        }
        samples.add(emb)
        updateProgress()
        if (samples.size < needed) {
            info.text = "Güzel! Tekrar \"Hey Jarvis\" de."
            listen()
            return
        }
        // Kayıtların birbirine benzerliğinden kişiye özel eşik öner.
        val sims = ArrayList<Float>()
        for (i in samples.indices) for (j in i + 1 until samples.size) sims.add(SpeakerVerifier.cosine(samples[i], samples[j]))
        val mean = sims.average().toFloat()
        val profile = SpeakerVerifier.average(samples)
        val p = JarvisApp.prefs
        p.voiceProfile = profile
        p.voiceThreshold = (mean - 0.2f).coerceIn(0.35f, 0.65f)
        p.voiceLock = true
        info.text = "Sesin kaydedildi ✓\nArtık Jarvis yalnızca senin \"Hey Jarvis\" demene cevap verecek.\n" +
            "(Kayıt tutarlılığı: %${(mean * 100).toInt()}, eşik: %${(p.voiceThreshold * 100).toInt()}. " +
            "Ayarlardan hassasiyeti değiştirebilirsin.)"
        listener?.release()
        listener = null
    }
}
