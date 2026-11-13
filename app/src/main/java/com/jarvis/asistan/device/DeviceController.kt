package com.jarvis.asistan.device

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.view.KeyEvent
import android.accessibilityservice.AccessibilityService
import com.jarvis.asistan.JarvisApp
import com.jarvis.asistan.service.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Calendar
import java.util.Locale

/** Bir aracın sonucu: metin ve isteğe bağlı JPEG görüntü (base64). */
data class ToolOutput(val text: String, val imageBase64: String? = null, val isError: Boolean = false)

/** Tabletin donanım ve sistem özelliklerine erişim. */
class DeviceController(private val ctx: Context) {
    val context: Context get() = ctx


    private val tr = Locale("tr", "TR")
    private val audio get() = ctx.getSystemService(AudioManager::class.java)
    private val a11y get() = JarvisAccessibilityService.instance

    private fun needA11y(): ToolOutput = ToolOutput(
        "Erişilebilirlik izni kapalı. Kullanıcı Jarvis uygulamasında Kurulum > Erişilebilirlik adımını tamamlamalı.",
        isError = true
    )

    private fun norm(s: String) = s.lowercase(tr).replace('ı', 'i').replace("ğ", "g").replace("ü", "u")
        .replace("ş", "s").replace("ö", "o").replace("ç", "c").replace(Regex("[^a-z0-9 ]"), "").trim()

    val media = MediaTools(ctx)
    val computer = ComputerUse(ctx)

    fun launchIntent(intent: Intent): Boolean = launch(intent)

    private fun launch(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        (a11y ?: ctx).startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    // ---------------- Güç ----------------

    private suspend fun su(cmd: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val ok = withTimeoutOrNull(4000) {
                while (p.isAlive) delay(100)
                p.exitValue() == 0
            }
            if (ok == null) p.destroy()
            ok == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun powerOff(): ToolOutput {
        if (su("reboot -p")) return ToolOutput("Tablet kapatılıyor (root).")
        val s = a11y ?: return needA11y()
        return ToolOutput(s.powerMenu(restart = false))
    }

    suspend fun reboot(): ToolOutput {
        if (su("reboot")) return ToolOutput("Tablet yeniden başlatılıyor (root).")
        val s = a11y ?: return needA11y()
        return ToolOutput(s.powerMenu(restart = true))
    }

    fun lockScreen(): ToolOutput {
        val s = a11y ?: return needA11y()
        return if (s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN))
            ToolOutput("Ekran kilitlendi.") else ToolOutput("Ekran kilitlenemedi.", isError = true)
    }

    fun systemKey(key: String): ToolOutput {
        val s = a11y ?: return needA11y()
        val action = when (key) {
            "geri" -> AccessibilityService.GLOBAL_ACTION_BACK
            "ana_ekran" -> AccessibilityService.GLOBAL_ACTION_HOME
            "son_uygulamalar" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "bildirimler" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "hizli_ayarlar" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "ekran_goruntusu" -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            "guc_menusu" -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            "bolunmus_ekran" -> AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
            else -> return ToolOutput("Bilinmeyen tuş: $key", isError = true)
        }
        return if (s.performGlobalAction(action)) ToolOutput("Tamam: $key") else ToolOutput("Yapılamadı: $key", isError = true)
    }

    // ---------------- Ses / ekran ----------------

    fun volume(op: String, level: Int?, type: String): ToolOutput {
        val stream = when (type) {
            "zil" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "bildirim" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        val am = audio
        val max = am.getStreamMaxVolume(stream)
        try {
            when (op) {
                "artir" -> am.setStreamVolume(stream, minOf(max, am.getStreamVolume(stream) + maxOf(1, max / 7)), 0)
                "azalt" -> am.setStreamVolume(stream, maxOf(0, am.getStreamVolume(stream) - maxOf(1, max / 7)), 0)
                "sessiz" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                "sesi_ac" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                else -> {
                    val pct = (level ?: 50).coerceIn(0, 100)
                    am.setStreamVolume(stream, Math.round(pct / 100f * max), 0)
                }
            }
        } catch (e: SecurityException) {
            return ToolOutput("Rahatsız Etmeyin modu açıkken ses değiştirilemiyor.", isError = true)
        }
        val now = Math.round(am.getStreamVolume(stream) * 100f / max)
        return ToolOutput("${type.ifEmpty { "medya" }} sesi şimdi %$now.")
    }

    fun brightness(level: Int?, auto: Boolean?): ToolOutput {
        if (!Settings.System.canWrite(ctx)) {
            return ToolOutput("Parlaklık için \"Sistem ayarlarını değiştirme\" izni gerekli (Kurulum ekranından verilebilir).", isError = true)
        }
        val cr = ctx.contentResolver
        if (auto == true) {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            return ToolOutput("Otomatik parlaklık açıldı.")
        }
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val pct = (level ?: 50).coerceIn(1, 100)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, Math.round(pct / 100f * 255))
        return ToolOutput("Parlaklık %$pct yapıldı.")
    }

    fun flashlight(on: Boolean): ToolOutput {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ToolOutput("Bu tablette fener (flaş) yok.", isError = true)
        return try {
            cm.setTorchMode(id, on)
            ToolOutput(if (on) "Fener açıldı." else "Fener kapatıldı.")
        } catch (e: Exception) {
            ToolOutput("Fener kontrol edilemedi: ${e.message}", isError = true)
        }
    }

    fun doNotDisturb(on: Boolean): ToolOutput {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            return ToolOutput("Rahatsız Etmeyin için izin yok; hizli_ayar aracıyla \"Rahatsız etmeyin\" düğmesini deneyebilirsin.", isError = true)
        }
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        return ToolOutput(if (on) "Rahatsız Etmeyin açıldı." else "Rahatsız Etmeyin kapatıldı.")
    }

    /** Hızlı ayarlar panelindeki bir düğmeye (Wi-Fi/WLAN, Bluetooth, uçak modu…) basar. */
    suspend fun quickTile(name: String): ToolOutput {
        val s = a11y ?: return needA11y()
        s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        delay(900)
        val aliases = when (norm(name)) {
            "wifi", "wi fi", "wlan", "kablosuz" -> listOf("WLAN", "Wi-Fi", "Wi‑Fi", "Wifi", "İnternet", "Internet")
            "bluetooth" -> listOf("Bluetooth")
            "ucak modu", "ucak" -> listOf("Uçak modu", "Airplane mode", "Uçak")
            "rahatsiz etmeyin" -> listOf("Rahatsız etmeyin", "Rahatsız Etmeyin", "Do not disturb")
            "konum", "gps" -> listOf("Konum", "Location")
            "otomatik dondurme", "dondurme", "ekran dondurme" -> listOf("Otomatik döndür", "Otomatik döndürme", "Auto-rotate", "Dikey", "Portrait")
            "mobil veri" -> listOf("Mobil veri", "Mobile data")
            "hotspot", "erisim noktasi" -> listOf("Kişisel erişim noktası", "Hotspot", "Erişim noktası")
            "karanlik mod", "koyu mod" -> listOf("Karanlık mod", "Koyu mod", "Dark mode")
            "goz koruma", "goz konforu" -> listOf("Göz konforu", "Göz koruma", "Eye comfort")
            else -> listOf(name)
        }
        for (pass in 0 until 2) {
            for (a in aliases) {
                if (s.findNodes(a).isNotEmpty() && s.clickText(a)) {
                    delay(600)
                    s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    return ToolOutput("\"$a\" düğmesine basıldı (durumu değişti).")
                }
            }
            // Panel tam açılmamış olabilir: aşağı kaydırıp tekrar dene.
            val dm = s.resources.displayMetrics
            s.swipe(dm.widthPixels / 2f, dm.heightPixels * 0.15f, dm.widthPixels / 2f, dm.heightPixels * 0.7f)
            delay(700)
        }
        val visible = s.screenText(40)
        s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        return ToolOutput("\"$name\" düğmesi bulunamadı. Panelde görünenler:\n$visible", isError = true)
    }

    // ---------------- Uygulamalar ----------------

    private fun launchables(): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, PackageManager.MATCH_ALL)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase(tr) }
    }

    fun listApps(): ToolOutput = ToolOutput(launchables().joinToString(", ") { it.first })

    fun openApp(name: String): ToolOutput {
        val q = norm(name)
        val apps = launchables()
        val hit = apps.firstOrNull { norm(it.first) == q }
            ?: apps.firstOrNull { norm(it.first).startsWith(q) }
            ?: apps.firstOrNull { norm(it.first).contains(q) }
            ?: apps.firstOrNull { it.second.lowercase().contains(q.replace(" ", "")) }
            ?: return ToolOutput("\"$name\" adında uygulama bulunamadı. Yüklü uygulamalar: " +
                apps.joinToString(", ") { it.first }, isError = true)
        val intent = ctx.packageManager.getLaunchIntentForPackage(hit.second)
            ?: return ToolOutput("${hit.first} açılamıyor.", isError = true)
        return if (launch(intent)) ToolOutput("${hit.first} açıldı.")
        else ToolOutput("${hit.first} açılamadı. \"Diğer uygulamaların üzerinde göster\" izni gerekebilir.", isError = true)
    }

    fun openUrl(url: String): ToolOutput {
        val u = if (url.startsWith("http")) url else "https://$url"
        return if (launch(Intent(Intent.ACTION_VIEW, Uri.parse(u)))) ToolOutput("Açıldı: $u")
        else ToolOutput("Bağlantı açılamadı.", isError = true)
    }

    fun playMusic(query: String, app: String): ToolOutput {
        if (app == "youtube") return openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        if (app == "spotify") {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(query)))
            if (launch(i)) return ToolOutput("Spotify'da \"$query\" arandı.")
        }
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, query)
        if (i.resolveActivity(ctx.packageManager) != null && launch(i)) return ToolOutput("\"$query\" çalınıyor.")
        return openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(query))
    }

    fun media(op: String): ToolOutput {
        val code = when (op) {
            "oynat" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "duraklat" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "sonraki" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "onceki" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "durdur" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        return ToolOutput("Medya: $op")
    }

    // ---------------- Saat / alarm / hatırlatıcı ----------------

    fun setAlarm(hour: Int, minute: Int, label: String?, days: List<Int>): ToolOutput {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (!label.isNullOrBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        if (days.isNotEmpty()) {
            // 1=Pazartesi..7=Pazar  ->  Calendar (1=Pazar..7=Cumartesi)
            val cal = ArrayList(days.map { if (it == 7) Calendar.SUNDAY else it + 1 })
            i.putExtra(AlarmClock.EXTRA_DAYS, cal)
        }
        return if (launch(i)) ToolOutput("Alarm %02d:%02d için kuruldu.".format(hour, minute))
        else ToolOutput("Saat uygulaması alarm kurmayı kabul etmedi.", isError = true)
    }

    fun setTimer(seconds: Int, label: String?): ToolOutput {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (!label.isNullOrBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return if (launch(i)) ToolOutput("$seconds saniyelik zamanlayıcı başladı.")
        else ToolOutput("Zamanlayıcı kurulamadı.", isError = true)
    }

    fun alarms(show: Boolean): ToolOutput {
        val next = ctx.getSystemService(AlarmManager::class.java).nextAlarmClock
        val txt = if (next == null) "Kurulu bir sonraki alarm yok." else {
            val t = java.time.Instant.ofEpochMilli(next.triggerTime).atZone(java.time.ZoneId.systemDefault())
            "Bir sonraki alarm: " + t.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM EEEE HH:mm", tr))
        }
        if (show) launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        return ToolOutput(txt)
    }

    fun reminder(minutesLater: Int?, date: String?, time: String?, message: String): ToolOutput {
        val at: LocalDateTime = when {
            minutesLater != null -> LocalDateTime.now().plusMinutes(minutesLater.toLong())
            time != null -> {
                val t = runCatching { LocalTime.parse(time) }.getOrNull()
                    ?: return ToolOutput("Saat biçimi HH:mm olmalı.", isError = true)
                val d = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                var dt = LocalDateTime.of(d, t)
                if (date == null && dt.isBefore(LocalDateTime.now())) dt = dt.plusDays(1)
                dt
            }
            else -> return ToolOutput("dakika_sonra veya saat verilmeli.", isError = true)
        }
        return ToolOutput(Scheduler.addReminder(ctx, at, message))
    }

    fun reminders(): ToolOutput = ToolOutput(Scheduler.listReminders())
    fun cancelReminder(id: Int): ToolOutput = ToolOutput(Scheduler.cancelReminder(ctx, id))

    // ---------------- Durum ----------------

    fun status(): ToolOutput {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = stat.availableBytes / 1e9
        val totalGb = stat.totalBytes / 1e9
        val mi = ActivityManager.MemoryInfo().also { ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val net = when {
            caps == null -> "bağlı değil"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobil veri"
            else -> "bağlı"
        }
        val am = audio
        val vol = Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        val br = runCatching { Math.round(Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100f / 255) }.getOrNull()
        return ToolOutput(
            """
            Cihaz: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}
            Pil: %$pct ${if (charging) "(şarj oluyor)" else ""}
            Depolama: ${"%.1f".format(freeGb)} GB boş / ${"%.1f".format(totalGb)} GB
            RAM: ${"%.1f".format(mi.availMem / 1e9)} GB boş / ${"%.1f".format(mi.totalMem / 1e9)} GB
            İnternet: $net
            Medya sesi: %$vol${if (br != null) ", parlaklık: %$br" else ""}
            """.trimIndent()
        )
    }

    fun batteryPercent(): Int = runCatching {
        ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrDefault(-1)

    // ---------------- Görme ----------------

    suspend fun camera(back: Boolean): ToolOutput {
        val r = CameraActivity.capture(ctx, back)
        val bmp = r.bitmap ?: return ToolOutput(r.error ?: "Fotoğraf çekilemedi.", isError = true)
        return ToolOutput("${if (back) "Arka" else "Ön"} kameradan çekilen fotoğraf:", toJpegBase64(bmp))
    }

    suspend fun screenshot(): ToolOutput {
        val s = a11y ?: return needA11y()
        if (Build.VERSION.SDK_INT < 30) return ToolOutput("Ekran görüntüsü Android 11+ gerektirir. Ekrandaki yazılar:\n" + s.screenText())
        val bmp = s.screenshot() ?: return ToolOutput("Ekran görüntüsü alınamadı. Ekrandaki yazılar:\n" + s.screenText(), isError = true)
        return ToolOutput("Ekran görüntüsü:", toJpegBase64(bmp))
    }

    fun readScreen(): ToolOutput = a11y?.let { ToolOutput(it.screenText()) } ?: needA11y()

    suspend fun click(text: String): ToolOutput {
        val s = a11y ?: return needA11y()
        val ok = s.clickText(text)
        delay(700)
        return if (ok) ToolOutput("\"$text\" öğesine dokunuldu. Yeni ekran:\n" + s.screenText(60))
        else ToolOutput("\"$text\" ekranda bulunamadı. Ekrandakiler:\n" + s.screenText(60), isError = true)
    }

    fun type(text: String): ToolOutput {
        val s = a11y ?: return needA11y()
        return if (s.typeText(text)) ToolOutput("Yazıldı: $text") else ToolOutput("Yazı alanı bulunamadı.", isError = true)
    }

    suspend fun scroll(dir: String): ToolOutput {
        val s = a11y ?: return needA11y()
        val ok = s.scroll(dir)
        delay(500)
        return if (ok) ToolOutput("Kaydırıldı. Ekrandakiler:\n" + s.screenText(60)) else ToolOutput("Kaydırılamadı.", isError = true)
    }

    fun notifications(): ToolOutput = JarvisNotificationListener.instance?.let { ToolOutput(it.readable()) }
        ?: ToolOutput("Bildirim erişimi kapalı (Kurulum > Bildirim erişimi).", isError = true)

    // ---------------- Hafıza ve YKS ----------------

    fun remember(fact: String): ToolOutput {
        JarvisApp.prefs.addMemory(fact.trim())
        return ToolOutput("Hafızaya kaydedildi.")
    }

    fun forget(index: Int): ToolOutput {
        val list = JarvisApp.prefs.memories.toMutableList()
        if (index !in 1..list.size) return ToolOutput("Bu numarada bilgi yok.", isError = true)
        val removed = list.removeAt(index - 1)
        JarvisApp.prefs.memories = list
        return ToolOutput("Silindi: $removed")
    }

    fun saveDeneme(input: JSONObject): ToolOutput {
        val obj = JSONObject()
        obj.put("tarih", input.optString("tarih").ifBlank { LocalDate.now().toString() })
        obj.put("tur", input.optString("tur", "TYT"))
        if (input.has("yayin")) obj.put("yayin", input.optString("yayin"))
        if (input.has("toplam_net")) obj.put("toplam_net", input.optDouble("toplam_net"))
        input.optJSONArray("dersler")?.let { obj.put("dersler", it) }
        if (input.has("notlar")) obj.put("notlar", input.optString("notlar"))
        JarvisApp.prefs.addDeneme(obj)
        return ToolOutput("Deneme kaydedildi. " + denemeHistory(obj.getString("tur")).text)
    }

    fun denemeHistory(type: String?): ToolOutput {
        val arr = JarvisApp.prefs.denemeler
        val list = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { type.isNullOrBlank() || it.optString("tur").equals(type, ignoreCase = true) }
            .sortedBy { it.optString("tarih") }
        if (list.isEmpty()) return ToolOutput("Henüz kayıtlı deneme yok.")
        val lines = list.takeLast(15).joinToString("\n") { d ->
            val ders = d.optJSONArray("dersler")?.let { a ->
                (0 until a.length()).joinToString(", ") { i ->
                    val o = a.getJSONObject(i)
                    "${o.optString("ders")} ${o.optDouble("net").let { if (it.isNaN()) "?" else "%.2f".format(it) }}"
                }
            } ?: ""
            val net = d.optDouble("toplam_net").let { if (it.isNaN()) "?" else "%.2f".format(it) }
            "${d.optString("tarih")} ${d.optString("tur")} ${d.optString("yayin")}: toplam $net net. $ders ${d.optString("notlar")}".trim()
        }
        val nets = list.mapNotNull { it.optDouble("toplam_net").takeIf { n -> !n.isNaN() } }
        val trend = if (nets.size >= 2) "\nSon değişim: ${"%+.2f".format(nets.last() - nets[nets.size - 2])} net. En yüksek: ${"%.2f".format(nets.max())}." else ""
        return ToolOutput("Deneme geçmişi (${list.size} kayıt):\n$lines$trend")
    }

    companion object {
        fun toJpegBase64(src: Bitmap, maxSide: Int = 1280): String {
            val scale = minOf(1f, maxSide.toFloat() / maxOf(src.width, src.height))
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }

        @Suppress("unused")
        fun jsonArrayOfInts(a: JSONArray?): List<Int> = if (a == null) emptyList() else (0 until a.length()).map { a.getInt(it) }
    }
}
