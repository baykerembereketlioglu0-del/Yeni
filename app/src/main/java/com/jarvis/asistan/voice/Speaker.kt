package com.jarvis.asistan.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.jarvis.asistan.JarvisApp
import com.jarvis.asistan.JarvisState
import com.jarvis.asistan.Mode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Android TextToSpeech üzerinde sıralı (kuyruklu) Türkçe konuşma. */
class Speaker(context: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private val pending = MutableStateFlow(0)
    private val ids = AtomicInteger(0)
    private val tr = Locale("tr", "TR")

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                JarvisState.setMode(Mode.SPEAKING)
            }
            override fun onDone(utteranceId: String?) { dec() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { dec() }
            override fun onError(utteranceId: String?, errorCode: Int) { dec() }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { dec() }
        })
    }

    private fun dec() { pending.update { (it - 1).coerceAtLeast(0) } }

    suspend fun awaitReady(): Boolean {
        val ok = ready.await()
        if (ok) applySettings()
        return ok
    }

    fun applySettings() {
        val p = JarvisApp.prefs
        tts.language = tr
        val wanted = p.ttsVoice
        if (wanted.isNotEmpty()) {
            tts.voices?.firstOrNull { it.name == wanted }?.let { tts.voice = it }
        }
        tts.setSpeechRate(p.ttsRate)
        tts.setPitch(p.ttsPitch)
    }

    /** Kullanılabilir Türkçe sesler (ad listesi). */
    fun turkishVoices(): List<Voice> =
        (tts.voices ?: emptySet()).filter { it.locale.language == "tr" }.sortedBy { it.name }

    val isSpeaking: Boolean get() = pending.value > 0

    /** Metni kuyruğa ekler, beklemeden döner. */
    fun enqueue(text: String) {
        val clean = sanitize(text)
        if (clean.isBlank()) return
        pending.update { it + 1 }
        val id = "u${ids.incrementAndGet()}"
        val res = tts.speak(clean, TextToSpeech.QUEUE_ADD, Bundle(), id)
        if (res != TextToSpeech.SUCCESS) dec()
    }

    /** Konuşur ve bitene kadar bekler. */
    suspend fun say(text: String) {
        enqueue(text)
        awaitIdle()
    }

    suspend fun awaitIdle(timeoutMs: Long = 120_000) {
        withTimeoutOrNull(timeoutMs) { pending.first { it == 0 } }
    }

    fun stop() {
        tts.stop()
        pending.value = 0
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    companion object {
        /** Markdown ve konuşulamayacak işaretleri temizler. */
        fun sanitize(s: String): String = s
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("[*_#`>|~]"), "")
            .replace(Regex("\\[(.*?)]\\((.*?)\\)"), "$1")
            .replace(Regex("^\\s*[-•]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
