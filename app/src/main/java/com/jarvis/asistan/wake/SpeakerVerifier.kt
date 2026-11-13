package com.jarvis.asistan.wake

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Konuşmacı doğrulama: CAM++ (3D-Speaker, VoxCeleb) modeliyle sesten 512 boyutlu "ses izi" çıkarır.
 * Kayıtlı ses izine kosinüs benzerliği yüksekse konuşan kişi sahibidir.
 * Tamamen cihaz üzerinde çalışır.
 */
class SpeakerVerifier(model: ByteArray) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
    private val session = env.createSession(model, opts)
    private val input = session.inputNames.first()

    /** @param samples 16 kHz PCM16. @return normalize edilmiş ses izi; ses çok kısaysa null. */
    fun embed(samples: ShortArray, trim: Boolean = true): FloatArray? {
        var x = FloatArray(samples.size) { samples[it] / 32768f }
        if (trim) x = trimSilence(x) ?: return null
        return embedFloat(x)
    }

    fun embedFloat(x: FloatArray): FloatArray? {
        val feats = Fbank.compute(x)
        if (feats.size < MIN_FRAMES) return null
        val data = FloatArray(feats.size * Fbank.BINS)
        for (f in feats.indices) System.arraycopy(feats[f], 0, data, f * Fbank.BINS, Fbank.BINS)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, feats.size.toLong(), Fbank.BINS.toLong())).use { t ->
            session.run(mapOf(input to t)).use { out ->
                val fb = (out[0] as OnnxTensor).floatBuffer
                val e = FloatArray(fb.remaining())
                fb.get(e)
                return normalize(e)
            }
        }
    }

    override fun close() {
        session.close()
        opts.close()
    }

    companion object {
        /** En az ~0.4 sn konuşma gerekir. */
        private const val MIN_FRAMES = 40

        fun normalize(v: FloatArray): FloatArray {
            var n = 0.0
            for (a in v) n += a * a
            val s = sqrt(n).toFloat().coerceAtLeast(1e-9f)
            return FloatArray(v.size) { v[it] / s }
        }

        fun cosine(a: FloatArray, b: FloatArray): Float {
            var d = 0f
            for (i in a.indices) d += a[i] * b[i]
            return d
        }

        /** Başındaki ve sonundaki sessizliği atar (enerjisi tepe değerin 35 dB altındaki kısımlar). */
        fun trimSilence(x: FloatArray): FloatArray? {
            val frame = 320 // 20 ms
            val n = x.size / frame
            if (n < 5) return null
            val db = DoubleArray(n) { f ->
                var e = 0.0
                for (i in 0 until frame) { val v = x[f * frame + i]; e += v * v }
                10 * log10(e / frame + 1e-10)
            }
            val peak = db.max()
            if (peak < -55) return null // neredeyse sessiz
            val thr = peak - 35
            val first = db.indexOfFirst { it > thr }
            val last = db.indexOfLast { it > thr }
            val start = ((first - 5).coerceAtLeast(0)) * frame
            val end = ((last + 6).coerceAtMost(n)) * frame
            return x.copyOfRange(start, end)
        }

        /** Ses izlerinin ortalaması (normalize). */
        fun average(list: List<FloatArray>): FloatArray {
            val out = FloatArray(list.first().size)
            for (v in list) for (i in v.indices) out[i] += v[i]
            return normalize(out)
        }
    }
}
