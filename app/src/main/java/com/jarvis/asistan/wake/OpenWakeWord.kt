package com.jarvis.asistan.wake

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * openWakeWord (https://github.com/dscripka/openWakeWord) akış hattının birebir Kotlin karşılığı.
 *
 * Ses: 16 kHz, 16-bit mono PCM; [process] her çağrıda 1280 örnek (80 ms) bekler.
 * Hat: ham ses -> melspectrogram.onnx -> embedding_model.onnx (76 kare pencere, 8 kare adım)
 *      -> hey_jarvis.onnx (son 16 gömme) -> 0..1 arası skor.
 *
 * Android'e bağımlı değildir; böylece masaüstü JVM'de Python referansıyla karşılaştırılarak test edilebilir.
 */
class OpenWakeWord(
    melModel: ByteArray,
    embeddingModel: ByteArray,
    wakeModel: ByteArray,
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val opts = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(1)
        setInterOpNumThreads(1)
    }
    private val melSession = env.createSession(melModel, opts)
    private val embSession = env.createSession(embeddingModel, opts)
    private val wakeSession = env.createSession(wakeModel, opts)

    private val melInput = melSession.inputNames.first()
    private val embInput = embSession.inputNames.first()
    private val wakeInput = wakeSession.inputNames.first()

    private var raw = ShortArray(0)
    private val melBuffer = ArrayList<FloatArray>()
    private val featureBuffer = ArrayList<FloatArray>()
    private var accumulated = 0
    private var predictions = 0

    init {
        reset()
    }

    /** Tamponları başlangıç durumuna döndürür (Python AudioFeatures.__init__ ile aynı). */
    fun reset() {
        raw = ShortArray(0)
        accumulated = 0
        predictions = 0
        melBuffer.clear()
        repeat(76) { melBuffer.add(FloatArray(32) { 1f }) }
        featureBuffer.clear()
        // 10 saniyelik sessizliğin gömmeleri ile doldur.
        val silenceMel = melspectrogram(ShortArray(160000))
        var i = 0
        while (i + 76 <= silenceMel.size) {
            featureBuffer.add(embedding(silenceMel, i))
            i += 8
        }
        trimFeatures()
    }

    /** 1280 örneklik bir parça işler, uyandırma skorunu (0..1) döndürür. */
    fun process(chunk: ShortArray): Float {
        appendRaw(chunk)
        accumulated += chunk.size
        if (accumulated >= 1280) {
            val n = accumulated
            val take = minOf(raw.size, n + 160 * 3)
            val mel = melspectrogram(raw.copyOfRange(raw.size - take, raw.size))
            melBuffer.addAll(mel)
            while (melBuffer.size > MEL_MAX) melBuffer.removeAt(0)

            for (i in (n / 1280 - 1) downTo 0) {
                val end = if (i == 0) melBuffer.size else melBuffer.size - 8 * i
                val start = end - 76
                if (start >= 0) featureBuffer.add(embedding(melBuffer, start))
            }
            accumulated = 0
        }
        trimFeatures()

        val score = predict()
        predictions++
        // Python referansı ilk 5 tahmini sıfırlar.
        return if (predictions <= 5) 0f else score
    }

    /** Son [samples] örneklik ham ses (konuşmacı doğrulaması için). */
    fun recent(samples: Int): ShortArray =
        if (raw.size <= samples) raw.copyOf() else raw.copyOfRange(raw.size - samples, raw.size)

    private fun appendRaw(chunk: ShortArray) {
        val merged = ShortArray(raw.size + chunk.size)
        System.arraycopy(raw, 0, merged, 0, raw.size)
        System.arraycopy(chunk, 0, merged, raw.size, chunk.size)
        raw = if (merged.size > RAW_MAX) merged.copyOfRange(merged.size - RAW_MAX, merged.size) else merged
    }

    private fun trimFeatures() {
        while (featureBuffer.size > FEATURE_MAX) featureBuffer.removeAt(0)
    }

    private fun melspectrogram(samples: ShortArray): List<FloatArray> {
        val data = FloatArray(samples.size) { samples[it].toFloat() }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, samples.size.toLong())).use { t ->
            melSession.run(mapOf(melInput to t)).use { out ->
                val tensor = out[0] as OnnxTensor
                val fb = tensor.floatBuffer
                val total = fb.remaining()
                val frames = total / 32
                val result = ArrayList<FloatArray>(frames)
                for (f in 0 until frames) {
                    val row = FloatArray(32)
                    for (k in 0 until 32) row[k] = fb.get(f * 32 + k) / 10f + 2f
                    result.add(row)
                }
                return result
            }
        }
    }

    private fun embedding(mel: List<FloatArray>, start: Int): FloatArray {
        val data = FloatArray(76 * 32)
        for (f in 0 until 76) System.arraycopy(mel[start + f], 0, data, f * 32, 32)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 76, 32, 1)).use { t ->
            embSession.run(mapOf(embInput to t)).use { out ->
                val fb = (out[0] as OnnxTensor).floatBuffer
                val result = FloatArray(96)
                fb.get(result)
                return result
            }
        }
    }

    private fun predict(): Float {
        val data = FloatArray(16 * 96)
        val base = featureBuffer.size - 16
        for (i in 0 until 16) System.arraycopy(featureBuffer[base + i], 0, data, i * 96, 96)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 16, 96)).use { t ->
            wakeSession.run(mapOf(wakeInput to t)).use { out ->
                return (out[0] as OnnxTensor).floatBuffer.get(0)
            }
        }
    }

    override fun close() {
        melSession.close()
        embSession.close()
        wakeSession.close()
        opts.close()
    }

    companion object {
        const val CHUNK = 1280
        private const val RAW_MAX = 16000 * 10
        private const val MEL_MAX = 10 * 97
        private const val FEATURE_MAX = 120
    }
}
