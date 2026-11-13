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
| **Ses kilidi (yalnızca senin sesin)** | 5 kez "Hey Jarvis" diyerek sesini tanıtırsın; Jarvis ses izini (CAM++ konuşmacı modeli, tamamen tablette) her uyanışta kontrol eder ve başkasının "Hey Jarvis"ine cevap vermez. Tablet başkasının elindeyken "Hey Jarvis, tableti kapat / ekranı kilitle" dersen hemen yapar. Kilit açıkken ekrandaki "Konuş" düğmesi de çalışmaz. Kullandıkça sesine uyum sağlar. |
| **Ekran görevleri (dijital asistan)** | Claude'un bilgisayar kullanımı araç setiyle tableti bir insan gibi kullanır: ekran görüntüsüne bakar, dokunur, kaydırır, yazar. Örn. "Claude uygulamasını aç, YKS sohbetine gir ve bana türev çalışma planı yazdır", "Galerideki son videoyu YouTube'a yükle". Uzun görevlerde bağlam dolunca kendi notlarıyla yeni oturumda devam eder. |
| **Galeri ve dosyalar** | "Galerimdeki son 3 fotoğrafı sil", "Hepsini sil", "Fotoğraflarımı klasör klasör ayır", "Son silmeyi geri al", "İndirilenlerdeki youtube_yukleme.txt'yi oku". Silinenler 30 gün çöp kutusunda kalır; 10'dan fazla öğe için önce onay ister. |
| **Ders programı** | Programını sesle söyle ya da fotoğrafını **Paylaş → "Jarvis'e gönder"** ile at. Jarvis kaydeder, her dersin başında hatırlatır, bitince "yaptın mı?" diye sorar, uyguladığın/uygulamadığın dersleri takip eder, rapor çıkarır ve ona göre yol gösterir. |
| **Kamera** | "Nasıl görünüyorum?" → ön kamerayla fotoğraf çekip yorumlar. "Elimdekine bak" → arka kamera. |
| **Günaydın** | Sabah alarmını kapattığında (veya sabah tableti ilk açtığında) günaydın der; hava durumunu (Ayarlar'a şehrini yazarsan), TYT'ye kalan günü, bugünkü programını söyler; okul günlerinde "Okula hazır mısın, kahvaltı yaptın mı?" diye sorar ve sohbete devam eder. Alarmı ertelediysen bekler. |
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
   - **Tüm dosyalara erişim** ve **Fotoğraf ve videolar** (galeri ve dosya işleri için).
   - **Ses kilidi: sesini tanıt** (önerilir).
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
- "Bu benim ders programım, kaydet." (fotoğrafını Paylaş → Jarvis'e gönder ile at)
- "Bu hafta programıma ne kadar uydum?"
- "Galerimdeki son 3 fotoğrafı sil." / "Fotoğraflarımı konularına göre klasörlere ayır."
- "Galerideki son videoyu YouTube'a yükle; başlık ve açıklamayı İndirilenler'deki youtube_yukleme.txt'den al."
- "Claude uygulamasına gir, son sohbete şunu yaz: …"
- "Teşekkürler Jarvis, bu kadar."

## Maliyet

Claude API kullandıkça ücretlendirilir. Varsayılan model **Claude Opus 5.5** (en akıllı) — kısa bir sesli
etkileşim yaklaşık 2–5 sent tutar (ekran görevleri daha pahalıdır, yukarıya bak). Daha ucuz seçenekler Ayarlar → Model'de:
**Claude Sonnet 5.5** (yaklaşık yarı fiyat) ve **Claude Haiku 5.5** (çok daha ucuz, en hızlı).
Konuşmalar 15 dakika sessizlikten sonra sıfırlanır; bu, maliyeti ve gecikmeyi düşük tutar.

## Bilmen gerekenler (dürüst sınırlar)

- **Tableti kapatma:** Android, root olmayan uygulamaların doğrudan kapatmasına izin vermez. Jarvis bunu
  Erişilebilirlik ile güç menüsünü açıp "Kapat"a basarak yapar. MagicOS sürümüne göre menü farklıysa
  Jarvis ekranda ne gördüğünü söyler; tablet root'luysa doğrudan kapatır.
- **Ses kilidi** "Hey Jarvis" sözünün ses izine bakar. Banka düzeyinde bir güvenlik değildir: sesi
  çok benzeyen biri (ör. kardeş) bazen geçebilir; uyandıktan sonraki takip cümleleri ayrıca doğrulanmaz.
  Seni tanımazsa Ayarlar'dan kilidin sıkılığını düşür ya da sesini yeniden tanıt. Yalnızca "Hey Jarvis"
  ile uyanır ("Hey Charles" gibi başka bir isim çalışmaz).
- **Ekran görevleri** görünür şekilde yapılır: Android, bir uygulamanın başka uygulamaları gizlice
  (ekran kapalıyken/kilitliyken) kullanmasına izin vermez. Görev sırasında ekran açık ve kilitsiz olmalı;
  her adım birkaç saniye sürer ve uzun görevler (ör. video yükleme) Claude API'de normal sohbetten daha
  pahalıdır (Opus 5.5 ile görev başına kabaca 10-50 sent). İstediğin an "Hey Jarvis, dur" diyebilirsin.
  Herkese açık yayınlama, mesaj gönderme, satın alma gibi adımlardan önce sesli onay ister.
- **YouTube yükleme** YouTube uygulaması üzerinden yapılır. Mobil uygulamada ayrı bir etiket alanı yoksa
  etiketler açıklamanın sonuna #etiket olarak eklenir.
- **Galeri:** Silinenler Android'in çöp kutusuna gider (30 gün); Honor Galeri'nin "Son silinenler"
  bölümünde görünmeyebilir. Geri almak için "Hey Jarvis, son silmeyi geri al" de.
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
- `wake/Fbank.kt`, `wake/SpeakerVerifier.kt`, `wake/VoiceLock.kt` — Kaldi uyumlu fbank + CAM++ konuşmacı
  doğrulama (kaldi-native-fbank ve Python ONNX çıktısıyla birebir doğrulandı).
- `device/ComputerUse.kt` — `computer_toolset_20260801` eylemlerinin Android dokunmatik ekrana uyarlanması.
- `device/MediaTools.kt`, `device/Program.kt` — galeri/dosya işlemleri ve ders programı takibi.
- `brain/ClaudeBrain.kt` — resmi Anthropic Java SDK ile akışlı istek, araç döngüsü, cümle cümle seslendirme.
  Güvenlik sınıflandırıcısı reddederse sunucu tarafı otomatik yedek model (`fallbacks: "default"`) açıktır.
- `brain/Tools.kt` — Claude'a sunulan tüm cihaz araçları; `device/` — uygulamaları.
- `service/JarvisService.kt` — uyandırma → dinleme → düşünme → konuşma döngüsü ve otomatik olaylar.
- `app/jarvis.keystore` sabit imzalama anahtarıdır; güncellemeler eski sürümün üzerine kurulur.
