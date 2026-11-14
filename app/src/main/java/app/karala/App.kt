package app.karala

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import app.karala.data.KaralaDb
import app.karala.data.Prefs
import app.karala.data.Repo
import app.karala.study.FocusTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    lateinit var prefs: Prefs; private set
    lateinit var repo: Repo; private set
    /** Ekranlardan bağımsız, süreç ömrü boyunca yaşayan işler için. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        repo = Repo(this, KaralaDb.build(this), prefs)
        createChannels()
        FocusTimer.init(this)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_OVERLAY, "Ekran kalemi", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Ekran kalemi açıkken gösterilen kalıcı bildirim"; setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_TIMER, "Odak zamanlayıcı", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Pomodoro oturumu ve mola bitiş uyarıları"
        })
        nm.createNotificationChannel(NotificationChannel(CH_REMINDER, "Hatırlatmalar", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Ödev ve sınav hatırlatmaları"
        })
    }

    companion object {
        lateinit var instance: App; private set
        const val CH_OVERLAY = "overlay"
        const val CH_TIMER = "timer"
        const val CH_REMINDER = "reminder"
    }
}
