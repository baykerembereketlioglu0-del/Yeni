package com.jarvis.asistan.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.asistan.JarvisApp
import com.jarvis.asistan.service.JarvisService
import com.jarvis.asistan.service.Scheduler
import com.jarvis.asistan.voice.Speaker
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class SettingsActivity : AppCompatActivity() {

    private val p get() = JarvisApp.prefs
    private lateinit var form: LinearLayout
    private var speaker: Speaker? = null

    private val models = listOf(
        "claude-opus-5-5" to "Claude Opus 5.5 (en akıllı, önerilen)",
        "claude-sonnet-5-5" to "Claude Sonnet 5.5 (hızlı, daha ucuz)",
        "claude-haiku-5-5" to "Claude Haiku 5.5 (en hızlı, en ucuz)",
    )
    private val efforts = listOf("low" to "Hızlı (önerilen)", "medium" to "Dengeli", "high" to "Derin düşünme")
    private val alanlar = listOf("SAY" to "Sayısal", "EA" to "Eşit Ağırlık", "SOZ" to "Sözel", "DIL" to "Dil")
    private val days = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        form = Ui.vertical(this, 18).apply { setBackgroundColor(Ui.BG) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(form) })
        build()
    }

    private fun field(label: String, value: String, hint: String = "", secret: Boolean = false, onSave: (String) -> Unit): EditText {
        form.addView(Ui.text(this, label, 14f, Ui.DIM).apply { setPadding(0, Ui.dp(this@SettingsActivity, 10), 0, 0) })
        val et = EditText(this).apply {
            setText(value)
            this.hint = hint
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.DIM)
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        form.addView(et, Ui.lp())
        savers += { onSave(et.text.toString()) }
        return et
    }

    private fun <T> spinner(label: String, items: List<Pair<T, String>>, current: T, onSave: (T) -> Unit) {
        form.addView(Ui.text(this, label, 14f, Ui.DIM).apply { setPadding(0, Ui.dp(this@SettingsActivity, 10), 0, 0) })
        val sp = Spinner(this)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items.map { it.second }) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply { setTextColor(Ui.TEXT) }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sp.adapter = adapter
        sp.setSelection(items.indexOfFirst { it.first == current }.coerceAtLeast(0))
        form.addView(sp, Ui.lp())
        savers += { onSave(items[sp.selectedItemPosition].first) }
    }

    private fun switch(label: String, value: Boolean, onSave: (Boolean) -> Unit) {
        @Suppress("UseSwitchCompatOrMaterialCode")
        val sw = Switch(this).apply {
            text = label
            isChecked = value
            setTextColor(Ui.TEXT)
            textSize = 15f
            setPadding(0, Ui.dp(this@SettingsActivity, 10), 0, 0)
        }
        form.addView(sw, Ui.lp())
        savers += { onSave(sw.isChecked) }
    }

    private fun slider(label: String, min: Float, max: Float, value: Float, fmt: (Float) -> String, onSave: (Float) -> Unit) {
        val tv = Ui.text(this, "", 14f, Ui.DIM).apply { setPadding(0, Ui.dp(this@SettingsActivity, 10), 0, 0) }
        form.addView(tv)
        val sb = SeekBar(this).apply {
            this.max = 100
            progress = (((value - min) / (max - min)) * 100).toInt()
        }
        fun v() = min + (max - min) * sb.progress / 100f
        tv.text = "$label: ${fmt(v())}"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) { tv.text = "$label: ${fmt(v())}" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        form.addView(sb, Ui.lp())
        savers += { onSave(v()) }
    }

    private val savers = ArrayList<() -> Unit>()

    private fun build() {
        form.addView(Ui.text(this, "AYARLAR", 22f, Ui.CYAN, true).apply { letterSpacing = 0.3f })

        form.addView(Ui.title(this, "BEYİN (CLAUDE)"))
        field("Claude API anahtarı (console.anthropic.com > API Keys)", p.apiKey, "sk-ant-...", secret = true) { p.apiKey = it }
        spinner("Model", models, p.model) { p.model = it }
        spinner("Düşünme hızı", efforts, p.effort) { p.effort = it }

        form.addView(Ui.title(this, "SEN"))
        field("Adın", p.userName, "örn. Kerem") { p.userName = it }
        field("Sana nasıl hitap etsin?", p.hitap, "efendim") { p.hitap = it }
        field("Şehir (hava durumu için)", p.city, "örn. İstanbul") { p.city = it }
        spinner("YKS alanın", alanlar, p.alan) { p.alan = it }
        field("TYT tarihi (YYYY-AA-GG)", p.yksDate, "2027-06-19") {
            if (runCatching { LocalDate.parse(it.trim()) }.isSuccess) p.yksDate = it
        }

        form.addView(Ui.title(this, "SABAH"))
        switch("Alarm kapanınca günaydın de", p.morningEnabled) { p.morningEnabled = it }
        switch("Sabah tableti ilk açtığımda da günaydın de", p.morningOnUnlock) { p.morningOnUnlock = it }

        form.addView(Ui.title(this, "DENEME GÜNÜ"))
        switch("Denemeden dönünce nasıl geçtiğini sor", p.denemeAskEnabled) { p.denemeAskEnabled = it }
        spinner("Deneme günü", days.mapIndexed { i, d -> (i + 1) to d }, p.denemeDay) { p.denemeDay = it }
        field("Deneme bitiş saati (HH:mm) — bu saatten sonra tableti açınca sorar", p.denemeEnd, "13:00") {
            if (runCatching { LocalTime.parse(it.trim()) }.isSuccess) p.denemeEnd = it.trim()
        }

        form.addView(Ui.title(this, "AKŞAM MOTİVASYONU"))
        switch("Her akşam motivasyon ver", p.eveningEnabled) { p.eveningEnabled = it }
        field("Saat (HH:mm)", p.eveningTime, "21:00") {
            if (runCatching { LocalTime.parse(it.trim()) }.isSuccess) p.eveningTime = it.trim()
        }

        form.addView(Ui.title(this, "SES"))
        switch("\"Hey Jarvis\" ile uyan", p.wakeEnabled) { p.wakeEnabled = it }
        slider("Uyanma hassasiyeti", 0.2f, 0.9f, p.wakeThreshold, { v ->
            when {
                v < 0.35f -> "çok hassas (yanlış uyanabilir)"
                v < 0.6f -> "normal"
                else -> "seçici (yüksek sesle söyleyin)"
            }
        }) { p.wakeThreshold = it }
        slider("Konuşma hızı", 0.6f, 1.6f, p.ttsRate, { "%.1fx".format(it) }) { p.ttsRate = it }
        slider("Ses tonu", 0.6f, 1.4f, p.ttsPitch, { "%.1f".format(it) }) { p.ttsPitch = it }

        val voiceBox = Ui.vertical(this)
        form.addView(voiceBox, Ui.lp())
        val s = Speaker(this).also { speaker = it }
        lifecycleScope.launch {
            if (!s.awaitReady()) {
                voiceBox.addView(Ui.text(this@SettingsActivity, "Metin okuma motoru bulunamadı. Play Store'dan \"Google Konuşma Hizmetleri\"ni yükleyin.", 14f, Ui.BAD))
                return@launch
            }
            val voices = s.turkishVoices()
            if (voices.isEmpty()) {
                voiceBox.addView(Ui.text(this@SettingsActivity, "Türkçe ses bulunamadı. Ayarlar > Metin okuma çıktısı bölümünden Türkçe ses paketini indirin.", 14f, Ui.BAD))
            } else {
                val items = listOf("" to "Varsayılan Türkçe ses") + voices.map { v ->
                    v.name to v.name + if (v.isNetworkConnectionRequired) " (internet)" else ""
                }
                spinner("Jarvis'in sesi (erkek sesini deneyerek bulun)", items, p.ttsVoice) { p.ttsVoice = it }
            }
            form.addView(Ui.button(this@SettingsActivity, "Sesi dene") {
                saveAll()
                s.applySettings()
                s.enqueue("Merhaba ${p.hitap}. Tüm sistemler çevrimiçi. Bugün size nasıl yardımcı olabilirim?")
            }, Ui.lp(-2, -2).apply { topMargin = Ui.dp(this@SettingsActivity, 8) })
            addFooter()
        }
    }

    private fun addFooter() {
        form.addView(Ui.title(this, "HAFIZA VE VERİLER"))
        val mem = p.memories
        form.addView(Ui.text(this, if (mem.isEmpty()) "Jarvis henüz senin hakkında bir şey kaydetmedi." else mem.mapIndexed { i, m -> "${i + 1}. $m" }.joinToString("\n"), 14f, Ui.DIM))
        form.addView(Ui.text(this, "Kayıtlı deneme sayısı: ${p.denemeler.length()}", 14f, Ui.DIM).apply { setPadding(0, 8, 0, 0) })
        form.addView(Ui.button(this, "Hafızayı temizle") {
            p.memories = emptyList()
            Toast.makeText(this, "Hafıza temizlendi", Toast.LENGTH_SHORT).show()
        }, Ui.lp(-2, -2).apply { topMargin = Ui.dp(this@SettingsActivity, 8) })

        form.addView(Ui.button(this, "Kaydet") {
            saveAll()
            Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
            finish()
        }, Ui.lp().apply { topMargin = Ui.dp(this@SettingsActivity, 24) })
    }

    private fun saveAll() {
        savers.forEach { it() }
        Scheduler.scheduleEvening(this)
        JarvisService.command(this, JarvisService.ACTION_REFRESH)
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) runCatching { saveAll() }
    }

    override fun onDestroy() {
        speaker?.shutdown()
        super.onDestroy()
    }
}
