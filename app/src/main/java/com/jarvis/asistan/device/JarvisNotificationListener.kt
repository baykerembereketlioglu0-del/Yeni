package com.jarvis.asistan.device

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jarvis.asistan.service.JarvisService
import java.time.LocalTime

/**
 * Bildirimleri okur ve çalan alarmın kapatıldığını yakalar (sabah "günaydın" için).
 */
class JarvisNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isRingingAlarm(sbn)) ringingAlarmKey = sbn.key
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == ringingAlarmKey || isRingingAlarm(sbn)) {
            ringingAlarmKey = null
            if (LocalTime.now().hour !in 4..11) return
            val pkg = sbn.packageName
            // Erteleme ihtimaline karşı biraz bekle: alarm ertelendiyse günaydın deme.
            handler.postDelayed({
                if (ringingAlarmKey == null && !isSnoozed(pkg)) {
                    JarvisService.sendEvent(this, JarvisService.EVENT_MORNING, "alarm")
                }
            }, 12_000)
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun isSnoozed(pkg: String): Boolean {
        val list = runCatching { activeNotifications }.getOrNull() ?: return false
        return list.filter { it.packageName == pkg }.any { n ->
            val ex = n.notification.extras
            val txt = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT)
                .joinToString(" ") { ex.getCharSequence(it)?.toString().orEmpty() }.lowercase()
            listOf("ertel", "snooz", "erteleme").any { txt.contains(it) }
        }
    }

    /** Okunabilir bildirim listesi. */
    fun readable(limit: Int = 15): String {
        val list = runCatching { activeNotifications }.getOrNull() ?: return "Bildirimlere erişilemedi."
        val pm = packageManager
        val items = list
            .filter { it.packageName != packageName && !it.isOngoing }
            .sortedByDescending { it.postTime }
            .take(limit)
            .mapNotNull { sbn ->
                val ex = sbn.notification.extras
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
                "• $app — $title: ${text.take(200)}"
            }
        return if (items.isEmpty()) "Okunmamış bildirim yok." else items.joinToString("\n")
    }

    companion object {
        @Volatile var instance: JarvisNotificationListener? = null
            private set
        @Volatile private var ringingAlarmKey: String? = null

        private val clockHints = listOf("deskclock", "clock", "alarm")

        fun isRingingAlarm(sbn: StatusBarNotification): Boolean {
            val n = sbn.notification
            val pkg = sbn.packageName.lowercase()
            val isClockApp = clockHints.any { pkg.contains(it) }
            val isAlarmCategory = n.category == Notification.CATEGORY_ALARM
            // Çalan alarmlar tam ekran niyetiyle gelir; "yaklaşan alarm" bildirimleri gelmez.
            return (isAlarmCategory || isClockApp) && n.fullScreenIntent != null
        }
    }
}
