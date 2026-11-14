package app.karala.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karala.study.Reference

@Composable
fun FormulasScreen(onBack: () -> Unit) {
    var course by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase(TR)
    Screen("Formül kitapçığı", onBack) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Formül ara (ör. türev, pH, alan)") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                if (q.isEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Reference.courses.forEachIndexed { i, c -> FilterChip(course == i, { course = i }, { Text(c.name) }) }
                }
            }
            val courses = if (q.isEmpty()) listOf(Reference.courses[course]) else Reference.courses
            var any = false
            courses.forEach { c ->
                c.topics.forEach { t ->
                    val list = if (q.isEmpty()) t.formulas else t.formulas.filter {
                        it.title.lowercase(TR).contains(q) || it.body.lowercase(TR).contains(q) || t.name.lowercase(TR).contains(q)
                    }
                    if (list.isNotEmpty()) {
                        any = true
                        item(key = "${c.name}/${t.name}") { SectionTitle(if (q.isEmpty()) t.name else "${c.name} · ${t.name}") }
                        items(list.size, key = { "${c.name}/${t.name}/$it/${list[it].title}" }) { i ->
                            val f = list[i]
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(f.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(f.body, fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 22.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (!any) item { Empty("Sonuç yok", "Başka bir kelime dene.") }
        }
    }
}

@Composable
fun PeriodicScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf<Reference.Element?>(null) }
    val cell = 46.dp
    val grid = remember { Reference.elements.associateBy { it.row to it.col } }
    Screen("Periyodik tablo", onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                for (r in 0..8) {
                    if (r == 7) Spacer(Modifier.height(10.dp))
                    Row {
                        for (c in 0..17) {
                            val e = grid[r to c]
                            if (e == null) Spacer(Modifier.size(cell)) else {
                                val col = Color(e.category.color)
                                Box(Modifier.size(cell).padding(1.5.dp).clip(RoundedCornerShape(6.dp)).background(col.copy(alpha = if (selected == e) 1f else 0.82f))
                                    .border(if (selected == e) 2.dp else 0.dp, if (selected == e) MaterialTheme.colorScheme.onSurface else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { selected = e }, contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${e.z}", fontSize = 8.sp, lineHeight = 9.sp, color = Color(0xFF1B1E26))
                                        Text(e.symbol, fontSize = 15.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1E26))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.padding(16.dp)) {
                val e = selected
                if (e == null) Text("Ayrıntı için bir elemente dokun.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(Color(e.category.color)), contentAlignment = Alignment.Center) {
                            Text(e.symbol, fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B1E26))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(e.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Atom numarası: ${e.z}")
                            Text("Atom kütlesi: ${e.mass.replace('.', ',')} u")
                            Text("${e.period}. periyot" + if (e.group > 0) " · ${e.group}. grup" else "")
                            Text(e.category.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                SectionTitle("Renkler")
                Reference.Category.entries.chunked(2).forEach { pair ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        pair.forEach { c ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(Color(c.color)))
                                Spacer(Modifier.width(8.dp)); Text(c.label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
