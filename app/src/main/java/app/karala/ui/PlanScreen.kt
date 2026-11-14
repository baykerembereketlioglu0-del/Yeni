package app.karala.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.karala.data.Repo
import app.karala.data.ScheduleEntry
import app.karala.data.Subject
import app.karala.data.Task
import app.karala.study.Reminders
import kotlinx.coroutines.launch

private val TASK_TYPES = listOf("Ödev", "Sınav", "Çalışma")
private val REMINDERS = listOf(-1 to "Hatırlatma yok", 0 to "Tam zamanında", 15 to "15 dk önce", 60 to "1 saat önce", 1440 to "1 gün önce", 4320 to "3 gün önce")
val DAYS = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

@Composable
fun PlanScreen() {
    var tab by remember { mutableIntStateOf(0) }
    Screen("Plan") { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Ödev ve sınavlar") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Ders programı") })
            }
            if (tab == 0) TasksTab() else ScheduleTab()
        }
    }
}

@Composable
private fun TasksTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val subjects by repo.dao.subjects().collectAsStateWithLifecycle(emptyList())
    val tasks by repo.dao.tasks().collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Task?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    val now = remember(tasks) { System.currentTimeMillis() }
    val tomorrow = remember(tasks) { Repo.startOfDay(1) }
    val open = tasks.filter { !it.done }
    val groups = listOf(
        "Gecikenler" to open.filter { it.dueAt < now },
        "Bugün" to open.filter { it.dueAt in now until tomorrow },
        "Yaklaşanlar" to open.filter { it.dueAt >= tomorrow },
    )
    val done = tasks.filter { it.done }.sortedByDescending { it.doneAt ?: 0 }

    fun toggle(t: Task) = scope.launch {
        val u = t.copy(done = !t.done, doneAt = if (!t.done) System.currentTimeMillis() else null)
        repo.dao.update(u); Reminders.schedule(ctx, u)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            if (tasks.isEmpty()) item { Empty("Planın boş", "Ödevlerini ve sınav tarihlerini ekle; Karala geri sayımı tutsun, zamanı gelince hatırlatsın.") }
            groups.forEach { (title, list) ->
                if (list.isNotEmpty()) {
                    item(key = title) { SectionTitle(title) }
                    items(list, key = { it.id }) { t -> TaskRow(t, subjects, now, { toggle(t) }) { editing = t } }
                }
            }
            if (done.isNotEmpty()) {
                item(key = "done") {
                    SectionTitle("Tamamlananlar (${done.size})") { TextButton({ showDone = !showDone }) { Text(if (showDone) "Gizle" else "Göster") } }
                }
                if (showDone) items(done, key = { it.id }) { t -> TaskRow(t, subjects, now, { toggle(t) }) { editing = t } }
            }
        }
        ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Ekle") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }

    if (adding || editing != null) TaskDialog(editing, subjects, { adding = false; editing = null },
        onDelete = { t -> scope.launch { Reminders.cancel(ctx, t.id); repo.dao.deleteTask(t.id) } }) { t ->
        scope.launch {
            val saved = if (t.id == 0L) t.copy(id = repo.dao.insert(t)) else t.also { repo.dao.update(it) }
            Reminders.schedule(ctx, saved)
        }
    }
}

@Composable
private fun TaskRow(t: Task, subjects: List<Subject>, now: Long, onToggle: () -> Unit, onClick: () -> Unit) {
    val s = subjects.byId(t.subjectId)
    val late = !t.done && t.dueAt < now
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        Row(Modifier.padding(end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(t.done, { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(t.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubjectDot(s, 8); Spacer(Modifier.width(6.dp))
                    Text("${TASK_TYPES.getOrElse(t.type) { "" }}${s?.let { " · ${it.name}" } ?: ""} · ${fmt(t.dueAt, "d MMM EEE HH:mm")}",
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!t.done) Text(relativeDay(t.dueAt, now), style = MaterialTheme.typography.labelMedium,
                color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            if (t.remindMinutes >= 0 && !t.done) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.NotificationsActive, "Hatırlatma açık", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDialog(task: Task?, subjects: List<Subject>, onDismiss: () -> Unit, onDelete: (Task) -> Unit, onSave: (Task) -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(task?.title ?: "") }
    var type by remember { mutableIntStateOf(task?.type ?: 0) }
    var subject by remember { mutableStateOf(task?.subjectId) }
    var dueAt by remember { mutableLongStateOf(task?.dueAt ?: (Repo.startOfDay(1) + 17 * 3_600_000L)) }
    var remind by remember { mutableIntStateOf(task?.remindMinutes ?: 60) }
    var note by remember { mutableStateOf(task?.note ?: "") }
    var remindMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (task == null) "Yeni görev" else "Görevi düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Başlık") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TASK_TYPES.forEachIndexed { i, name ->
                        SegmentedButton(type == i, { type = i }, SegmentedButtonDefaults.itemShape(i, TASK_TYPES.size)) { Text(name) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SubjectPicker(subjects, subject, { subject = it })
                Spacer(Modifier.height(6.dp))
                OutlinedButton({ pickDateTime(ctx, dueAt) { dueAt = it } }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Event, null); Spacer(Modifier.width(8.dp)); Text(fmt(dueAt, "d MMMM EEEE · HH:mm"))
                }
                Box {
                    OutlinedButton({ remindMenu = true }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Notifications, null); Spacer(Modifier.width(8.dp))
                        Text(REMINDERS.firstOrNull { it.first == remind }?.second ?: "$remind dk önce")
                    }
                    DropdownMenu(remindMenu, { remindMenu = false }) {
                        REMINDERS.forEach { (m, name) -> DropdownMenuItem({ Text(name) }, { remind = m; remindMenu = false }) }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Not (isteğe bağlı)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton({
                onSave((task ?: Task(title = "", dueAt = 0)).copy(title = title.trim(), type = type, subjectId = subject, dueAt = dueAt, remindMinutes = remind, note = note.trim()))
                onDismiss()
            }, enabled = title.isNotBlank()) { Text("Kaydet") }
        },
        dismissButton = {
            Row {
                if (task != null) TextButton({ onDelete(task); onDismiss() }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                TextButton(onDismiss) { Text("Vazgeç") }
            }
        },
    )
}

// ---------------- Ders programı ----------------

@Composable
private fun ScheduleTab() {
    val scope = rememberCoroutineScope()
    val subjects by repo.dao.subjects().collectAsStateWithLifecycle(emptyList())
    val entries by repo.dao.schedule().collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<ScheduleEntry?>(null) }
    var adding by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            if (entries.isEmpty()) item { Empty("Ders programın boş", "Okul, kurs ya da kendi çalışma saatlerini ekle; bugünün dersleri ana sayfada görünsün.") }
            for (d in 1..7) {
                val list = entries.filter { it.dayOfWeek == d }
                if (list.isEmpty()) continue
                item(key = "d$d") { SectionTitle(DAYS[d - 1]) }
                items(list, key = { it.id }) { e ->
                    val s = subjects.byId(e.subjectId)
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(MaterialTheme.shapes.medium).clickable { editing = e }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${hm(e.startMinute)}\n${hm(e.endMinute)}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp))
                            SubjectDot(s, 12); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.title.ifBlank { s?.name ?: "Ders" }, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (e.place.isNotBlank()) Text(e.place, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Ders ekle") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
    if (adding || editing != null) ScheduleDialog(editing, subjects, { adding = false; editing = null },
        onDelete = { e -> scope.launch { repo.dao.deleteSchedule(e.id) } }) { e ->
        scope.launch { if (e.id == 0L) repo.dao.insert(e) else repo.dao.update(e) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDialog(entry: ScheduleEntry?, subjects: List<Subject>, onDismiss: () -> Unit, onDelete: (ScheduleEntry) -> Unit, onSave: (ScheduleEntry) -> Unit) {
    val ctx = LocalContext.current
    var day by remember { mutableIntStateOf(entry?.dayOfWeek ?: 1) }
    var start by remember { mutableIntStateOf(entry?.startMinute ?: 9 * 60) }
    var end by remember { mutableIntStateOf(entry?.endMinute ?: 9 * 60 + 40) }
    var subject by remember { mutableStateOf(entry?.subjectId) }
    var title by remember { mutableStateOf(entry?.title ?: "") }
    var place by remember { mutableStateOf(entry?.place ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (entry == null) "Ders ekle" else "Dersi düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAYS.forEachIndexed { i, d -> FilterChip(day == i + 1, { day = i + 1 }, { Text(d.take(3)) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ pickTime(ctx, start) { start = it; if (end <= it) end = (it + 40).coerceAtMost(1439) } }, Modifier.weight(1f)) { Text("Başlangıç ${hm(start)}") }
                    OutlinedButton({ pickTime(ctx, end) { end = it } }, Modifier.weight(1f)) { Text("Bitiş ${hm(end)}") }
                }
                Spacer(Modifier.height(6.dp))
                SubjectPicker(subjects, subject, { subject = it })
                OutlinedTextField(title, { title = it }, label = { Text("Başlık (isteğe bağlı)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(place, { place = it }, label = { Text("Yer / derslik") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (end <= start) Text("Bitiş saati başlangıçtan sonra olmalı.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton({
                onSave((entry ?: ScheduleEntry(dayOfWeek = 1, startMinute = 0, endMinute = 0)).copy(dayOfWeek = day, startMinute = start, endMinute = end,
                    subjectId = subject, title = title.trim(), place = place.trim()))
                onDismiss()
            }, enabled = end > start) { Text("Kaydet") }
        },
        dismissButton = {
            Row {
                if (entry != null) TextButton({ onDelete(entry); onDismiss() }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                TextButton(onDismiss) { Text("Vazgeç") }
            }
        },
    )
}
