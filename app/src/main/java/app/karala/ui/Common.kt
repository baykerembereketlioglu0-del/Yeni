package app.karala.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.karala.App
import app.karala.data.Subject
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val TR: Locale = Locale("tr")
fun fmt(ms: Long, pattern: String): String = SimpleDateFormat(pattern, TR).format(Date(ms))
val repo get() = App.instance.repo
val prefs get() = App.instance.prefs

fun List<Subject>.byId(id: Long?): Subject? = if (id == null) null else firstOrNull { it.id == id }
fun Subject?.tint(): Color = if (this == null) Color(0xFF8A93A3) else Color(color)

/** "3 gün sonra", "bugün", "2 gün gecikti" */
fun relativeDay(ms: Long, now: Long = System.currentTimeMillis()): String {
    val a = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val b = Calendar.getInstance().apply { timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val d = Math.round((b.timeInMillis - a.timeInMillis) / 86_400_000.0).toInt()
    return when {
        d == 0 -> "bugün"; d == 1 -> "yarın"; d == -1 -> "dün"
        d > 0 -> "$d gün sonra"; else -> "${-d} gün gecikti"
    }
}

fun minutesText(min: Int): String = if (min >= 60) "${min / 60} sa ${min % 60} dk" else "$min dk"
fun hm(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = fab,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Empty(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun SubjectDot(subject: Subject?, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(subject.tint()))
}

/** Ders seçici. null = "Genel". */
@Composable
fun SubjectPicker(subjects: List<Subject>, selected: Long?, onSelect: (Long?) -> Unit, modifier: Modifier = Modifier, noneLabel: String = "Genel") {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(selected == null, { onSelect(null) }, { Text(noneLabel) }) }
        items(subjects, key = { it.id }) { s ->
            FilterChip(selected == s.id, { onSelect(s.id) }, { Text(s.name) }, leadingIcon = { SubjectDot(s) })
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Sil", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton({ onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun InputDialog(title: String, initial: String = "", label: String = "", confirm: String = "Kaydet", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, label = { if (label.isNotEmpty()) Text(label) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton({ onConfirm(text.trim()); onDismiss() }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(value, { v -> onChange(v.filter { it.isDigit() }.take(4)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier)
}

fun pickDate(ctx: Context, initial: Long, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(ctx, { _, y, m, d ->
        c.set(y, m, d); onPicked(c.timeInMillis)
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
}

fun pickTime(ctx: Context, initialMinuteOfDay: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPicked(h * 60 + m) }, initialMinuteOfDay / 60, initialMinuteOfDay % 60, true).show()
}

fun pickDateTime(ctx: Context, initial: Long, onPicked: (Long) -> Unit) {
    pickDate(ctx, initial) { day ->
        val c = Calendar.getInstance().apply { timeInMillis = day }
        val init = Calendar.getInstance().apply { timeInMillis = initial }
        pickTime(ctx, init.get(Calendar.HOUR_OF_DAY) * 60 + init.get(Calendar.MINUTE)) { mins ->
            c.set(Calendar.HOUR_OF_DAY, mins / 60); c.set(Calendar.MINUTE, mins % 60); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            onPicked(c.timeInMillis)
        }
    }
}

/** Sayfa küçük resmi. [version] değişince yeniden yüklenir. */
@Composable
fun PageThumb(pageId: Long?, version: Long, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val shape = RoundedCornerShape(12.dp)
    if (pageId == null) { Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)); return }
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(repo.files.thumb(pageId))
            .memoryCacheKey("thumb-$pageId-$version").diskCachePolicy(CachePolicy.DISABLED).crossfade(true).build(),
        contentDescription = null, contentScale = contentScale,
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun GoalBar(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = color, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, gapSize = 0.dp, drawStopIndicator = {})
}
