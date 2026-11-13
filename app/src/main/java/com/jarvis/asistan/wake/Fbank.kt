package com.jarvis.asistan.wake

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Kaldi uyumlu 80 boyutlu log-mel filtre bankası (torchaudio.compliance.kaldi.fbank varsayılanları):
 * 25 ms pencere, 10 ms kaydırma, snip_edges, DC kaldırma, 0.97 ön vurgu, povey penceresi, 512 FFT.
 */
object Fbank {
    private const val SR = 16000
    private const val WIN = 400
    private const val SHIFT = 160
    private const val NFFT = 512
    const val BINS = 80
    private const val EPS = 1.1920929e-07

    private val window = DoubleArray(WIN) { i -> (0.5 - 0.5 * cos(2 * PI * i / (WIN - 1))).pow(0.85) }

    /** [bin][fftIndex] ağırlıkları (Kaldi MelBanks). */
    private val melWeights: Array<DoubleArray> by lazy {
        fun mel(f: Double) = 1127.0 * ln(1.0 + f / 700.0)
        val numFftBins = NFFT / 2
        val binWidth = SR.toDouble() / NFFT
        val melLow = mel(20.0)
        val melHigh = mel(SR / 2.0)
        val delta = (melHigh - melLow) / (BINS + 1)
        Array(BINS) { b ->
            val left = melLow + b * delta
            val center = left + delta
            val right = center + delta
            DoubleArray(numFftBins) { i ->
                val m = mel(binWidth * i)
                if (m > left && m < right) {
                    if (m <= center) (m - left) / (center - left) else (right - m) / (right - center)
                } else 0.0
            }
        }
    }

    /** @param x [-1, 1] aralığında 16 kHz ses. @return kare × 80 öznitelik (ortalaması çıkarılmış). */
    fun compute(x: FloatArray, meanNormalize: Boolean = true): Array<FloatArray> {
        if (x.size < WIN) return emptyArray()
        val frames = (x.size - WIN) / SHIFT + 1
        val out = Array(frames) { FloatArray(BINS) }
        val re = DoubleArray(NFFT)
        val im = DoubleArray(NFFT)
        val power = DoubleArray(NFFT / 2)
        for (f in 0 until frames) {
            val off = f * SHIFT
            var mean = 0.0
            for (i in 0 until WIN) mean += x[off + i]
            mean /= WIN
            val w = DoubleArray(WIN) { x[off + it] - mean }
            for (i in WIN - 1 downTo 1) w[i] -= 0.97 * w[i - 1]
            w[0] -= 0.97 * w[0]
            for (i in 0 until NFFT) {
                re[i] = if (i < WIN) w[i] * window[i] else 0.0
                im[i] = 0.0
            }
            fft(re, im)
            for (i in 0 until NFFT / 2) power[i] = re[i] * re[i] + im[i] * im[i]
            val row = out[f]
            for (b in 0 until BINS) {
                val wts = melWeights[b]
                var e = 0.0
                for (i in wts.indices) if (wts[i] != 0.0) e += wts[i] * power[i]
                row[b] = ln(max(e, EPS)).toFloat()
            }
        }
        if (meanNormalize && frames > 0) {
            for (b in 0 until BINS) {
                var m = 0.0
                for (f in 0 until frames) m += out[f][b]
                val mm = (m / frames).toFloat()
                for (f in 0 until frames) out[f][b] -= mm
            }
        }
        return out
    }

    /** Yerinde, yinelemeli radix-2 karmaşık FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
