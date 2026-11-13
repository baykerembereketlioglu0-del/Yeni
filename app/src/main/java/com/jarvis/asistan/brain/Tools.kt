package com.jarvis.asistan.brain

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Tool
import com.jarvis.asistan.device.DeviceController
import com.jarvis.asistan.device.Program
import com.jarvis.asistan.service.Scheduler
import com.jarvis.asistan.device.ToolOutput
import org.json.JSONObject

/** Claude'a sunulan araçlar ve tablet üzerinde çalıştırılmaları. */
object Tools {

    val specs get() = ToolSpecs.specs

    private val byName = specs.associateBy { it.name }

    fun waitsForSpeech(name: String) = byName[name]?.waitSpeech == true

    fun toApiTools(): List<Tool> = specs.map { sp ->
        val props = Tool.InputSchema.Properties.builder().apply {
            sp.props.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) }
        }.build()
        Tool.builder()
            .name(sp.name)
            .description(sp.description)
            .inputSchema(Tool.InputSchema.builder().properties(props).required(sp.required).build())
            .eagerInputStreaming(true)
            .build()
    }

    /** Aracı çalıştırır. Girdi doğrulaması burada yapılır (akışlı girdiler eksik gelebilir). */
    suspend fun execute(d: DeviceController, name: String, inp: JSONObject): ToolOutput {
        val spec = byName[name] ?: return ToolOutput("Bilinmeyen araç: $name", isError = true)
        val missing = spec.required.filter { !inp.has(it) || inp.isNull(it) }
        if (missing.isNotEmpty()) return ToolOutput("Eksik parametre: ${missing.joinToString()}", isError = true)

        fun str(k: String) = inp.optString(k).takeIf { inp.has(k) && !inp.isNull(k) && it.isNotBlank() }
        fun int(k: String): Int? = if (inp.has(k) && !inp.isNull(k)) inp.optDouble(k).takeIf { !it.isNaN() }?.toInt() else null
        fun bool(k: String): Boolean? = if (inp.has(k) && !inp.isNull(k)) inp.optBoolean(k) else null
        fun longs(k: String): List<Long> = inp.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optLong(it) }.filter { it > 0 } } ?: emptyList()

        return when (name) {
            "tableti_kapat" -> d.powerOff()
            "tableti_yeniden_baslat" -> d.reboot()
            "ekrani_kilitle" -> d.lockScreen()
            "sistem_tusu" -> d.systemKey(str("tus")!!)
            "ses_ayarla" -> d.volume(str("islem") ?: "ayarla", int("seviye"), str("tur") ?: "medya")
            "parlaklik_ayarla" -> d.brightness(int("seviye"), bool("otomatik"))
            "fener" -> d.flashlight(bool("acik") ?: true)
            "rahatsiz_etmeyin" -> d.doNotDisturb(bool("acik") ?: true)
            "hizli_ayar" -> d.quickTile(str("ad")!!)
            "uygulama_ac" -> d.openApp(str("ad")!!)
            "uygulamalari_listele" -> d.listApps()
            "web_ac" -> d.openUrl(str("url")!!)
            "muzik_cal" -> d.playMusic(str("sorgu")!!, str("uygulama") ?: "otomatik")
            "medya_kontrol" -> d.media(str("islem")!!)
            "alarm_kur" -> {
                val h = int("saat") ?: return ToolOutput("saat gerekli", isError = true)
                val m = int("dakika") ?: 0
                if (h !in 0..23 || m !in 0..59) return ToolOutput("Geçersiz saat.", isError = true)
                val days = inp.optJSONArray("gunler")?.let { a -> (0 until a.length()).mapNotNull { a.optInt(it).takeIf { v -> v in 1..7 } } } ?: emptyList()
                d.setAlarm(h, m, str("etiket"), days)
            }
            "zamanlayici_kur" -> d.setTimer((int("saniye") ?: 60).coerceIn(1, 86400), str("etiket"))
            "alarmlari_goster" -> d.alarms(bool("ekranda_ac") == true)
            "hatirlatici_kur" -> d.reminder(int("dakika_sonra"), str("tarih"), str("saat"), str("mesaj")!!)
            "hatirlaticilari_listele" -> d.reminders()
            "hatirlatici_iptal" -> d.cancelReminder(int("no") ?: -1)
            "cihaz_durumu" -> d.status()
            "kamerayla_bak" -> d.camera(back = str("kamera") == "arka")
            "ekrana_bak" -> d.screenshot()
            "ekrani_oku" -> d.readScreen()
            "ekranda_tikla" -> d.click(str("metin")!!)
            "yazi_yaz" -> d.type(str("metin")!!)
            "kaydir" -> d.scroll(str("yon")!!)
            "bildirimleri_oku" -> d.notifications()
            "hafizaya_kaydet" -> d.remember(str("bilgi")!!)
            "hafizadan_sil" -> d.forget(int("no") ?: -1)
            "deneme_kaydet" -> d.saveDeneme(inp)
            "deneme_gecmisi" -> d.denemeHistory(str("tur"))
            "program_kaydet" -> {
                val arr = inp.optJSONArray("maddeler") ?: return ToolOutput("maddeler gerekli", isError = true)
                val msg = Program.save(arr, str("mod") ?: "degistir")
                Scheduler.scheduleNextProgram(d.context)
                ToolOutput(msg + "\n" + Program.show(null))
            }
            "program_goster" -> ToolOutput(Program.show(int("gun")))
            "program_sil" -> {
                val msg = if (bool("hepsi") == true) Program.clear() else Program.remove(int("gun") ?: return ToolOutput("gun gerekli", isError = true), str("baslangic"))
                Scheduler.scheduleNextProgram(d.context)
                ToolOutput(msg)
            }
            "program_durum_kaydet" -> ToolOutput(Program.logStatus(str("ad")!!, str("durum")!!, str("not") ?: "", str("tarih")))
            "program_raporu" -> ToolOutput(Program.report((int("gun_sayisi") ?: 7).coerceIn(1, 90)))
            "galeri_listele" -> d.media.list(str("tur") ?: "hepsi", str("album"), (int("baslangic") ?: 0).coerceAtLeast(0), int("adet") ?: 20)
            "galeriye_bak" -> d.media.look(longs("idler"))
            "galeri_sil" -> d.media.trash(longs("idler"))
            "galeri_geri_al" -> d.media.restoreLast()
            "galeri_klasorle" -> d.media.moveToFolder(longs("idler"), str("klasor")!!)
            "galeri_paylas" -> d.media.share(inp.optLong("id"), str("uygulama") ?: "", str("metin")) { d.launchIntent(it) }
            "dosyalari_listele" -> d.media.listFiles(str("yol") ?: "/")
            "dosya_ara" -> d.media.findFiles(str("ad")!!)
            "dosya_oku" -> d.media.readFile(str("yol")!!)
            "dosya_yaz" -> d.media.writeFile(str("yol")!!, inp.optString("icerik"), bool("ekle") == true)
            "sohbeti_bitir" -> ToolOutput("Sohbet modu kapanacak.")
            "konus" -> ToolOutput("Söylendi.") // Seslendirme ClaudeBrain içinde yapılır.
            else -> ToolOutput("Bilinmeyen araç: $name", isError = true)
        }
    }
}
