package com.jarvis.asistan.brain

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Tool
import com.jarvis.asistan.device.DeviceController
import com.jarvis.asistan.device.ToolOutput
import org.json.JSONObject

/** Claude'a sunulan araçlar ve tablet üzerinde çalıştırılmaları. */
object Tools {

    private fun s(d: String) = mapOf("type" to "string", "description" to d)
    private fun i(d: String) = mapOf("type" to "integer", "description" to d)
    private fun n(d: String) = mapOf("type" to "number", "description" to d)
    private fun b(d: String) = mapOf("type" to "boolean", "description" to d)
    private fun e(d: String, vararg v: String) = mapOf("type" to "string", "enum" to v.toList(), "description" to d)

    class Spec(
        val name: String,
        val description: String,
        val props: Map<String, Any> = emptyMap(),
        val required: List<String> = emptyList(),
        /** Çalıştırılmadan önce Jarvis'in sözünü bitirmesi beklenir (kapatma gibi). */
        val waitSpeech: Boolean = false,
    )

    val specs = listOf(
        Spec("konus", "Bir işlem yaparken kullanıcıya hemen sesli olarak bir şey söyler (örn. 'Tableti kapatıyorum efendim.', 'Hemen bakıyorum.'). Eylem aracıyla aynı yanıtta, ondan ÖNCE çağır. Son cevabını bununla verme; son cevap normal metin olmalı.",
            mapOf("metin" to s("Söylenecek kısa cümle")), listOf("metin")),
        Spec("tableti_kapat", "Tableti tamamen kapatır (güç kapatma). Kullanıcı açıkça kapatmanı isterse onay sormadan kullan.", waitSpeech = true),
        Spec("tableti_yeniden_baslat", "Tableti yeniden başlatır.", waitSpeech = true),
        Spec("ekrani_kilitle", "Ekranı kilitler / uyku moduna alır.", waitSpeech = true),
        Spec("sistem_tusu", "Sistem tuşu veya eylemi uygular.",
            mapOf("tus" to e("Eylem", "geri", "ana_ekran", "son_uygulamalar", "bildirimler", "hizli_ayarlar", "ekran_goruntusu", "guc_menusu", "bolunmus_ekran")),
            listOf("tus")),
        Spec("ses_ayarla", "Ses seviyesini ayarlar.",
            mapOf(
                "islem" to e("ayarla: seviyeye getir; artir/azalt: biraz değiştir", "ayarla", "artir", "azalt", "sessiz", "sesi_ac"),
                "seviye" to i("0-100 arası yüzde (islem=ayarla için)"),
                "tur" to e("Hangi ses", "medya", "zil", "alarm", "bildirim"),
            ), listOf("islem")),
        Spec("parlaklik_ayarla", "Ekran parlaklığını ayarlar.",
            mapOf("seviye" to i("1-100 yüzde"), "otomatik" to b("true ise otomatik parlaklığı açar"))),
        Spec("fener", "Fener (flaş ışığı) açar/kapatır.", mapOf("acik" to b("true=aç")), listOf("acik")),
        Spec("rahatsiz_etmeyin", "Rahatsız Etmeyin modunu açar/kapatır.", mapOf("acik" to b("true=aç")), listOf("acik")),
        Spec("hizli_ayar", "Hızlı ayarlar panelindeki bir düğmeye basarak açıp kapatır: Wi-Fi, Bluetooth, uçak modu, konum, otomatik döndürme, mobil veri, hotspot, karanlık mod, göz konforu vb.",
            mapOf("ad" to s("Düğmenin adı, örn. 'wifi', 'bluetooth', 'uçak modu'")), listOf("ad")),
        Spec("uygulama_ac", "Yüklü bir uygulamayı adıyla açar (YouTube, WhatsApp, Kamera, Ayarlar, Galeri…).",
            mapOf("ad" to s("Uygulama adı")), listOf("ad")),
        Spec("uygulamalari_listele", "Tablette yüklü uygulamaların adlarını listeler."),
        Spec("web_ac", "Tarayıcıda bir web adresi açar.", mapOf("url" to s("Adres")), listOf("url")),
        Spec("muzik_cal", "Şarkı/sanatçı/çalma listesi veya video çalar/arar.",
            mapOf("sorgu" to s("Ne çalınacak"), "uygulama" to e("Tercih edilen uygulama", "otomatik", "youtube", "spotify")),
            listOf("sorgu")),
        Spec("medya_kontrol", "Çalan müziği/videoyu kontrol eder.",
            mapOf("islem" to e("İşlem", "oynat_duraklat", "oynat", "duraklat", "sonraki", "onceki", "durdur")), listOf("islem")),
        Spec("alarm_kur", "Saat uygulamasında alarm kurar.",
            mapOf(
                "saat" to i("0-23"), "dakika" to i("0-59"), "etiket" to s("İsteğe bağlı açıklama"),
                "gunler" to mapOf("type" to "array", "items" to mapOf("type" to "integer"), "description" to "Tekrarlanacak günler: 1=Pazartesi … 7=Pazar. Boş = tek seferlik"),
            ), listOf("saat", "dakika")),
        Spec("zamanlayici_kur", "Geri sayım zamanlayıcısı başlatır.",
            mapOf("saniye" to i("Süre (saniye)"), "etiket" to s("İsteğe bağlı")), listOf("saniye")),
        Spec("alarmlari_goster", "Bir sonraki alarmı söyler; istenirse alarm listesini ekranda açar.",
            mapOf("ekranda_ac" to b("true ise saat uygulamasını açar"))),
        Spec("hatirlatici_kur", "Jarvis'in belirtilen zamanda sesli hatırlatma yapmasını sağlar (pomodoro, ders, mola, ilaç…).",
            mapOf(
                "dakika_sonra" to i("Şu andan kaç dakika sonra"),
                "tarih" to s("YYYY-AA-GG (isteğe bağlı)"),
                "saat" to s("HH:mm (isteğe bağlı)"),
                "mesaj" to s("Hatırlatılacak şey"),
            ), listOf("mesaj")),
        Spec("hatirlaticilari_listele", "Kurulu hatırlatıcıları listeler."),
        Spec("hatirlatici_iptal", "Hatırlatıcıyı numarasıyla iptal eder.", mapOf("no" to i("Hatırlatıcı numarası")), listOf("no")),
        Spec("cihaz_durumu", "Pil, şarj, depolama, RAM, internet, ses ve parlaklık bilgisini verir."),
        Spec("kamerayla_bak", "Kamerayı açıp fotoğraf çeker ve sana gösterir. Kullanıcı 'nasıl görünüyorum', 'bak', 'elimde ne var' gibi şeyler derse kullan.",
            mapOf("kamera" to e("Hangi kamera", "on", "arka"))),
        Spec("ekrana_bak", "Tabletin ekran görüntüsünü alıp sana gösterir."),
        Spec("ekrani_oku", "Ekrandaki yazıları ve tıklanabilir öğeleri metin olarak verir (uygulamaları kullanmak için)."),
        Spec("ekranda_tikla", "Ekranda yazısı/etiketi verilen öğeye dokunur.", mapOf("metin" to s("Öğenin yazısı")), listOf("metin")),
        Spec("yazi_yaz", "Odaklı yazı alanına metin yazar.", mapOf("metin" to s("Yazılacak metin")), listOf("metin")),
        Spec("kaydir", "Ekranı kaydırır.", mapOf("yon" to e("Yön", "asagi", "yukari", "sola", "saga")), listOf("yon")),
        Spec("bildirimleri_oku", "Tabletteki güncel bildirimleri okur."),
        Spec("hafizaya_kaydet", "Kullanıcı hakkında kalıcı olarak hatırlanması gereken bir bilgiyi kaydeder (hedef bölüm, zayıf dersler, sevdiği şeyler, önemli tarihler…).",
            mapOf("bilgi" to s("Kısa ve net bilgi cümlesi")), listOf("bilgi")),
        Spec("hafizadan_sil", "Hafızadaki bir bilgiyi numarasıyla siler.", mapOf("no" to i("Bilgi numarası (1'den başlar)")), listOf("no")),
        Spec("deneme_kaydet", "YKS deneme sınavı sonucunu kaydeder. Kaydettikten sonra geçmişle karşılaştırma döner.",
            mapOf(
                "tur" to e("Deneme türü", "TYT", "AYT", "YDT"),
                "tarih" to s("YYYY-AA-GG; boşsa bugün"),
                "yayin" to s("Deneme yayını (isteğe bağlı)"),
                "toplam_net" to n("Toplam net"),
                "dersler" to mapOf(
                    "type" to "array",
                    "description" to "Ders bazında sonuçlar",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "ders" to s("Ders adı"), "dogru" to i("Doğru"), "yanlis" to i("Yanlış"), "net" to n("Net"),
                        ),
                        "required" to listOf("ders"),
                    ),
                ),
                "notlar" to s("Kullanıcının yorumları: zorlandığı konular, süre sorunu, moral vb."),
            ), listOf("tur")),
        Spec("deneme_gecmisi", "Kayıtlı deneme sonuçlarını ve gidişatı getirir.",
            mapOf("tur" to e("Filtre (boş = hepsi)", "TYT", "AYT", "YDT"))),
        Spec("sohbeti_bitir", "Kullanıcı vedalaştığında, teşekkür edip bitirdiğinde ya da başka isteği kalmadığında sohbet modunu kapatır; Jarvis tekrar 'Hey Jarvis' beklemeye döner."),
    )

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
            "sohbeti_bitir" -> ToolOutput("Sohbet modu kapanacak.")
            "konus" -> ToolOutput("Söylendi.") // Seslendirme ClaudeBrain içinde yapılır.
            else -> ToolOutput("Bilinmeyen araç: $name", isError = true)
        }
    }
}
