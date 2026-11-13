package com.jarvis.asistan.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jarvis.asistan.JarvisApp
import com.jarvis.asistan.device.JarvisAccessibilityService

/** Jarvis'in tam yetkiyle çalışması için gereken izinler ve kontrolleri. */
object Setup {

    class Step(
        val title: String,
        val why: String,
        val isDone: (Context) -> Boolean,
        val fix: (Activity) -> Unit,
        val essential: Boolean = true,
    )

    private fun granted(ctx: Context, perm: String) =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    private fun appDetails(a: Activity) =
        a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))

    private fun safeStart(a: Activity, i: Intent) {
        try { a.startActivity(i) } catch (e: Exception) { appDetails(a) }
    }

    fun accessibilityOn(ctx: Context): Boolean {
        if (JarvisAccessibilityService.instance != null) return true
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, JarvisAccessibilityService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    val steps: List<Step> = listOf(
        Step("Claude API anahtarı", "Jarvis'in beyni Claude'dur; anahtar olmadan düşünemez.",
            { JarvisApp.prefs.apiKey.isNotBlank() },
            { it.startActivity(Intent(it, SettingsActivity::class.java)) }),
        Step("Mikrofon", "\"Hey Jarvis\" demeni duyabilmesi için.",
            { granted(it, Manifest.permission.RECORD_AUDIO) },
            { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.RECORD_AUDIO), 1) }),
        Step("Kamera", "\"Nasıl görünüyorum?\" dediğinde sana bakabilmesi için.",
            { granted(it, Manifest.permission.CAMERA) },
            { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.CAMERA), 2) }),
        Step("Bildirim izni", "Arka planda çalıştığını gösteren bildirim için.",
            { Build.VERSION.SDK_INT < 33 || granted(it, Manifest.permission.POST_NOTIFICATIONS) },
            { if (Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3) }),
        Step("Erişilebilirlik (tam kontrol)",
            "Tableti kapatma, kilitleme, ekrana dokunma, ekranı görme ve Wi-Fi/Bluetooth düğmeleri için. " +
                "Listede \"Jarvis\"i açın. Gri/kilitliyse: Uygulama bilgisi > sağ üstteki ⋮ > \"Kısıtlanmış ayarlara izin ver\" deyip tekrar deneyin.",
            { accessibilityOn(it) },
            { safeStart(it, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }),
        Step("Bildirim erişimi", "Alarmını kapattığında günaydın diyebilmesi ve bildirimlerini okuyabilmesi için. (Kilitliyse yukarıdaki \"kısıtlanmış ayarlar\" adımını uygulayın.)",
            { NotificationManagerCompat.getEnabledListenerPackages(it).contains(it.packageName) },
            { safeStart(it, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }),
        Step("Diğer uygulamaların üzerinde göster", "Arka plandayken kamerayı ve uygulamaları açabilmesi için.",
            { Settings.canDrawOverlays(it) },
            { safeStart(it, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${it.packageName}"))) }),
        Step("Pil kısıtlaması olmasın",
            "Honor tabletlerde Jarvis'in arka planda kapanmaması için. Ayrıca: Ayarlar > Pil > Uygulama başlatma > Jarvis > \"Manuel yönet\" ve üç seçeneği de açın.",
            { it.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(it.packageName) },
            { a ->
                safeStart(a, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${a.packageName}")))
            }),
        Step("Tüm dosyalara erişim (galeri)",
            "Galerideki fotoğraf/videoları listeleyip silebilmesi, klasörlere ayırabilmesi, YouTube'a gönderebilmesi ve dosyalarını okuyabilmesi için. Açılan ekranda Jarvis'e izin ver.",
            { Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager() },
            { safeStart(it, Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${it.packageName}"))) }),
        Step("Fotoğraf ve videolar", "Galerideki fotoğraf ve videolara erişim.",
            { if (Build.VERSION.SDK_INT >= 33) granted(it, Manifest.permission.READ_MEDIA_IMAGES) else granted(it, Manifest.permission.READ_EXTERNAL_STORAGE) },
            {
                val perms = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                ActivityCompat.requestPermissions(it, perms, 4)
            }),
        Step("Ses kilidi: sesini tanıt",
            "Jarvis yalnızca SENİN \"Hey Jarvis\" demene cevap versin. 5 kez \"Hey Jarvis\" diyerek sesini kaydet (tablet başkasının elindeyken bile sesinle kapatabilmen için).",
            { JarvisApp.prefs.voiceProfile != null },
            { it.startActivity(Intent(it, VoiceEnrollActivity::class.java)) },
            essential = false),
        Step("Sistem ayarlarını değiştirme", "Parlaklığı ayarlayabilmesi için.",
            { Settings.System.canWrite(it) },
            { safeStart(it, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${it.packageName}"))) },
            essential = false),
        Step("Rahatsız Etmeyin erişimi", "\"Rahatsız etme modunu aç\" diyebilmen için.",
            { it.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted },
            { safeStart(it, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
            essential = false),
        Step("Konuşma tanıma servisi", "Sözlerini yazıya dökmek için Google uygulaması gerekir. Yoksa Play Store'dan \"Google\" uygulamasını yükleyin.",
            { SpeechRecognizer.isRecognitionAvailable(it) },
            { safeStart(it, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.googlequicksearchbox"))) }),
    )

    fun missing(ctx: Context) = steps.filter { !it.isDone(ctx) }
}
