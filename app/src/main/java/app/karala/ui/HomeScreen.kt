package app.karala.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
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
import app.karala.overlay.Icon as KI
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

private fun greeting(now: Long): String = when (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)) {
    in 5..10 -> "Günaydın"; in 11..16 -> "İyi günler"; in 17..21 -> "İyi akşamlar"; else -> "İyi geceler"
}

@Composable
fun HomeScreen(nav: NavController, pen: PenLauncher = rememberPenLauncher()) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { now = System.currentTimeMillis(); canOverlay = Settings.canDrawOverlays(ctx); pen.stylus = prefs.stylusOnly }

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
    val nowMinute = remember(now) { Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) } }
    val k = K

    // Surface: içerik rengini (koyu temada açık yazı) alt bileşenlere taşır.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().statusBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 24.dp)) {

        // ---- Karşılama ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fmt(now, "d MMMM EEEE").uppercase(TR), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${greeting(now)} 👋", style = MaterialTheme.typography.headlineMedium)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button) { nav.navigate("settings") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Settings, "Ayarlar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(18.dp))

        // ---- Ekran kalemi ----
        PenHero(canOverlay, running, pen.stylus, onStart = { if (running) OverlayService.stop(ctx) else pen.launch() }, onMode = { pen.pickMode() })

        // ---- Bugün ----
        SectionTitle("Bugün") { TextButton({ nav.navigate("stats") }) { Text("İstatistik") } }
        KCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(vertical = 18.dp, horizontal = 8.dp)) {
                val mg = prefs.dailyMinuteGoal.coerceAtLeast(1); val qg = prefs.dailyQuestionGoal.coerceAtLeast(1)
                RingStat(minutesToday / mg.toFloat(), k.sun, "$minutesToday", "dk çalışma", "hedef $mg", Modifier.weight(1f))
                RingStat(questionsToday / qg.toFloat(), k.mint, "$questionsToday", "soru", "hedef $qg", Modifier.weight(1f))
                RingStat(streak / 7f, k.coral, "$streak", "gün seri", if (streak > 0) "devam 🔥" else "bugün başla", Modifier.weight(1f))
            }
        }

        // ---- Sınav geri sayımı ----
        if (nextExam != null) {
            Spacer(Modifier.height(12.dp))
            val days = ((nextExam.dueAt - todayStart) / Srs.DAY_MS).toInt()
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(k.dusk)
                .clickable { nav.navigate("plan") { launchSingleTop = true } }.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$days", style = MaterialTheme.typography.displaySmall, color = Color.White)
                    Text("GÜN", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sınava kalan", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                    Text(nextExam.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fmt(nextExam.dueAt, "d MMMM EEEE"), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                }
                Icon(Icons.Rounded.School, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(34.dp))
            }
        }

        // ---- Tekrar ----
        if (dueMistakes + dueCards > 0) {
            SectionTitle("Tekrar zamanı")
            if (dueMistakes > 0) ActionRow(Icons.Rounded.ErrorOutline, k.coral, "$dueMistakes hatalı soru seni bekliyor", "Unutmadan yeniden çöz") { nav.navigate("mistakeReview") }
            if (dueCards > 0) ActionRow(Icons.Rounded.Style, k.violet, "$dueCards bilgi kartı tekrar edilecek", "Tekrar sekmesinden desteni seç") { nav.navigate("review") { launchSingleTop = true } }
        }

        // ---- Yaklaşan işler ----
        if (openTasks.isNotEmpty()) {
            SectionTitle("Yaklaşan işler") { TextButton({ nav.navigate("plan") { launchSingleTop = true } }) { Text("Tümü") } }
            openTasks.forEach { t ->
                val late = t.dueAt < now
                ActionRow(
                    when (t.type) { 1 -> Icons.Rounded.School; 2 -> Icons.Rounded.AutoStories; else -> Icons.Rounded.Assignment },
                    if (late) MaterialTheme.colorScheme.error else subjects.byId(t.subjectId)?.tint() ?: k.sky,
                    t.title, "${subjects.byId(t.subjectId)?.name?.plus(" · ") ?: ""}${relativeDay(t.dueAt, now)} ${fmt(t.dueAt, "HH:mm")}",
                    danger = late,
                ) { nav.navigate("plan") { launchSingleTop = true } }
            }
        }

        // ---- Bugünkü dersler (zaman çizelgesi) ----
        if (todayLessons.isNotEmpty()) {
            SectionTitle("Bugünkü program")
            KCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    todayLessons.forEachIndexed { i, e ->
                        val s = subjects.byId(e.subjectId)
                        val live = nowMinute in e.startMinute until e.endMinute
                        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                            Text(hm(e.startMinute), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp),
                                color = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.fillMaxHeight().width(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.width(2.dp).weight(1f).background(if (i == 0) Color.Transparent else MaterialTheme.colorScheme.outlineVariant))
                                Box(Modifier.size(if (live) 14.dp else 10.dp).clip(CircleShape).background(s.tint()))
                                Box(Modifier.width(2.dp).weight(1f).background(if (i == todayLessons.lastIndex) Color.Transparent else MaterialTheme.colorScheme.outlineVariant))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f).padding(vertical = 9.dp)) {
                                Text(e.title.ifBlank { s?.name ?: "Ders" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${hm(e.startMinute)}–${hm(e.endMinute)}" + if (e.place.isNotBlank()) " · ${e.place}" else "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (live) Text("ŞİMDİ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }

        // ---- Kısayollar ----
        SectionTitle("Hızlı başla")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Quick(Icons.Rounded.Timer, "Odaklan", k.sun, Modifier.weight(1f)) { nav.navigate("focus") }
            Quick(Icons.Rounded.Calculate, "Hesapla", k.mint, Modifier.weight(1f)) { nav.navigate("calc") }
            Quick(Icons.Rounded.Functions, "Formüller", k.sky, Modifier.weight(1f)) { nav.navigate("formulas") }
            Quick(Icons.Rounded.Leaderboard, "Denemeler", k.violet, Modifier.weight(1f)) { nav.navigate("exams") }
        }
    }
    }
}

/** Ana eylem kartı: ekran kalemini başlatır; karalama izi açılışta kendi kendini çizer. */
@Composable
private fun PenHero(canOverlay: Boolean, running: Boolean, stylus: Boolean, onStart: () -> Unit, onMode: () -> Unit) {
    val k = K
    val ink = k.onBrand
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(1400)) }
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(k.brand)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            // Sağ üstte karalama: bir dalga ve bir halka; sırayla çizilirler.
            val wave = Path().apply {
                moveTo(w * 0.64f, h * 0.26f)
                cubicTo(w * 0.69f, h * 0.02f, w * 0.73f, h * 0.44f, w * 0.78f, h * 0.20f)
                cubicTo(w * 0.82f, h * 0.00f, w * 0.86f, h * 0.40f, w * 0.91f, h * 0.17f)
                cubicTo(w * 0.94f, h * 0.05f, w * 0.97f, h * 0.17f, w * 0.99f, h * 0.11f)
            }
            val loop = Path().apply {
                moveTo(w * 0.80f, h * 0.62f)
                cubicTo(w * 0.80f, h * 0.44f, w * 0.99f, h * 0.44f, w * 0.97f, h * 0.66f)
                cubicTo(w * 0.95f, h * 0.90f, w * 0.72f, h * 0.86f, w * 0.80f, h * 0.60f)
            }
            val seg = Path()
            listOf(wave to (draw.value * 2f), loop to (draw.value * 2f - 1f)).forEach { (path, t) ->
                val part = t.coerceIn(0f, 1f)
                if (part > 0f) PathMeasure().apply { setPath(path, false) }.let { m -> m.getSegment(0f, m.length * part, seg, true) }
            }
            drawPath(seg, ink.copy(alpha = 0.16f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(ink), contentAlignment = Alignment.Center) {
                    KIcon(KI.PEN, k.sun.takeIf { k.dark } ?: Color(0xFFFFD24A), 24.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Ekran kalemi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = ink)
                    if (running) Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF0E8F60)))
                        Spacer(Modifier.width(6.dp))
                        Text("Açık", style = MaterialTheme.typography.labelMedium, color = ink)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    !canOverlay -> "Videonun, PDF'in ya da herhangi bir uygulamanın üzerine yazabilmek için bir izin gerekiyor."
                    running -> "Video uygulamana geç; yüzen kalem düğmesi her yerde seninle."
                    else -> "Videodaki soruyu durdur, üzerine çöz, tek dokunuşla defterine kaydet."
                },
                style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.82f), modifier = Modifier.fillMaxWidth(0.78f),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(ink).clickable(role = Role.Button, onClick = onStart).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = Color(0xFFFFD24A))
                    Spacer(Modifier.width(6.dp))
                    Text(if (!canOverlay) "İzin ver" else if (running) "Kapat" else "Başlat", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFD24A))
                }
                // Çizim girişi: el / kalem
                Row(Modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(ink.copy(alpha = 0.12f))
                    .border(1.dp, ink.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button, onClickLabel = "Çizim girişini değiştir", onClick = onMode).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    KIcon(if (stylus) KI.STYLUS else KI.HAND, ink, 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(if (stylus) "Kalem" else "El", style = MaterialTheme.typography.titleSmall, color = ink)
                    Icon(Icons.Rounded.ExpandMore, null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun RingStat(progress: Float, color: Color, value: String, label: String, hint: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(progress, color, Modifier.size(84.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, tint: Color, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    KCard(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Quick(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    KCard(modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 4.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBubble(icon, tint, 44)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
