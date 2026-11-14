package app.karala.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.karala.MainActivity
import app.karala.data.Repo
import app.karala.overlay.OverlayService
import app.karala.study.Srs
import java.util.Calendar

/** Ardışık çalışma günü sayısı: bugün (ya da dün) dahil geriye doğru kesintisiz etkin günler. */
fun streak(activeDays: Set<Long>, today: Long): Int {
    var day = if (today in activeDays) today else today - 1
    var n = 0
    while (day in activeDays) { n++; day-- }
    return n
}

private fun dayIndex(ms: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return (ms + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / Srs.DAY_MS
}

@Composable
fun HomeScreen(nav: NavController) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { now = System.currentTimeMillis(); canOverlay = Settings.canDrawOverlays(ctx) }

    val running by OverlayService.running.collectAsStateWithLifecycle()
    val subjects by repo.dao.subjects().collectAsStateWithLifecycle(emptyList())
    val tasks by repo.dao.tasks().collectAsStateWithLifecycle(emptyList())
    val schedule by repo.dao.schedule().collectAsStateWithLifecycle(emptyList())
    val since = remember(now) { Repo.startOfDay(-120) }
    val sessions by repo.dao.sessionsSince(since).collectAsStateWithLifecycle(emptyList())
    val logs by repo.dao.logsSince(since).collectAsStateWithLifecycle(emptyList())
    val dueMistakes by repo.dao.dueMistakeCount(now).collectAsStateWithLifecycle(0)
    val dueCards by repo.dao.dueCardCount(now).collectAsStateWithLifecycle(0)

    val todayStart = remember(now) { Repo.startOfDay() }
    val minutesToday = sessions.filter { it.startAt >= todayStart }.sumOf { it.durationSec } / 60
    val questionsToday = logs.filter { it.at >= todayStart }.sumOf { it.correct + it.wrong + it.blank }
    val activeDays = remember(sessions, logs) { (sessions.map { dayIndex(it.startAt) } + logs.map { dayIndex(it.at) }).toSet() }
    val streak = streak(activeDays, dayIndex(now))
    val nextExam = tasks.firstOrNull { it.type == 1 && !it.done && it.dueAt >= todayStart }
    val openTasks = tasks.filter { !it.done && it.dueAt < Repo.startOfDay(2) }.take(4)
    val dow = remember(now) { (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1 }
    val todayLessons = schedule.filter { it.dayOfWeek == dow }

    Screen("Karala", actions = { IconButton({ nav.navigate("settings") }) { Icon(Icons.Rounded.Settings, "Ayarlar") } }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            // ---- Ekran kalemi ----
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Draw, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Text("Ekran kalemi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            !canOverlay -> "Videonun, PDF'in ya da herhangi bir uygulamanın üzerine yazabilmek için \"diğer uygulamaların üzerinde göster\" iznini ver."
                            running -> "Açık. Video uygulamana geç; yüzen kalem düğmesi her yerde seninle."
                            else -> "Ders videosundaki soruyu durdur, üzerine çöz, tek dokunuşla defterine kaydet."
                        },
                        color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button({
                            if (running) OverlayService.stop(ctx) else MainActivity.startPen(ctx)
                        }) {
                            Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (!canOverlay) "İzin ver" else if (running) "Kapat" else "Başlat")
                        }
                    }
                }
            }

            // ---- Bugün ----
            SectionTitle("Bugün")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(minutesText(minutesToday), "çalışma", Modifier.weight(1f))
                StatTile("$questionsToday", "soru", Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                StatTile("$streak 🔥", "gün seri", Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.height(12.dp))
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    val mg = prefs.dailyMinuteGoal.coerceAtLeast(1); val qg = prefs.dailyQuestionGoal.coerceAtLeast(1)
                    Text("Günlük hedef · $minutesToday/$mg dk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp)); GoalBar(minutesToday / mg.toFloat())
                    Spacer(Modifier.height(10.dp))
                    Text("Soru hedefi · $questionsToday/$qg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp)); GoalBar(questionsToday / qg.toFloat(), color = MaterialTheme.colorScheme.secondary)
                }
            }

            // ---- Sınav geri sayımı ----
            if (nextExam != null) {
                Spacer(Modifier.height(12.dp))
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("plan") { launchSingleTop = true } }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        val days = ((nextExam.dueAt - todayStart) / Srs.DAY_MS).toInt()
                        Text("$days", fontSize = 34.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("gün kaldı", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(nextExam.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(fmt(nextExam.dueAt, "d MMMM EEEE"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }

            // ---- Tekrar ----
            if (dueMistakes + dueCards > 0) {
                SectionTitle("Tekrar zamanı")
                if (dueMistakes > 0) ActionRow(Icons.Rounded.ErrorOutline, "$dueMistakes hatalı soru seni bekliyor", "Unutmadan yeniden çöz") { nav.navigate("mistakeReview") }
                if (dueCards > 0) ActionRow(Icons.Rounded.Style, "$dueCards bilgi kartı tekrar edilecek", "Tekrar sekmesinden desteni seç") { nav.navigate("review") { launchSingleTop = true } }
            }

            // ---- Yaklaşan işler ----
            if (openTasks.isNotEmpty()) {
                SectionTitle("Yaklaşan işler")
                openTasks.forEach { t ->
                    val late = t.dueAt < now
                    ActionRow(
                        when (t.type) { 1 -> Icons.Rounded.School; 2 -> Icons.Rounded.AutoStories; else -> Icons.Rounded.Assignment },
                        t.title, "${subjects.byId(t.subjectId)?.name?.plus(" · ") ?: ""}${relativeDay(t.dueAt, now)} ${fmt(t.dueAt, "HH:mm")}",
                        danger = late,
                    ) { nav.navigate("plan") { launchSingleTop = true } }
                }
            }

            // ---- Bugünkü dersler ----
            if (todayLessons.isNotEmpty()) {
                SectionTitle("Bugünkü program")
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        todayLessons.forEach { e ->
                            val s = subjects.byId(e.subjectId)
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${hm(e.startMinute)}–${hm(e.endMinute)}", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
                                SubjectDot(s); Spacer(Modifier.width(8.dp))
                                Text(e.title.ifBlank { s?.name ?: "Ders" }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (e.place.isNotBlank()) Text(e.place, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // ---- Kısayollar ----
            SectionTitle("Hızlı başla")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Quick(Icons.Rounded.Timer, "Odaklan", Modifier.weight(1f)) { nav.navigate("focus") }
                Quick(Icons.Rounded.Calculate, "Hesapla", Modifier.weight(1f)) { nav.navigate("calc") }
                Quick(Icons.Rounded.Functions, "Formüller", Modifier.weight(1f)) { nav.navigate("formulas") }
                Quick(Icons.Rounded.BarChart, "İstatistik", Modifier.weight(1f)) { nav.navigate("stats") }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Quick(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 4.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
