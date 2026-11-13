# J.A.R.V.I.S. — Honor Android tablet için kişisel sesli asistan

"Hey Jarvis" dediğinde uyanır, Claude ile düşünür, Türkçe sesli konuşur ve tabletin
sistemini kontrol eder. YKS'ye hazırlanan bir öğrenci için koçluk özellikleri vardır.

## Neler yapabilir?

| Özellik | Nasıl çalışır |
|---|---|
| **"Hey Jarvis" ile uyanma** | Ses tamamen tablette, çevrimdışı işlenir ([openWakeWord](https://github.com/dscripka/openWakeWord) "hey jarvis" modeli). Jarvis konuşurken tekrar "Hey Jarvis" diyerek sözünü kesebilirsin. |
| **Sohbet** | Sözlerin Google konuşma tanıma ile yazıya dökülür, Claude (varsayılan: Claude Opus 5.5) cevaplar, Jarvis cümle cümle seslendirir. Cevaptan sonra "Hey Jarvis" demeden konuşmaya devam edebilirsin. "Teşekkürler, bu kadar" deyince bekleme moduna döner. |
| **Tableti kapatma / yeniden başlatma / kilitleme** | "Tableti kapat" → "Tableti kapatıyorum efendim" der ve güç menüsünden kapatır. |
| **Sistem kontrolü** | Ses, parlaklık, fener, Wi-Fi, Bluetooth, uçak modu, Rahatsız Etmeyin, uygulama açma, müzik/YouTube, medya kontrolü, alarm, zamanlayıcı, hatırlatıcı, bildirimleri okuma, cihaz durumu (pil, depolama…). |
| **Ekranı kullanma** | Ekrandakileri okuyabilir, ekran görüntüsüne bakabilir, öğelere dokunabilir, yazı yazabilir, kaydırabilir (örn. "YouTube'u aç ve Barış Manço ara"). |
| **Kamera** | "Nasıl görünüyorum?" → ön kamerayla fotoğraf çekip yorumlar. "Elimdekine bak" → arka kamera. |
| **Günaydın** | Sabah alarmını kapattığında (veya sabah tableti ilk açtığında) günaydın der, tarihi, hava durumunu, TYT'ye kalan günü ve günün motivasyonunu söyler. Alarmı ertelediysen bekler. |
| **Deneme dönüşü** | Deneme gününde (varsayılan Cumartesi) belirlediğin saatten sonra tableti ilk açtığında "Hoş geldiniz efendim, deneme nasıl geçti?" diye sorar, netlerini kaydeder ve önceki denemelerle karşılaştırır. |
| **YKS koçluğu** | Akşam motivasyonu, pomodoro/mola hatırlatıcıları, konu anlatımı, net hesaplama, deneme geçmişi takibi. |
| **Hafıza** | Hedef bölümün, zayıf derslerin gibi önemli bilgileri kalıcı olarak hatırlar. |
| **Güncel bilgi** | Hava durumu, haberler, ÖSYM duyuruları için web araması yapar. |

## Kurulum

### 1. Claude API anahtarı al
1. <https://console.anthropic.com> adresinde hesap aç.
2. **Billing** bölümünden bakiye yükle (Claude.ai aboneliğinden ayrıdır; kullandıkça ödenir).
3. **Settings → Limits** bölümünden aylık harcama limiti koymanı öneririm.
4. **API Keys → Create Key** ile anahtar oluştur ve kopyala (`sk-ant-...`).

### 2. APK'yı kur
1. `Jarvis.apk` dosyasını tablete indir (GitHub'da bu deponun **Releases → "Jarvis — son sürüm"** bölümünde de bulunur; her güncellemede otomatik yenilenir).
2. Dosyaya dokun. "Bilinmeyen uygulamalar" uyarısı çıkarsa tarayıcıya/dosya yöneticisine izin ver.

### 3. İlk açılış
1. Jarvis'i aç → **Kurulum** ekranı açılır. Adımları sırayla yap:
   - **Claude API anahtarı** → Ayarlar'a yapıştır.
   - **Mikrofon, Kamera, Bildirim** izinleri.
   - **Erişilebilirlik**: listeden "Jarvis"i aç. *Gri/kilitli görünürse:* Ayarlar → Uygulamalar → Jarvis → sağ üstteki **⋮** → **"Kısıtlanmış ayarlara izin ver"**, sonra tekrar dene. (Kapatma, kilitleme, ekrana dokunma, Wi-Fi/Bluetooth bunun sayesinde çalışır.)
   - **Bildirim erişimi** (alarm sonrası günaydın için).
   - **Diğer uygulamaların üzerinde göster** (arka plandayken kamera ve uygulama açmak için).
   - **Pil kısıtlaması olmasın**.
2. **Honor'a özel (çok önemli):** Ayarlar → Pil → **Uygulama başlatma** → Jarvis → **Manuel yönet** → üç seçeneği de aç. Son uygulamalar ekranında Jarvis'i aşağı çekerek kilitle. Aksi hâlde MagicOS Jarvis'i arka planda kapatabilir.
3. **Ayarlar**'dan adını, hitap şeklini ("efendim"), şehrini, YKS alanını, deneme gününü/saatini gir. "Jarvis'in sesi" listesinden erkek sesleri deneyip seç (**Sesi dene**).

## Kullanım örnekleri

- "Hey Jarvis… tableti kapat."
- "Hey Jarvis, nasıl görünüyorum?"
- "Sesi yüzde otuza indir." / "Parlaklığı artır." / "Wi-Fi'yi kapat."
- "Yarın sabah yedi buçuğa alarm kur."
- "Yirmi beş dakikalık pomodoro başlat, bitince mola ver."
- "Bugün TYT denemesinde doksan iki net yaptım: Türkçe otuz bir, matematik yirmi iki…"
- "Son denemelerimle karşılaştır, hangi derse yüklenmeliyim?"
- "Ekranda ne var?" / "YouTube'u aç ve lofi müzik çal."
- "Hedefim Boğaziçi Bilgisayar Mühendisliği, bunu unutma."
- "Teşekkürler Jarvis, bu kadar."

## Maliyet

Claude API kullandıkça ücretlendirilir. Varsayılan model **Claude Opus 5.5** (en akıllı) — kısa bir sesli
etkileşim yaklaşık 1–3 sent tutar. Daha ucuz seçenekler Ayarlar → Model'de:
**Claude Sonnet 5.5** (yaklaşık yarı fiyat) ve **Claude Haiku 5.5** (çok daha ucuz, en hızlı).
Konuşmalar 15 dakika sessizlikten sonra sıfırlanır; bu, maliyeti ve gecikmeyi düşük tutar.

## Bilmen gerekenler (dürüst sınırlar)

- **Tableti kapatma:** Android, root olmayan uygulamaların doğrudan kapatmasına izin vermez. Jarvis bunu
  Erişilebilirlik ile güç menüsünü açıp "Kapat"a basarak yapar. MagicOS sürümüne göre menü farklıysa
  Jarvis ekranda ne gördüğünü söyler; tablet root'luysa doğrudan kapatır.
- **Wi-Fi/Bluetooth:** Android 10+ bunları uygulamaların açıp kapatmasına izin vermez; Jarvis hızlı ayarlar
  panelindeki düğmeye basar.
- **Konuşma tanıma** için Google uygulaması ve internet gerekir.
- **Yeniden başlattıktan sonra** Jarvis kendiliğinden açılır; Android 15'te mikrofon yetkisi için uygulamayı
  bir kez açman veya bildirimdeki "Konuş"a dokunman gerekebilir.
- **Gizlilik:** "Hey Jarvis" algılama tamamen tablette yapılır. Uyandıktan sonra sözlerin Google'a (yazıya
  dökme) ve yazılı hâli ile kamera/ekran görüntüleri Anthropic'e (Claude) gönderilir. API anahtarın yalnızca
  tablette saklanır.

## Geliştirici notları

- Kotlin, Android 9+ (minSdk 28), hedef Android 15. Derleme: `./gradlew :app:assembleRelease`
- `wake/OpenWakeWord.kt` — openWakeWord akış hattının Kotlin karşılığı; Python referans uygulamasıyla
  aynı skorları ürettiği doğrulandı.
- `brain/ClaudeBrain.kt` — resmi Anthropic Java SDK ile akışlı istek, araç döngüsü, cümle cümle seslendirme.
  Güvenlik sınıflandırıcısı reddederse sunucu tarafı otomatik yedek model (`fallbacks: "default"`) açıktır.
- `brain/Tools.kt` — Claude'a sunulan tüm cihaz araçları; `device/` — uygulamaları.
- `service/JarvisService.kt` — uyandırma → dinleme → düşünme → konuşma döngüsü ve otomatik olaylar.
- `app/jarvis.keystore` sabit imzalama anahtarıdır; güncellemeler eski sürümün üzerine kurulur.
