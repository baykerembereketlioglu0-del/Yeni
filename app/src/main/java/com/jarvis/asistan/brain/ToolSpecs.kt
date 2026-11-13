package com.jarvis.asistan.brain

/** Claude/Gemini'ye sunulan araçların tanımları (Android'den bağımsız). */
object ToolSpecs {
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
        // ---- Ders/çalışma programı ----
        Spec("program_kaydet", "Haftalık ders/çalışma programını kaydeder. Program saatlerinde Jarvis otomatik hatırlatır ve bitişte uygulanıp uygulanmadığını sorar.",
            mapOf(
                "mod" to e("degistir: tüm programı bununla değiştir; ekle: mevcut programa ekle; gunleri_degistir: yalnızca verilen günleri yenile", "degistir", "ekle", "gunleri_degistir"),
                "maddeler" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "gunler" to mapOf("type" to "array", "items" to mapOf("type" to "integer"), "description" to "1=Pazartesi … 7=Pazar"),
                            "baslangic" to s("HH:mm"), "bitis" to s("HH:mm"),
                            "ad" to s("Ders/etkinlik adı, örn. 'Matematik - Türev soru çözümü' veya 'Okul'"),
                            "detay" to s("İsteğe bağlı: hedef soru sayısı, kaynak, konu"),
                        ),
                        "required" to listOf("gunler", "baslangic", "bitis", "ad"),
                    ),
                ),
            ), listOf("mod", "maddeler")),
        Spec("program_goster", "Kayıtlı programı gösterir.", mapOf("gun" to i("1-7; boşsa tüm hafta"))),
        Spec("program_sil", "Programdan madde siler (başlangıç verilmezse o günün tamamı).",
            mapOf("gun" to i("1-7"), "baslangic" to s("HH:mm (isteğe bağlı)"), "hepsi" to b("true ise tüm program silinir"))),
        Spec("program_durum_kaydet", "Bir program maddesinin uygulanıp uygulanmadığını kaydeder.",
            mapOf("ad" to s("Madde adı"), "durum" to e("Durum", "yapildi", "kismen", "yapilmadi"), "not" to s("Kısa not: neden, ne kadar yapıldı…"), "tarih" to s("YYYY-AA-GG; boşsa bugün")),
            listOf("ad", "durum")),
        Spec("program_raporu", "Programa uyum raporu: genel ve ders bazında uygulama oranı, son kayıtlar.",
            mapOf("gun_sayisi" to i("Kaç günlük (varsayılan 7)"))),
        // ---- Galeri ve dosyalar ----
        Spec("galeri_listele", "Galerideki fotoğraf/videoları en yeniden eskiye listeler (kimlik numaralarıyla) ve albümleri gösterir.",
            mapOf("tur" to e("Tür", "hepsi", "foto", "video"), "album" to s("Albüm adı filtresi (isteğe bağlı)"),
                "baslangic" to i("Kaçıncı öğeden başlasın (0 = en yeni)"), "adet" to i("Kaç öğe (varsayılan 20, en fazla 100)"))),
        Spec("galeriye_bak", "Verilen kimliklerdeki fotoğraf/videoların küçük resimlerini tek bir görselde gösterir (en fazla 16). Tek kimlik verilirse büyük önizleme döner.",
            mapOf("idler" to mapOf("type" to "array", "items" to mapOf("type" to "integer"))), listOf("idler")),
        Spec("galeri_sil", "Fotoğraf/videoları çöp kutusuna taşır (30 gün geri alınabilir).",
            mapOf("idler" to mapOf("type" to "array", "items" to mapOf("type" to "integer"))), listOf("idler")),
        Spec("galeri_geri_al", "Son silme işlemini geri alır."),
        Spec("galeri_klasorle", "Fotoğraf/videoları bir klasöre (galeride albüm) taşır. Her klasör için ayrı çağır.",
            mapOf("idler" to mapOf("type" to "array", "items" to mapOf("type" to "integer")), "klasor" to s("Klasör/albüm adı")),
            listOf("idler", "klasor")),
        Spec("galeri_paylas", "Bir fotoğraf/videoyu başka bir uygulamaya gönderir (YouTube yükleme ekranı, WhatsApp, Instagram, Drive…). Sonra ekranı kontrol etmek için screenshot al.",
            mapOf("id" to i("Galeri kimliği"), "uygulama" to s("youtube, whatsapp, instagram, tiktok, telegram, drive veya paket adı; boşsa paylaşım menüsü"),
                "metin" to s("İsteğe bağlı eşlik eden metin")), listOf("id")),
        Spec("dosyalari_listele", "Dahili depolamadaki bir klasörü listeler (örn. 'Download', 'Documents', '/').", mapOf("yol" to s("Klasör yolu")), listOf("yol")),
        Spec("dosya_ara", "Dahili depolamada adında verilen ifade geçen dosyaları bulur.", mapOf("ad" to s("Aranacak ad parçası")), listOf("ad")),
        Spec("dosya_oku", "Metin dosyasını okur (örn. Download/youtube_yukleme.txt).", mapOf("yol" to s("Dosya yolu")), listOf("yol")),
        Spec("dosya_yaz", "Metin dosyası oluşturur/yazar (not, liste, plan…).",
            mapOf("yol" to s("Dosya yolu, örn. Documents/Jarvis/notlar.txt"), "icerik" to s("İçerik"), "ekle" to b("true ise sona ekler")),
            listOf("yol", "icerik")),
        Spec("sohbeti_bitir", "Kullanıcı vedalaştığında, teşekkür edip bitirdiğinde ya da başka isteği kalmadığında sohbet modunu kapatır; Jarvis tekrar 'Hey Jarvis' beklemeye döner."),
    )

}
