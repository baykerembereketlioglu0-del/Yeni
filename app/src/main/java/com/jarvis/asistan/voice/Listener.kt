package com.jarvis.asistan.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jarvis.asistan.JarvisState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Android SpeechRecognizer ile tek seferlik Türkçe konuşma tanıma. */
class Listener(private val context: Context) {

    sealed class Result {
        data class Text(val text: String) : Result()
        data object Silence : Result()
        data class Error(val message: String) : Result()
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    suspend fun listen(): Result = withContext(Dispatchers.Main) {
        if (!isAvailable()) {
            return@withContext Result.Error(
                "Bu tablette konuşma tanıma servisi bulunamadı. Google uygulamasını yükleyip güncelleyin."
            )
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        try {
            suspendCancellableCoroutine<Result> { cont ->
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
                var done = false
                fun finish(r: Result) {
                    if (done) return
                    done = true
                    JarvisState.setPartial("")
                    JarvisState.setLevel(0f)
                    if (cont.isActive) cont.resume(r)
                }
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        JarvisState.setLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> finish(Result.Silence)
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> finish(Result.Error("İnternet bağlantısı yok gibi görünüyor."))
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> finish(Result.Error("Mikrofon izni verilmemiş."))
                            else -> finish(Result.Error("Konuşma tanıma hatası ($error)."))
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        finish(if (text.isNullOrBlank()) Result.Silence else Result.Text(text))
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.let { JarvisState.setPartial(it) }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                cont.invokeOnCancellation { runCatching { recognizer.cancel() } }
                recognizer.startListening(intent)
            }
        } finally {
            runCatching { recognizer.destroy() }
        }
    }
}
