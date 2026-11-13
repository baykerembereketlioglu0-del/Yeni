package com.jarvis.asistan.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.jarvis.asistan.JarvisApp
import com.jarvis.asistan.JarvisState
import com.jarvis.asistan.Mode
import com.jarvis.asistan.R
import com.jarvis.asistan.brain.Brain
import com.jarvis.asistan.brain.ClaudeBrain
import com.jarvis.asistan.brain.GeminiBrain
import com.jarvis.asistan.brain.Persona
import com.jarvis.asistan.device.DeviceController
import com.jarvis.asistan.ui.MainActivity
import com.jarvis.asistan.voice.Listener
import com.jarvis.asistan.voice.Speaker
import com.jarvis.asistan.wake.VoiceLock
import com.jarvis.asistan.wake.WakeWordListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Jarvis'in kalbi: ön planda çalışır, "Hey Jarvis"i dinler, sohbeti yönetir ve
 * sabah / deneme / akşam / hatırlatıcı olaylarını tetikler.
 */
class JarvisService : LifecycleService() {

    private lateinit var speaker: Speaker
    private lateinit var listener: Listener
    private lateinit var wake: WakeWordListener
    private lateinit var brain: Brain
    private var brainProvider = ""
    private lateinit var device: DeviceController

    private var conversation: Job? = null
    private var replyJob: Job? = null
    /** Bekleyen otomatik olay: metin + isteğe bağlı görüntü (base64 JPEG). */
    data class Ev(val text: String, val image: String? = null)
    private val pendingEvents = ArrayDeque<Ev>()
    private var ready = false
    /** Ses kaydı ekranı mikrofonu kullanırken uyandırma dinlemesi durur. */
    private var wakePaused = false
    /** Servis mikrofon tipiyle ön planda mı? (değilse arka planda mikrofon sessiz kalır) */
    private var micForeground = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onUnlock()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        goForeground()
        device = DeviceController(this)
        speaker = Speaker(this)
        listener = Listener(this)
        brain = makeBrain()
        wake = WakeWordListener(this) { audio -> lifecycleScope.launch { onWakeWord(audio) } }

        ContextCompat.registerReceiver(this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_EXPORTED)
        Scheduler.rescheduleAll(this)

        if (VoiceLock.isActive) lifecycleScope.launch(Dispatchers.Default) {
            runCatching { VoiceLock.verifier(this@JarvisService) } // ilk doğrulamada gecikme olmasın
        }
        lifecycleScope.launch {
            speaker.awaitReady()
            ready = true
            idle()
            drainEvents()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Uygulama açıldığında ya da bildirime dokunulduğunda mikrofon yetkisini tazele
        // (ör. yeniden başlatmadan sonra servis mikrofonsuz başlamış olabilir).
        if (!micForeground && hasMic() && intent?.action != ACTION_STOP) {
            goForeground()
            if (micForeground && conversation?.isActive != true) lifecycleScope.launch {
                withContext(Dispatchers.IO) { wake.stop() }
                idle()
            }
        }
        when (intent?.action) {
            ACTION_TALK -> lifecycleScope.launch {
                if (VoiceLock.isActive && replyJob?.isActive != true && conversation?.isActive != true) {
                    // Ses kilidi açıkken düğmeyle başlatılamaz: sahibin sesi doğrulanmalı.
                    JarvisState.setPartial("Ses kilidi açık: lütfen \"Hey Jarvis\" deyin")
                    delay(3000)
                    JarvisState.setPartial("")
                } else onWakeWord(null)
            }
            ACTION_EVENT -> handleEvent(intent.getStringExtra(EXTRA_TYPE).orEmpty(), intent.getStringExtra(EXTRA_PAYLOAD).orEmpty())
            ACTION_SHARE -> {
                val img = pendingShareImage
                pendingShareImage = null
                handleShare(intent.getStringExtra(EXTRA_PAYLOAD).orEmpty(), img)
            }
            ACTION_PAUSE_WAKE -> lifecycleScope.launch {
                wakePaused = true
                withContext(Dispatchers.IO) { wake.stop() }
                JarvisState.setMode(Mode.OFF)
            }
            ACTION_RESUME_WAKE -> lifecycleScope.launch {
                wakePaused = false
                if (conversation?.isActive != true) idle()
            }
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> lifecycleScope.launch {
                speaker.applySettings()
                if (brainProvider != JarvisApp.prefs.provider && conversation?.isActive != true) brain = makeBrain()
                brain.reset()
                if (conversation?.isActive != true) idle()
            }
        }
        return START_STICKY
    }

    private fun makeBrain(): Brain {
        brainProvider = JarvisApp.prefs.provider
        return if (brainProvider == "claude") ClaudeBrain(device, speaker) else GeminiBrain(device, speaker)
    }

    private fun goForeground() {
        val n = buildNotification()
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        try {
            val type = if (Build.VERSION.SDK_INT >= 30 && micGranted) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, NOTIF_ID, n, type)
            micForeground = Build.VERSION.SDK_INT < 30 || type == ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } catch (e: Exception) {
            micForeground = false
            Log.w(TAG, "Mikrofon tipiyle ön plana geçilemedi, özel kullanım tipi deneniyor", e)
            runCatching {
                ServiceCompat.startForeground(this, NOTIF_ID, n,
                    if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            }
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val talk = PendingIntent.getService(this, 1, Intent(this, JarvisService::class.java).setAction(ACTION_TALK), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 2, Intent(this, JarvisService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, JarvisApp.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_jarvis)
            .setContentTitle("J.A.R.V.I.S. çevrimiçi")
            .setContentText("\"Hey Jarvis\" deyin ya da Konuş'a dokunun")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Konuş", talk)
            .addAction(0, "Durdur", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Bekleme moduna dön: uyandırma kelimesini dinle. */
    private suspend fun idle() {
        if (JarvisApp.prefs.wakeEnabled && hasMic() && !wakePaused) {
            withContext(Dispatchers.IO) { wake.start() }
            JarvisState.setMode(Mode.IDLE)
        } else {
            JarvisState.setMode(Mode.OFF)
        }
    }

    /** "Hey Jarvis" duyuldu ([audio] ile) ya da Konuş'a basıldı ([audio] = null). */
    private suspend fun onWakeWord(audio: ShortArray?) {
        if (audio != null && VoiceLock.isActive) {
            val r = withContext(Dispatchers.Default) { VoiceLock.check(this@JarvisService, audio) }
            if (!r.accepted) {
                // Sahibin sesi değil: tepki verme, dinlemeye devam et.
                JarvisState.setPartial("Ses tanınmadı (benzerlik %${(r.score * 100).toInt()})")
                lifecycleScope.launch { delay(2500); JarvisState.setPartial("") }
                if (replyJob?.isActive == true || conversation?.isActive == true) {
                    if (replyJob?.isActive == true) withContext(Dispatchers.IO) { wake.start() }
                } else idle()
                return
            }
        }
        val reply = replyJob
        if (reply?.isActive == true) {
            // Jarvis konuşurken sözü kesildi: sus ve dinle.
            speaker.stop()
            reply.cancel()
            return
        }
        if (conversation?.isActive == true) return
        startConversation(null)
    }

    private fun startConversation(event: Ev?) {
        if (!ready || conversation?.isActive == true) {
            if (event != null) pendingEvents.addLast(event)
            return
        }
        conversation = lifecycleScope.launch {
            try {
                runConversation(event)
            } finally {
                replyJob = null
                // Sohbet bitti (veya iptal edildi): yeni bir eşyordamda beklemeye dön.
                lifecycleScope.launch {
                    idle()
                    drainEvents()
                }
            }
        }
    }

    private fun drainEvents() {
        if (conversation?.isActive == true) return
        pendingEvents.removeFirstOrNull()?.let { startConversation(it) }
    }

    private suspend fun runConversation(firstEvent: Ev?) {
        withContext(Dispatchers.IO) { wake.stop() }
        var event = firstEvent
        var first = true
        while (lifecycleScope.isActive) {
            val text: String
            var image: String? = null
            if (event != null) {
                text = event.text
                image = event.image
                event = null
            } else {
                if (!hasMic()) {
                    speaker.say("Sizi duyabilmem için mikrofon izni gerekiyor ${JarvisApp.prefs.hitap}.")
                    return
                }
                if (first) beep()
                JarvisState.setMode(Mode.LISTENING)
                when (val r = listener.listen()) {
                    is Listener.Result.Text -> {
                        text = r.text
                        JarvisState.add(true, text)
                    }
                    is Listener.Result.Silence -> return
                    is Listener.Result.Error -> {
                        JarvisState.setMode(Mode.ERROR)
                        JarvisState.add(false, r.message)
                        speaker.say(r.message)
                        return
                    }
                }
                if (isStopPhrase(text)) {
                    speaker.stop()
                    return
                }
            }
            first = false

            // Cevap verirken "Hey Jarvis" ile sözünü kesebilmek için uyandırmayı dinle.
            JarvisState.setMode(Mode.THINKING)
            val job = lifecycleScope.async { brain.respond(text, image) }
            replyJob = job
            withContext(Dispatchers.IO) { wake.start() }
            try {
                job.await()
                speaker.awaitIdle()
            } catch (e: CancellationException) {
                if (!lifecycleScope.isActive) throw e
                // Söz kesildi -> hemen dinlemeye geç.
                Log.i(TAG, "Söz kesildi")
                first = true
            } finally {
                replyJob = null
                withContext(Dispatchers.IO) { wake.stop() }
            }
            if (brain.endRequested) return
        }
    }

    private fun isStopPhrase(t: String): Boolean {
        val n = t.lowercase().trim().trimEnd('.', '!')
        return n in setOf("sus", "dur", "iptal", "vazgeç", "vazgeçtim", "boş ver", "boşver", "sessiz ol", "kes")
    }

    private suspend fun beep() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
            delay(250)
            tg.release()
        }
    }

    // ---------------- Otomatik olaylar ----------------

    private fun onUnlock() {
        val p = JarvisApp.prefs
        val now = LocalDateTime.now()
        val today = LocalDate.now().toString()
        if (p.morningEnabled && p.morningOnUnlock && now.hour in 5..11 && p.lastGreet != today) {
            handleEvent(EVENT_MORNING, "unlock")
            return
        }
        val end = runCatching { LocalTime.parse(p.denemeEnd) }.getOrDefault(LocalTime.of(13, 0))
        if (p.denemeAskEnabled && now.dayOfWeek.value == p.denemeDay &&
            now.toLocalTime().isAfter(end) && now.hour < 23 && p.lastDenemeAsk != today
        ) {
            handleEvent(EVENT_DENEME, "")
        }
    }

    fun handleEvent(type: String, payload: String) {
        val p = JarvisApp.prefs
        val today = LocalDate.now().toString()
        val text = when (type) {
            EVENT_MORNING -> {
                if (!p.morningEnabled || p.lastGreet == today) return
                p.lastGreet = today
                Persona.morningEvent(p, payload)
            }
            EVENT_DENEME -> {
                if (p.lastDenemeAsk == today) return
                p.lastDenemeAsk = today
                Persona.denemeEvent(p)
            }
            EVENT_EVENING -> {
                if (!p.eveningEnabled) return
                Persona.eveningEvent(p)
            }
            EVENT_REMINDER -> Persona.reminderEvent(payload)
            EVENT_PROGRAM_START -> {
                if (!p.programRemindStart) return
                Persona.programStartEvent(payload)
            }
            EVENT_PROGRAM_END -> {
                if (!p.programAskEnd) return
                Persona.programEndEvent(payload)
            }
            else -> return
        }
        Log.i(TAG, "Olay: $type")
        startConversation(Ev(text))
    }

    /** Başka bir uygulamadan Jarvis'e paylaşılan metin/görüntü. */
    private fun handleShare(text: String, image: String?) {
        startConversation(Ev(Persona.shareEvent(text, image != null), image))
    }

    private fun shutdown() {
        conversation?.cancel()
        wake.stop()
        speaker.stop()
        JarvisState.setMode(Mode.OFF)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlockReceiver) }
        wake.release()
        speaker.shutdown()
        if (instance === this) instance = null
        JarvisState.setMode(Mode.OFF)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "JarvisService"
        private const val NOTIF_ID = 42
        const val ACTION_TALK = "com.jarvis.asistan.TALK"
        const val ACTION_EVENT = "com.jarvis.asistan.EVENT"
        const val ACTION_STOP = "com.jarvis.asistan.STOP"
        const val ACTION_REFRESH = "com.jarvis.asistan.REFRESH"
        const val ACTION_SHARE = "com.jarvis.asistan.SHARE"
        const val ACTION_PAUSE_WAKE = "com.jarvis.asistan.PAUSE_WAKE"
        const val ACTION_RESUME_WAKE = "com.jarvis.asistan.RESUME_WAKE"
        const val EXTRA_TYPE = "type"
        const val EXTRA_PAYLOAD = "payload"
        const val EVENT_MORNING = "morning"
        const val EVENT_DENEME = "deneme"
        const val EVENT_EVENING = "evening"
        const val EVENT_REMINDER = "reminder"
        const val EVENT_PROGRAM_START = "program_start"
        const val EVENT_PROGRAM_END = "program_end"

        /** Paylaşılan görüntü (Intent ile taşınamayacak kadar büyük olabilir). */
        @Volatile var pendingShareImage: String? = null

        fun share(ctx: Context, text: String, imageBase64: String?) {
            pendingShareImage = imageBase64
            runCatching {
                ContextCompat.startForegroundService(ctx,
                    Intent(ctx, JarvisService::class.java).setAction(ACTION_SHARE).putExtra(EXTRA_PAYLOAD, text))
            }.onFailure { Log.w(TAG, "Paylaşım iletilemedi", it) }
        }

        @Volatile var instance: JarvisService? = null
            private set

        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, JarvisService::class.java)) }
                .onFailure { Log.w(TAG, "Servis başlatılamadı", it) }
        }

        fun command(ctx: Context, action: String) {
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, JarvisService::class.java).setAction(action))
            }.onFailure { Log.w(TAG, "Komut gönderilemedi: $action", it) }
        }

        /** Başka bileşenlerden (alarm, bildirim dinleyici) olay gönderir. */
        fun sendEvent(ctx: Context, type: String, payload: String) {
            val svc = instance
            if (svc != null) {
                svc.lifecycleScope.launch { svc.handleEvent(type, payload) }
                return
            }
            runCatching {
                ContextCompat.startForegroundService(
                    ctx,
                    Intent(ctx, JarvisService::class.java).setAction(ACTION_EVENT)
                        .putExtra(EXTRA_TYPE, type).putExtra(EXTRA_PAYLOAD, payload)
                )
            }.onFailure { Log.w(TAG, "Olay için servis başlatılamadı", it) }
        }
    }
}
