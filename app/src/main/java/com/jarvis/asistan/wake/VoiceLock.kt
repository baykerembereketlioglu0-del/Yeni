package com.jarvis.asistan.wake

import android.content.Context
import android.util.Log
import com.jarvis.asistan.JarvisApp

/**
 * "Yalnızca benim sesim" kilidi: "Hey Jarvis" diyen kişinin ses izini kayıtlı profille karşılaştırır.
 * Başarılı ve güvenli doğrulamalarda profili yavaşça günceller (sesine zamanla uyum sağlar).
 */
object VoiceLock {
    private const val TAG = "JarvisVoiceLock"
    @Volatile private var verifier: SpeakerVerifier? = null

    @Synchronized
    fun verifier(ctx: Context): SpeakerVerifier =
        verifier ?: SpeakerVerifier(ctx.assets.open("speaker_campplus.onnx").use { it.readBytes() }).also { verifier = it }

    val isActive: Boolean
        get() = JarvisApp.prefs.voiceLock && JarvisApp.prefs.voiceProfile != null

    data class Result(val accepted: Boolean, val score: Float, val reason: String)

    fun check(ctx: Context, audio: ShortArray): Result {
        val p = JarvisApp.prefs
        val profile = p.voiceProfile ?: return Result(true, 1f, "profil yok")
        val emb = try {
            verifier(ctx).embed(audio)
        } catch (e: Throwable) {
            Log.e(TAG, "Ses izi çıkarılamadı", e)
            null
        } ?: return Result(false, 0f, "ses çok kısa/sessiz")
        val score = SpeakerVerifier.cosine(profile, emb)
        val thr = p.voiceThreshold
        val ok = score >= thr
        if (ok && score >= thr + 0.15f) {
            // Uyum: profili %5 oranında yeni örneğe yaklaştır.
            val updated = FloatArray(profile.size) { profile[it] * 0.95f + emb[it] * 0.05f }
            p.voiceProfile = SpeakerVerifier.normalize(updated)
        }
        Log.i(TAG, "Ses benzerliği=%.3f eşik=%.2f -> %s".format(score, thr, if (ok) "KABUL" else "RET"))
        return Result(ok, score, if (ok) "sahip" else "tanınmayan ses")
    }
}
