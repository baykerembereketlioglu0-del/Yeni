package app.karala.study

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.karala.App
import app.karala.MainActivity
import app.karala.R
import app.karala.data.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Phase { IDLE, FOCUS, BREAK, LONG_BREAK }

data class FocusState(
    val phase: Phase = Phase.IDLE,
    val running: Boolean = false,
    /** Çalışırken bitiş anı (duvar saati, ms). */
    val endAt: Long = 0,
    /** Duraklatılmışken kalan süre. */
    val pausedRemaining: Long = 0,
    val totalMs: Long = 0,
    /** Bu döngüde tamamlanan odak sayısı. */
    val round: Int = 0,
    val subjectId: Long? = null,
) {
    fun remaining(now: Long = System.currentTimeMillis()): Long =
        if (running) (endAt - now).coerceAtLeast(0) else pausedRemaining
}

/**
 * Pomodoro zamanlayıcı. Durum SharedPreferences'ta saklanır; uygulama kapansa bile bitiş alarmı çalışır.
 * Ana iş parçacığından kullanılır.
 */
object FocusTimer {
    private lateinit var app: App
    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state

    fun init(a: App) {
        app = a
        val sp = a.prefs.sp
        _state.value = FocusState(
            phase = runCatching { Phase.valueOf(sp.getString("ft_phase", "IDLE")!!) }.getOrDefault(Phase.IDLE),
            running = sp.getBoolean("ft_running", false),
            endAt = sp.getLong("ft_end", 0), pausedRemaining = sp.getLong("ft_rem", 0),
            totalMs = sp.getLong("ft_total", 0), round = sp.getInt("ft_round", 0),
            subjectId = sp.getLong("ft_subject", -1).takeIf { it >= 0 },
        )
        tick()
    }

    private fun set(s: FocusState) {
        _state.value = s
        app.prefs.sp.edit().putString("ft_phase", s.phase.name).putBoolean("ft_running", s.running)
            .putLong("ft_end", s.endAt).putLong("ft_rem", s.pausedRemaining).putLong("ft_total", s.totalMs)
            .putInt("ft_round", s.round).putLong("ft_subject", s.subjectId ?: -1).apply()
        if (s.running) Alarms.set(app, s.endAt, alarmIntent()) else Alarms.cancel(app, alarmIntent())
    }

    private fun alarmIntent() = PendingIntent.getBroadcast(app, 1, Intent(app, FocusReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun lengthOf(p: Phase): Long = 60_000L * when (p) {
        Phase.FOCUS -> app.prefs.focusMinutes; Phase.BREAK -> app.prefs.breakMinutes
        Phase.LONG_BREAK -> app.prefs.longBreakMinutes; Phase.IDLE -> 0
    }

    fun setSubject(id: Long?) { set(_state.value.copy(subjectId = id)) }

    fun startFocus() = begin(Phase.FOCUS, _state.value.round)

    private fun begin(p: Phase, round: Int) {
        val len = lengthOf(p)
        set(_state.value.copy(phase = p, running = true, endAt = System.currentTimeMillis() + len,
            pausedRemaining = 0, totalMs = len, round = round))
    }

    fun pause() {
        val s = _state.value
        if (!s.running) return
        set(s.copy(running = false, pausedRemaining = s.remaining()))
    }

    fun resume() {
        val s = _state.value
        if (s.running || s.phase == Phase.IDLE) return
        set(s.copy(running = true, endAt = System.currentTimeMillis() + s.pausedRemaining, pausedRemaining = 0))
    }

    /** Geçerli aşamayı bitirir; odaktaysa o ana kadarki süre kaydedilir. */
    fun skip() {
        val s = _state.value
        if (s.phase == Phase.IDLE) return
        complete(s, elapsedMs = s.totalMs - s.remaining(), notify = false)
    }

    fun reset() {
        val s = _state.value
        if (s.phase == Phase.FOCUS) logFocus(s, s.totalMs - s.remaining())
        set(FocusState(subjectId = s.subjectId))
    }

    /** Süre dolduysa aşamayı tamamlar. Arayüz döngüsünden ve alarmdan çağrılır (tekrar çağrılması zararsızdır). */
    fun tick() {
        val s = _state.value
        if (s.running && System.currentTimeMillis() >= s.endAt) complete(s, s.totalMs, notify = true)
    }

    private fun complete(s: FocusState, elapsedMs: Long, notify: Boolean) {
        when (s.phase) {
            Phase.FOCUS -> {
                logFocus(s, elapsedMs)
                val round = s.round + 1
                val long = round % app.prefs.roundsBeforeLongBreak.coerceAtLeast(1) == 0
                begin(if (long) Phase.LONG_BREAK else Phase.BREAK, round)
                if (notify) Notifier.timer(app, "Odak tamamlandı 🎉", if (long) "Uzun molayı hak ettin." else "Kısa bir mola ver.")
            }
            Phase.BREAK, Phase.LONG_BREAK -> {
                // Mola bitti: yeni odak kullanıcı başlatana kadar bekler.
                val len = lengthOf(Phase.FOCUS)
                set(s.copy(phase = Phase.FOCUS, running = false, pausedRemaining = len, totalMs = len))
                if (notify) Notifier.timer(app, "Mola bitti", "Hazırsan yeni odak oturumunu başlat.")
            }
            Phase.IDLE -> {}
        }
    }

    private fun logFocus(s: FocusState, elapsedMs: Long) {
        val sec = (elapsedMs / 1000).toInt()
        val start = System.currentTimeMillis() - elapsedMs
        app.scope.launch { app.repo.logSession(s.subjectId, start, sec, 0) }
    }
}

class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { FocusTimer.tick() }
}

object Alarms {
    fun set(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }.onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }
    fun cancel(ctx: Context, pi: PendingIntent) = ctx.getSystemService(AlarmManager::class.java).cancel(pi)
}

@android.annotation.SuppressLint("MissingPermission") // allowed() izin kontrolünü yapıyor
object Notifier {
    private fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context, route: String): PendingIntent = PendingIntent.getActivity(ctx, route.hashCode(),
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun timer(ctx: Context, title: String, text: String) {
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, App.CH_TIMER).setSmallIcon(R.drawable.ic_stat_pen)
            .setContentTitle(title).setContentText(text).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openApp(ctx, "focus")).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(2, n) }
    }

    fun reminder(ctx: Context, task: Task) {
        if (!allowed(ctx)) return
        val kind = when (task.type) { 1 -> "Sınav"; 2 -> "Çalışma"; else -> "Ödev" }
        val time = SimpleDateFormat("d MMM EEE HH:mm", Locale("tr")).format(Date(task.dueAt))
        val n = NotificationCompat.Builder(ctx, App.CH_REMINDER).setSmallIcon(R.drawable.ic_stat_pen)
            .setContentTitle("$kind: ${task.title}").setContentText(time).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(openApp(ctx, "plan")).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1000 + (task.id % 100000).toInt(), n) }
    }
}

/** Görev hatırlatmaları. */
object Reminders {
    private fun pi(ctx: Context, taskId: Long) = PendingIntent.getBroadcast(ctx, (taskId % Int.MAX_VALUE).toInt() + 100,
        Intent(ctx, ReminderReceiver::class.java).putExtra("task", taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context, t: Task) {
        Alarms.cancel(ctx, pi(ctx, t.id))
        if (t.done || t.remindMinutes < 0) return
        val at = t.dueAt - t.remindMinutes * 60_000L
        if (at > System.currentTimeMillis()) Alarms.set(ctx, at, pi(ctx, t.id))
    }

    fun cancel(ctx: Context, taskId: Long) = Alarms.cancel(ctx, pi(ctx, taskId))

    suspend fun rescheduleAll(ctx: Context) { App.instance.repo.dao.tasksWithReminder().forEach { schedule(ctx, it) } }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("task", -1)
        val pending = goAsync()
        App.instance.scope.launch {
            try { App.instance.repo.dao.taskNow(id)?.takeIf { !it.done }?.let { Notifier.reminder(context, it) } }
            finally { pending.finish() }
        }
    }
}

/** Yeniden başlatmadan sonra alarmlar silinir; hatırlatmaları ve zamanlayıcı alarmını yeniden kurar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        App.instance.scope.launch {
            try {
                Reminders.rescheduleAll(context)
                // Ekran notları kaldırılana kadar kalır: yeniden başlatmadan sonra ekrana geri getir.
                app.karala.overlay.StickyService.restoreIfNeeded(context)
                val s = FocusTimer.state.value
                if (s.running) { FocusTimer.pause(); FocusTimer.resume() }
            } finally { pending.finish() }
        }
    }
}
