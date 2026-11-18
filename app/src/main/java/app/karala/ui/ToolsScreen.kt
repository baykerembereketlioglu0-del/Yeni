package app.karala.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.karala.data.ExamResult
import app.karala.data.Repo
import app.karala.data.Subject
import app.karala.study.Expr
import app.karala.study.FocusTimer
import app.karala.study.Phase
import app.karala.study.Srs
import app.karala.study.Units
import app.karala.study.net
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun ToolsScreen(nav: NavController, pen: PenLauncher = rememberPenLauncher()) {
    data class Tool(val route: String, val title: String, val hint: String, val icon: ImageVector, val tint: Color)
    val k = K
    val tools = listOf(
        Tool("sticky", "Ekran notu", "Her uygulamanın üstünde duran yapışkan not", Icons.Rounded.StickyNote2, k.sun),
        Tool("focus", "Odak", "Pomodoro: 25 dk çalış, 5 dk dinlen", Icons.Rounded.Timer, k.coral),
        Tool("stats", "İstatistik", "Çalışma süren ve soru sayıların", Icons.Rounded.BarChart, k.mint),
        Tool("exams", "Deneme takibi", "Netlerini kaydet, gelişimini gör", Icons.Rounded.Leaderboard, k.violet),
        Tool("calc", "Hesap makinesi", "Bilimsel, trigonometri dahil", Icons.Rounded.Calculate, k.sky),
        Tool("formulas", "Formül kitapçığı", "Matematik, geometri, fizik, kimya", Icons.Rounded.Functions, k.coral),
        Tool("periodic", "Periyodik tablo", "118 element, Türkçe adlarıyla", Icons.Rounded.Science, k.mint),
        Tool("convert", "Birim çevirici", "Uzunluk, kütle, sıcaklık, enerji…", Icons.Rounded.SwapHoriz, k.sun),
    )
    Screen("Araçlar") { pad ->
        LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tools) { t ->
                KCard(Modifier.clip(MaterialTheme.shapes.medium).clickable { if (t.route == "sticky") pen.addNote() else nav.navigate(t.route) }) {
                    Column(Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 118.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(t.icon, t.tint, 46)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Rounded.ArrowOutward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(t.title, style = MaterialTheme.typography.titleMedium)
                        Text(t.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------------- Odak ----------------

private fun clock(ms: Long): String { val s = (ms + 999) / 1000; return "%02d:%02d".format(s / 60, s % 60) }

@Composable
fun FocusScreen(onBack: () -> Unit) {
    val state by FocusTimer.state.collectAsStateWithLifecycle()
    val subjects by repo.dao.subjects().collectAsStateWithLifecycle(emptyList())
    val todayStart = remember { Repo.startOfDay() }
    val sessions by repo.dao.sessionsSince(todayStart).collectAsStateWithLifecycle(emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var settings by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.running) {
        while (true) { now = System.currentTimeMillis(); FocusTimer.tick(); if (!state.running) break; delay(250) }
    }
    val idle = state.phase == Phase.IDLE
    tick
    val total = if (idle) prefs.focusMinutes * 60_000L else state.totalMs
    val remaining = if (idle) total else state.remaining(now)
    val isBreak = state.phase == Phase.BREAK || state.phase == Phase.LONG_BREAK
    val color = if (isBreak) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Screen("Odak", onBack, actions = { IconButton({ settings = true }) { Icon(Icons.Rounded.Tune, "Süreler") } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SubjectPicker(subjects, state.subjectId, { FocusTimer.setSubject(it) })
            Spacer(Modifier.height(24.dp))
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 16.dp.toPx()
                    val inset = Offset(w / 2, w / 2); val sz = Size(size.width - w, size.height - w)
                    drawArc(track, 0f, 360f, false, inset, sz, style = Stroke(w))
                    drawArc(color, -90f, 360f * (remaining / total.coerceAtLeast(1).toFloat()), false, inset, sz, style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(when (state.phase) { Phase.IDLE -> "HAZIR"; Phase.FOCUS -> "ODAK"; Phase.BREAK -> "KISA MOLA"; Phase.LONG_BREAK -> "UZUN MOLA" },
                        style = MaterialTheme.typography.labelLarge, color = color)
                    Text(clock(remaining), style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"))
                    Text("${state.round % prefs.roundsBeforeLongBreak.coerceAtLeast(1)}/${prefs.roundsBeforeLongBreak} tur",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton({ FocusTimer.reset() }, enabled = !idle) { Icon(Icons.Rounded.Refresh, "Sıfırla") }
                Button({
                    when { idle -> FocusTimer.startFocus(); state.running -> FocusTimer.pause(); else -> FocusTimer.resume() }
                }, Modifier.height(56.dp).widthIn(min = 160.dp)) {
                    Icon(if (state.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(when { idle -> "Başlat"; state.running -> "Duraklat"; else -> "Devam et" })
                }
                OutlinedIconButton({ FocusTimer.skip() }, enabled = !idle) { Icon(Icons.Rounded.SkipNext, "Aşamayı geç") }
            }
            Spacer(Modifier.height(24.dp))
            val min = sessions.sumOf { it.durationSec } / 60
            Text("Bugün ${sessions.size} oturum · ${minutesText(min)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Süre dolunca uygulama kapalı olsa bile bildirim gelir.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
    if (settings) AlertDialog(
        onDismissRequest = { settings = false; tick++ }, title = { Text("Süreler") },
        text = {
            Column {
                Stepper("Odak", prefs.focusMinutes, 5, 120, 5) { prefs.focusMinutes = it }
                Stepper("Kısa mola", prefs.breakMinutes, 1, 30, 1) { prefs.breakMinutes = it }
                Stepper("Uzun mola", prefs.longBreakMinutes, 5, 60, 5) { prefs.longBreakMinutes = it }
                Stepper("Uzun moladan önce tur", prefs.roundsBeforeLongBreak, 2, 8, 1, unit = "") { prefs.roundsBeforeLongBreak = it }
            }
        },
        confirmButton = { TextButton({ settings = false; tick++ }) { Text("Tamam") } },
    )
}

@Composable
fun Stepper(label: String, initial: Int, min: Int, max: Int, step: Int, unit: String = " dk", onChange: (Int) -> Unit) {
    var v by remember { mutableIntStateOf(initial) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton({ v = (v - step).coerceAtLeast(min); onChange(v) }) { Icon(Icons.Rounded.Remove, "Azalt") }
        Text("$v$unit", Modifier.widthIn(min = 56.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        IconButton({ v = (v + step).coerceAtMost(max); onChange(v) }) { Icon(Icons.Rounded.Add, "Artır") }
    }
}

// ---------------- Hesap makinesi ----------------

@Composable
fun CalculatorScreen(onBack: () -> Unit) {
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("0") }
    var error by remember { mutableStateOf(false) }
    var degrees by remember { mutableStateOf(prefs.calcDegrees) }
    var justEvaluated by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<Pair<String, String>>() }
    val fns = listOf("sin", "cos", "tan", "asin", "acos", "atan", "ln", "log")

    fun preview() {
        val v = runCatching { Expr.evaluate(expr, degrees) }.getOrNull()
        if (v != null) { result = Expr.format(v).replace('.', ','); error = false }
    }
    fun press(k: String) {
        when (k) {
            "C" -> { expr = ""; result = "0"; error = false }
            "⌫" -> { val fn = fns.map { "$it(" }.filter { expr.endsWith(it) }.maxByOrNull { it.length }; expr = expr.dropLast(fn?.length ?: 1); if (expr.isEmpty()) result = "0" else preview() }
            "=" -> if (expr.isNotBlank()) {
                try {
                    val text = Expr.format(Expr.evaluate(expr, degrees)).replace('.', ',')
                    history.add(0, expr to text); if (history.size > 30) history.removeAt(history.size - 1)
                    result = text; expr = text; error = false; justEvaluated = true; return
                } catch (e: Expr.Error) { result = e.message ?: "Hata"; error = true }
            }
            else -> {
                if (justEvaluated && (k[0].isDigit() || k == ",")) expr = ""
                expr += if (k in fns) "$k(" else k
                preview()
            }
        }
        justEvaluated = false
    }

    Screen("Hesap makinesi", onBack, actions = {
        TextButton({ degrees = !degrees; prefs.calcDegrees = degrees; preview() }) { Text(if (degrees) "DERECE" else "RADYAN") }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true) {
                items(history) { (e, r) ->
                    Column(Modifier.fillMaxWidth().clickable { expr = r; preview() }.padding(vertical = 4.dp), horizontalAlignment = Alignment.End) {
                        Text(e, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("= $r", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Text(expr.ifEmpty { " " }, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), reverseScrolling = true), textAlign = TextAlign.End,
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(result, Modifier.fillMaxWidth(), textAlign = TextAlign.End, fontSize = if (error) 22.sp else 40.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            val rows = listOf(
                listOf("sin", "cos", "tan", "ln", "log"),
                listOf("asin", "acos", "atan", "π", "e"),
                listOf("C", "(", ")", "⌫", "÷"),
                listOf("7", "8", "9", "×", "√"),
                listOf("4", "5", "6", "−", "^"),
                listOf("1", "2", "3", "+", "!"),
                listOf("0", ",", "%", "=", ),
            )
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { k ->
                        val isNum = k[0].isDigit() || k == ","
                        val bg = when { k == "=" -> MaterialTheme.colorScheme.primary; isNum -> MaterialTheme.colorScheme.surfaceContainerHigh; else -> MaterialTheme.colorScheme.surfaceContainer }
                        val fg = when { k == "=" -> MaterialTheme.colorScheme.onPrimary; k == "C" || k == "⌫" -> MaterialTheme.colorScheme.error; isNum -> MaterialTheme.colorScheme.onSurface; else -> MaterialTheme.colorScheme.primary }
                        Box(Modifier.weight(if (k == "=") 2f else 1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable { press(k) },
                            contentAlignment = Alignment.Center) {
                            Text(k, color = fg, fontSize = if (k.length > 1) 15.sp else 20.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Birim çevirici ----------------

@Composable
fun ConverterScreen(onBack: () -> Unit) {
    var cat by remember { mutableIntStateOf(0) }
    val c = Units.categories[cat]
    var from by remember(cat) { mutableIntStateOf(0) }
    var to by remember(cat) { mutableIntStateOf(1.coerceAtMost(c.units.size - 1)) }
    var input by remember { mutableStateOf("1") }
    val value = input.replace(',', '.').toDoubleOrNull()

    Screen("Birim çevirici", onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Units.categories.forEachIndexed { i, k -> FilterChip(cat == i, { cat = i }, { Text(k.name) }) }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(input, { input = it.filter { ch -> ch.isDigit() || ch in ",.-eE" }.take(18) }, label = { Text("Değer") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                suffix = { Text(c.units[from].symbol) })
            Spacer(Modifier.height(8.dp))
            Text("Kaynak birim", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                c.units.forEachIndexed { i, u -> FilterChip(from == i, { from = i }, { Text(u.name) }) }
            }
            SectionTitle("Sonuçlar")
            c.units.forEachIndexed { i, u ->
                if (i == from) return@forEachIndexed
                val out = value?.let { Expr.format(Units.convert(c, it, c.units[from], u)).replace('.', ',') } ?: "—"
                Surface(shape = MaterialTheme.shapes.medium, color = if (i == to) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(MaterialTheme.shapes.medium).clickable { to = i }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(u.name, Modifier.weight(1f))
                        Text("$out ${u.symbol}", fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// ---------------- İstatistik ----------------

@Composable
fun BarChart(values: List<Float>, labels: List<String>, color: Color, modifier: Modifier = Modifier, highlight: Int = -1) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n; val bw = slot * 0.56f
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - bw) / 2
                drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 3))
                val h = size.height * (v / max)
                if (h > 0) drawRoundRect(if (i == highlight || highlight < 0) color else color.copy(alpha = 0.55f), Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 3))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val subjects by repo.dao.subjects().collectAsStateWithLifecycle(emptyList())
    val weekStart = remember { Repo.startOfDay(-6) }
    val monthStart = remember { Repo.startOfDay(-29) }
    val sessions by repo.dao.sessionsSince(monthStart).collectAsStateWithLifecycle(emptyList())
    val logs by repo.dao.logsSince(monthStart).collectAsStateWithLifecycle(emptyList())
    val bySubject by repo.dao.studyBySubject(monthStart).collectAsStateWithLifecycle(emptyList())
    var logging by remember { mutableStateOf(false) }

    val dayNames = listOf("Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt")
    val labels = (0..6).map { i -> dayNames[Calendar.getInstance().apply { timeInMillis = weekStart + i * Srs.DAY_MS + 43_200_000L }.get(Calendar.DAY_OF_WEEK) - 1] }
    fun bucket(ms: Long) = ((ms - weekStart) / Srs.DAY_MS).toInt()
    val minutes = FloatArray(7); val questions = FloatArray(7)
    sessions.forEach { s -> val b = bucket(s.startAt); if (s.startAt >= weekStart && b in 0..6) minutes[b] += s.durationSec / 60f }
    logs.forEach { l -> val b = bucket(l.at); if (l.at >= weekStart && b in 0..6) questions[b] += (l.correct + l.wrong + l.blank).toFloat() }
    val correct = logs.sumOf { it.correct }; val wrong = logs.sumOf { it.wrong }; val blank = logs.sumOf { it.blank }

    Screen("İstatistik", onBack, actions = { TextButton({ logging = true }) { Text("Soru ekle") } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SectionTitle("Son 7 gün · çalışma (dk)")
            Text("Toplam ${minutesText(minutes.sum().toInt())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            BarChart(minutes.toList(), labels, MaterialTheme.colorScheme.primary, highlight = 6)
            SectionTitle("Son 7 gün · çözülen soru")
            Text("Toplam ${questions.sum().toInt()} soru", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            BarChart(questions.toList(), labels, MaterialTheme.colorScheme.secondary, highlight = 6)

            SectionTitle("Son 30 gün · sorular")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("$correct", "doğru", Modifier.weight(1f), Good)
                StatTile("$wrong", "yanlış", Modifier.weight(1f), Bad)
                StatTile("$blank", "boş", Modifier.weight(1f), MaterialTheme.colorScheme.onSurfaceVariant)
                StatTile(if (correct + wrong > 0) "%${correct * 100 / (correct + wrong)}" else "—", "isabet", Modifier.weight(1f))
            }

            SectionTitle("Son 30 gün · derslere göre süre")
            if (bySubject.isEmpty()) Text("Henüz kayıtlı çalışma yok. Odak zamanlayıcıyı kullandıkça burası dolacak.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val maxSec = bySubject.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
            bySubject.forEach { st ->
                val s = subjects.byId(st.subjectId)
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s?.name ?: "Genel", Modifier.width(96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    GoalBar(st.total / maxSec.toFloat(), Modifier.weight(1f), s.tint())
                    Text(minutesText((st.total / 60).toInt()), Modifier.width(84.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium)
                }
            }

            SectionTitle("Son 30 gün · derslere göre soru")
            val perSubject = logs.groupBy { it.subjectId }.mapValues { (_, l) -> Triple(l.sumOf { it.correct }, l.sumOf { it.wrong }, l.sumOf { it.blank }) }
                .filterValues { it.first + it.second + it.third > 0 }
            if (perSubject.isEmpty()) Text("Henüz soru kaydı yok. Ekran kalemindeki soru sayacını ya da \"Soru ekle\"yi kullan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            perSubject.forEach { (sid, t) ->
                val s = subjects.byId(sid)
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    SubjectDot(s); Spacer(Modifier.width(8.dp))
                    Text(s?.name ?: "Genel", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${t.first} D · ${t.second} Y · ${t.third} B", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("  net ${"%.1f".format(net(t.first, t.second, prefs.examDivisor))}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (logging) QuestionLogDialog(subjects, { logging = false }) { sid, d, y, b -> scope.launch { repo.logQuestions(sid, d, y, b) } }
}

@Composable
private fun QuestionLogDialog(subjects: List<Subject>, onDismiss: () -> Unit, onSave: (Long?, Int, Int, Int) -> Unit) {
    var subject by remember { mutableStateOf(prefs.lastSubjectId.takeIf { it >= 0 }) }
    var d by remember { mutableStateOf("") }; var y by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Çözdüğün soruları ekle") },
        text = {
            Column {
                SubjectPicker(subjects, subject, { subject = it })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(d, { d = it }, "Doğru", Modifier.weight(1f))
                    NumberField(y, { y = it }, "Yanlış", Modifier.weight(1f))
                    NumberField(b, { b = it }, "Boş", Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            val total = (d.toIntOrNull() ?: 0) + (y.toIntOrNull() ?: 0) + (b.toIntOrNull() ?: 0)
            TextButton({ onSave(subject, d.toIntOrNull() ?: 0, y.toIntOrNull() ?: 0, b.toIntOrNull() ?: 0); onDismiss() }, enabled = total > 0) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Vazgeç") } },
    )
}

// ---------------- Deneme takibi ----------------

@Composable
fun ExamsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val exams by repo.dao.exams().collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<ExamResult?>(null) }
    var adding by remember { mutableStateOf(false) }
    val nets = exams.map { net(it.correct, it.wrong, it.divisor) }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.surfaceContainerHighest

    Screen("Deneme takibi", onBack, fab = {
        ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Deneme ekle") })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            if (exams.isEmpty()) item { Empty("Henüz deneme yok", "Her denemeden sonra doğru-yanlış sayını gir; netinin zaman içindeki gidişini gör.") }
            if (nets.size >= 2) item {
                KCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        val diff = nets.last() - nets[nets.size - 2]
                        Text("Net gelişimi", fontWeight = FontWeight.SemiBold)
                        Text("Son deneme: ${"%.2f".format(nets.last())} (${if (diff >= 0) "+" else ""}${"%.2f".format(diff)}) · ortalama ${"%.2f".format(nets.average())} · en iyi ${"%.2f".format(nets.max())}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                            val lo = nets.min(); val hi = nets.max(); val span = (hi - lo).coerceAtLeast(1f)
                            for (i in 0..3) { val y = size.height * i / 3f; drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
                            val pad2 = 6.dp.toPx()
                            fun pt(i: Int) = Offset(pad2 + (size.width - 2 * pad2) * i / (nets.size - 1).toFloat(),
                                pad2 + (size.height - 2 * pad2) * (1 - (nets[i] - lo) / span))
                            val p = Path().apply { moveTo(pt(0).x, pt(0).y); for (i in 1 until nets.size) lineTo(pt(i).x, pt(i).y) }
                            drawPath(p, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                            for (i in nets.indices) drawCircle(line, 4.dp.toPx(), pt(i))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            items(exams.reversed(), key = { it.id }) { e ->
                KCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(MaterialTheme.shapes.medium).clickable { editing = e }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${fmt(e.at, "d MMM yyyy")} · ${e.correct} D · ${e.wrong} Y · ${e.blank} B", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("%.2f".format(net(e.correct, e.wrong, e.divisor)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(" net", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (adding || editing != null) ExamDialog(editing, { adding = false; editing = null }, onDelete = { e -> scope.launch { repo.dao.deleteExam(e.id) } }) { e ->
        scope.launch { if (e.id == 0L) repo.dao.insert(e) else repo.dao.update(e) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDialog(exam: ExamResult?, onDismiss: () -> Unit, onDelete: (ExamResult) -> Unit, onSave: (ExamResult) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(exam?.name ?: "") }
    var at by remember { mutableLongStateOf(exam?.at ?: System.currentTimeMillis()) }
    var d by remember { mutableStateOf(exam?.correct?.toString() ?: "") }
    var y by remember { mutableStateOf(exam?.wrong?.toString() ?: "") }
    var b by remember { mutableStateOf(exam?.blank?.toString() ?: "") }
    var divisor by remember { mutableIntStateOf(exam?.divisor ?: prefs.examDivisor) }
    val options = listOf(4 to "4 yanlış 1 doğru", 3 to "3 yanlış 1 doğru", 0 to "Götürmez")
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (exam == null) "Deneme ekle" else "Denemeyi düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Deneme adı (ör. TYT-3)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedButton({ pickDate(ctx, at) { at = it } }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Event, null); Spacer(Modifier.width(8.dp)); Text(fmt(at, "d MMMM yyyy")) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(d, { d = it }, "Doğru", Modifier.weight(1f))
                    NumberField(y, { y = it }, "Yanlış", Modifier.weight(1f))
                    NumberField(b, { b = it }, "Boş", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                options.forEach { (v, label) ->
                    Row(Modifier.fillMaxWidth().clickable { divisor = v }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(divisor == v, { divisor = v }); Text(label)
                    }
                }
                Text("Net: ${"%.2f".format(net(d.toIntOrNull() ?: 0, y.toIntOrNull() ?: 0, divisor))}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton({
                prefs.examDivisor = divisor
                onSave((exam ?: ExamResult(name = "", at = 0, correct = 0, wrong = 0, blank = 0)).copy(name = name.trim(), at = at,
                    correct = d.toIntOrNull() ?: 0, wrong = y.toIntOrNull() ?: 0, blank = b.toIntOrNull() ?: 0, divisor = divisor))
                onDismiss()
            }, enabled = name.isNotBlank() && (d.isNotEmpty() || y.isNotEmpty())) { Text("Kaydet") }
        },
        dismissButton = {
            Row {
                if (exam != null) TextButton({ onDelete(exam); onDismiss() }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                TextButton(onDismiss) { Text("Vazgeç") }
            }
        },
    )
}
