package app.karala.ui

import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.karala.BuildConfig
import app.karala.MainActivity
import app.karala.data.KaralaDb
import app.karala.data.Subject
import kotlinx.coroutines.launch

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onTheme: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val subjects by repo.dao.subjects().collectAsStateWithLifecycle(emptyList())
    var theme by remember { mutableIntStateOf(prefs.theme) }
    var stylusOnly by remember { mutableStateOf(prefs.stylusOnly) }
    var shapeAssist by remember { mutableStateOf(prefs.shapeAssist) }
    var shot by remember { mutableStateOf(prefs.captureWithScreenshot) }
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var exactOk by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<Subject?>(null) }
    var adding by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canOverlay = Settings.canDrawOverlays(ctx)
        exactOk = Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    Screen("Ayarlar", onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            SectionTitle("Görünüm")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Sistem", "Açık", "Koyu").forEachIndexed { i, name ->
                    SegmentedButton(theme == i, { theme = i; onTheme(i) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(name) }
                }
            }

            SectionTitle("Ekran kalemi")
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Diğer uygulamaların üzerinde göster")
                    Text(if (canOverlay) "İzin verildi" else "Ekran kalemi için gerekli", style = MaterialTheme.typography.bodySmall,
                        color = if (canOverlay) Good else MaterialTheme.colorScheme.error)
                }
                if (!canOverlay) Button({ runCatching { ctx.startActivity(MainActivity.overlayPermissionIntent(ctx)) } }) { Text("İzin ver") }
            }
            SwitchRow("Kaydederken ekran görüntüsü al", "Soru, çözümünle birlikte deftere girer. Oturum başına bir kez sistem izni istenir.", shot) { shot = it; prefs.captureWithScreenshot = it }
            SwitchRow("Yalnızca kalem ucuyla çiz", "Kalemli tabletlerde avuç içi ve parmak dokunuşlarını yok sayar.", stylusOnly) { stylusOnly = it; prefs.stylusOnly = it }
            SwitchRow("Basılı tut → şekle çevir", "Çizgiyi bitirmeden kalemi sabit tut: doğru, dikdörtgen ya da elipse düzelir.", shapeAssist) { shapeAssist = it; prefs.shapeAssist = it }

            SectionTitle("Günlük hedefler")
            Stepper("Çalışma süresi", prefs.dailyMinuteGoal, 15, 720, 15) { prefs.dailyMinuteGoal = it }
            Stepper("Soru sayısı", prefs.dailyQuestionGoal, 10, 1000, 10, unit = " soru") { prefs.dailyQuestionGoal = it }

            if (!exactOk) {
                SectionTitle("Bildirimler")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tam zamanında alarm")
                        Text("Kapalıyken hatırlatmalar ve pomodoro bitişi birkaç dakika gecikebilir.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (Build.VERSION.SDK_INT >= 31) TextButton({
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
                    }) { Text("Aç") }
                }
            }

            SectionTitle("Dersler") { TextButton({ adding = true }) { Icon(Icons.Rounded.Add, null); Text("Ekle") } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                subjects.forEach { s -> AssistChip({ editing = s }, { Text(s.name) }, leadingIcon = { SubjectDot(s) }) }
            }
            Text("Düzenlemek ya da silmek için derse dokun.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionTitle("Hakkında")
            Text("Karala ${BuildConfig.VERSION_NAME}")
            Text("Reklam yok, hesap yok. Tüm notların ve kayıtların yalnızca bu cihazda saklanır.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (adding || editing != null) SubjectDialog(editing, { adding = false; editing = null },
        onDelete = { s -> scope.launch { repo.dao.removeSubject(s.id) } }) { name, color ->
        val e = editing
        scope.launch {
            if (e == null) repo.dao.insert(Subject(name = name, color = color, position = subjects.size))
            else repo.dao.update(e.copy(name = name, color = color))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectDialog(subject: Subject?, onDismiss: () -> Unit, onDelete: (Subject) -> Unit, onSave: (String, Int) -> Unit) {
    val colors = remember { KaralaDb.DEFAULT_SUBJECTS.map { it.second.toInt() } + listOf(0xFF8A93A3.toInt(), 0xFFFF7043.toInt()) }
    var name by remember { mutableStateOf(subject?.name ?: "") }
    var color by remember { mutableIntStateOf(subject?.color ?: colors[0]) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (subject == null) "Yeni ders" else "Dersi düzenle") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Ders adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    colors.forEach { c ->
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                            .border(if (c == color) 3.dp else 0.dp, if (c == color) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                            .clickable { color = c })
                    }
                }
                if (subject != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Silersen bu derse ait defterler ve kayıtlar silinmez, \"Genel\"e taşınır.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton({ onSave(name.trim(), color); onDismiss() }, enabled = name.isNotBlank()) { Text("Kaydet") } },
        dismissButton = {
            Row {
                if (subject != null) TextButton({ onDelete(subject); onDismiss() }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                TextButton(onDismiss) { Text("Vazgeç") }
            }
        },
    )
}
