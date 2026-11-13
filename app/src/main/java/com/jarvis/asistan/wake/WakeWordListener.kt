package com.jarvis.asistan.wake

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.jarvis.asistan.JarvisApp

/**
 * Mikrofonu arka planda dinler ve "Hey Jarvis" duyulduğunda [onWake] çağırır.
 * Konuşma tanıma başlamadan önce [stop] ile mikrofon serbest bırakılmalıdır.
 */
class WakeWordListener(private val context: Context, private val onWake: () -> Unit) {

    @Volatile private var running = false
    /** Her start() yeni bir nesil başlatır; eski iş parçacıkları kendi nesilleri bitince durur. */
    @Volatile private var generation = 0
    private var thread: Thread? = null
    private var engine: OpenWakeWord? = null

    val isRunning: Boolean get() = running

    private fun engine(): OpenWakeWord {
        engine?.let { return it }
        val a = context.assets
        return OpenWakeWord(
            a.open("melspectrogram.onnx").use { it.readBytes() },
            a.open("embedding_model.onnx").use { it.readBytes() },
            a.open("hey_jarvis_v0.1.onnx").use { it.readBytes() },
        ).also { engine = it }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start() {
        if (running) return
        thread?.let { t -> if (t != Thread.currentThread()) runCatching { t.join(1500) } }
        running = true
        val gen = ++generation
        thread = Thread({ loop(gen) }, "jarvis-wake").apply { start() }
    }

    @Synchronized
    fun stop() {
        running = false
        generation++
        thread?.let { t ->
            if (t != Thread.currentThread()) runCatching { t.join(1500) }
        }
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun loop(gen: Int) {
        fun alive() = running && generation == gen
        val eng = try {
            engine()
        } catch (e: Throwable) {
            Log.e(TAG, "Wake engine yüklenemedi", e)
            if (generation == gen) running = false
            return
        }
        eng.reset()
        val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, OpenWakeWord.CHUNK * 2 * 4)
            )
        } catch (e: Throwable) {
            Log.e(TAG, "AudioRecord açılamadı", e)
            if (generation == gen) running = false
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord başlatılamadı")
            rec.release()
            if (generation == gen) running = false
            return
        }
        val aec = if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
        val ns = if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null

        val chunk = ShortArray(OpenWakeWord.CHUNK)
        var hits = 0
        var fired = false
        try {
            rec.startRecording()
            while (alive()) {
                var off = 0
                while (off < chunk.size && alive()) {
                    val n = rec.read(chunk, off, chunk.size - off)
                    if (n < 0) break
                    off += n
                }
                if (!alive() || off < chunk.size) break
                val score = eng.process(chunk)
                val threshold = JarvisApp.prefs.wakeThreshold
                hits = if (score >= threshold) hits + 1 else 0
                if (hits >= 1) {
                    Log.i(TAG, "Hey Jarvis algılandı (skor=$score)")
                    fired = true
                    break
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Dinleme hatası", e)
        } finally {
            runCatching { rec.stop() }
            rec.release()
            aec?.release()
            ns?.release()
            if (generation == gen) running = false
        }
        // Mikrofon tamamen serbest bırakıldıktan sonra haber ver.
        if (fired && generation == gen) onWake()
    }

    fun release() {
        stop()
        engine?.close()
        engine = null
    }

    companion object { private const val TAG = "JarvisWake" }
}
