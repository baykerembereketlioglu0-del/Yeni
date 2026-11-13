package com.jarvis.asistan

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisApp : Application() {
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Jarvis arka plan", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Jarvis'in \"Hey Jarvis\" komutunu dinlediğini gösterir"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Jarvis uyarıları", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    companion object {
        const val CH_SERVICE = "jarvis_service"
        const val CH_ALERT = "jarvis_alert"
        lateinit var instance: JarvisApp
            private set
        val prefs: Prefs get() = instance.prefs
    }
}

/** Arayüzün izlediği canlı durum. */
enum class Mode(val label: String) {
    OFF("Uyku modunda"),
    IDLE("\"Hey Jarvis\" bekleniyor"),
    LISTENING("Dinliyorum…"),
    THINKING("Düşünüyorum…"),
    SPEAKING("Konuşuyorum…"),
    ERROR("Bir sorun var"),
}

data class Line(val fromUser: Boolean, val text: String)

object JarvisState {
    private val _mode = MutableStateFlow(Mode.OFF)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    /** 0..1 arası mikrofon/ses seviyesi (animasyon için). */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    fun setMode(m: Mode) { _mode.value = m }
    fun setPartial(s: String) { _partial.value = s }
    fun setLevel(l: Float) { _level.value = l }
    fun add(fromUser: Boolean, text: String) {
        if (text.isBlank()) return
        _lines.value = (_lines.value + Line(fromUser, text.trim())).takeLast(30)
    }
    /** Jarvis'in yeni cevabı için boş satır açar. */
    fun newReply() {
        _lines.value = (_lines.value.filter { it.text.isNotBlank() } + Line(false, "")).takeLast(30)
    }
    fun appendToLast(text: String) {
        val cur = _lines.value
        if (cur.isNotEmpty() && !cur.last().fromUser) {
            _lines.value = cur.dropLast(1) + Line(false, cur.last().text + text)
        } else add(false, text)
    }
}
