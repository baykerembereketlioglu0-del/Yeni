package com.jarvis.asistan.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jarvis.asistan.JarvisApp
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Hatırlatıcıları ve günlük akşam motivasyonunu AlarmManager ile planlar. */
object Scheduler {
    private const val ACTION_REMINDER = "com.jarvis.asistan.REMINDER"
    private const val ACTION_EVENING = "com.jarvis.asistan.EVENING"
    private const val EVENING_ID = 7
    private val fmt = DateTimeFormatter.ofPattern("d MMMM EEEE HH:mm", java.util.Locale("tr"))

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun setExact(ctx: Context, at: LocalDateTime, pi: PendingIntent) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = am(ctx)
        if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun reminderIntent(ctx: Context, id: Int, msg: String) = PendingIntent.getBroadcast(
        ctx, id,
        Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_REMINDER).putExtra("id", id).putExtra("msg", msg),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun addReminder(ctx: Context, at: LocalDateTime, message: String): String {
        val p = JarvisApp.prefs
        val id = p.nextReminderId
        p.nextReminderId = id + 1
        val all = p.reminders
        all.put(id.toString(), JSONObject().put("at", at.toString()).put("msg", message))
        p.reminders = all
        setExact(ctx, at, reminderIntent(ctx, id, message))
        return "Hatırlatıcı kuruldu (no $id): ${at.format(fmt)} — \"$message\""
    }

    fun listReminders(): String {
        val all = JarvisApp.prefs.reminders
        if (all.length() == 0) return "Kurulu hatırlatıcı yok."
        return all.keys().asSequence().map { k ->
            val o = all.getJSONObject(k)
            "no $k: ${LocalDateTime.parse(o.getString("at")).format(fmt)} — ${o.getString("msg")}"
        }.joinToString("\n")
    }

    fun cancelReminder(ctx: Context, id: Int): String {
        val p = JarvisApp.prefs
        val all = p.reminders
        val o = all.optJSONObject(id.toString()) ?: return "Bu numarada hatırlatıcı yok."
        am(ctx).cancel(reminderIntent(ctx, id, o.optString("msg")))
        all.remove(id.toString())
        p.reminders = all
        return "Hatırlatıcı $id iptal edildi."
    }

    internal fun removeReminder(id: Int) {
        val p = JarvisApp.prefs
        val all = p.reminders
        all.remove(id.toString())
        p.reminders = all
    }

    /** Uygulama/yeniden başlatma sonrası tüm zamanlamaları yeniler. */
    fun rescheduleAll(ctx: Context) {
        val p = JarvisApp.prefs
        val all = p.reminders
        val now = LocalDateTime.now()
        for (k in all.keys().asSequence().toList()) {
            val o = all.getJSONObject(k)
            val at = LocalDateTime.parse(o.getString("at"))
            if (at.isBefore(now.minusMinutes(5))) all.remove(k)
            else setExact(ctx, if (at.isBefore(now)) now.plusSeconds(20) else at, reminderIntent(ctx, k.toInt(), o.getString("msg")))
        }
        p.reminders = all
        scheduleEvening(ctx)
    }

    fun scheduleEvening(ctx: Context) {
        val p = JarvisApp.prefs
        val pi = PendingIntent.getBroadcast(
            ctx, EVENING_ID, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_EVENING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am(ctx).cancel(pi)
        if (!p.eveningEnabled) return
        val t = runCatching { LocalTime.parse(p.eveningTime) }.getOrDefault(LocalTime.of(21, 0))
        var at = LocalDateTime.now().with(t).withSecond(0).withNano(0)
        if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
        setExact(ctx, at, pi)
    }

    class AlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_REMINDER -> {
                    val id = intent.getIntExtra("id", -1)
                    val msg = intent.getStringExtra("msg") ?: "Hatırlatma"
                    removeReminder(id)
                    JarvisService.sendEvent(context, JarvisService.EVENT_REMINDER, msg)
                }
                ACTION_EVENING -> {
                    scheduleEvening(context) // yarın için tekrar kur
                    JarvisService.sendEvent(context, JarvisService.EVENT_EVENING, "")
                }
            }
        }
    }

    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
            rescheduleAll(context)
            JarvisService.start(context)
        }
    }
}
