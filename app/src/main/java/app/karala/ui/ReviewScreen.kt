package app.karala.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.karala.data.Card
import app.karala.data.Deck
import app.karala.data.Export
import app.karala.data.Mistake
import app.karala.data.Subject
import app.karala.ink.InkRenderer
import app.karala.overlay.Icon as KI
import app.karala.study.Srs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val REASONS = listOf("Bilgi eksiği", "Dikkat hatası", "İşlem hatası", "Süre yetmedi", "Soruyu anlamadım")

@Composable
fun ReviewScreen(nav: NavController) {
    var tab by remember { mutableIntStateOf(0) }
    Screen("Tekrar") { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            KTabs(listOf("Hatalı sorular", "Bilgi kartları"), tab, Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)) { tab = it }
            if (tab == 0) MistakesTab(nav) else DecksTab(nav)
        }
    }
}

// ---------------- Hatalı sorular ----------------

@Composable
private fun MistakesTab(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val subjects by repo.dao.subjects().collectAsStateWithLifecycle(emptyList())
    val mistakes by repo.dao.mistakes().collectAsStateWithLifecycle(emptyList())
    var filter by remember { mutableStateOf<Long?>(null) }
    val now = remember(mistakes) { System.currentTimeMillis() }
    val due = mistakes.count { !it.learned && it.dueAt <= now }
    val shown = mistakes.filter { filter == null || it.subjectId == filter }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = Export.decodeImage(ctx, uri) ?: return@launch
            val id = repo.saveCaptureAsMistake(filter, "", 0, app.karala.ink.InkPage(), bmp)
            nav.navigate("mistake/$id")
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (due > 0) "$due soru tekrar bekliyor" else "Bugünlük tekrar yok 🎉", fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("Yanlış yaptığın sorular unutmadan önce yeniden sorulur.", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            if (due > 0) Button({ nav.navigate("mistakeReview") }) { Text("Başla") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SubjectPicker(subjects, filter, { filter = it }, noneLabel = "Tümü")
                }
            }
            if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Empty("Hatalı soru defterin boş", "Ekran kaleminde \"Hatalı soru olarak kaydet\"e bas ya da kitaptaki sorunun fotoğrafını ekle.")
            }
            items(shown, key = { it.id }) { m ->
                val s = subjects.byId(m.subjectId)
                KCard(
                    modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { nav.navigate("mistake/${m.id}") }) {
                    Column {
                        PageThumb(m.pageId, m.dueAt + m.reps, Modifier.fillMaxWidth().aspectRatio(1.35f).padding(6.dp))
                        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SubjectDot(s, 8); Spacer(Modifier.width(6.dp))
                                Text(m.topic.ifBlank { s?.name ?: "Genel" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            val (label, color) = when {
                                m.learned -> "Öğrenildi ✓" to Good
                                m.dueAt <= now -> "Tekrar zamanı" to MaterialTheme.colorScheme.error
                                else -> "Tekrar: ${relativeDay(m.dueAt, now)}" to MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text("${REASONS.getOrElse(m.reason) { "" }} · $label", style = MaterialTheme.typography.bodySmall, color = color,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            icon = { Icon(Icons.Rounded.AddPhotoAlternate, null) }, text = { Text("Fotoğraftan ekle") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

@Composable
fun MistakeDetailScreen(id: Long, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val subjects by repo.dao.subjects().collectAsStateWithLifecycle(emptyList())
    var m by remember { mutableStateOf<Mistake?>(null) }
    var showInk by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(id) { m = repo.dao.mistakeNow(id) }
    val cur = m

    Screen(cur?.topic?.ifBlank { null } ?: subjects.byId(cur?.subjectId)?.name ?: "Hatalı soru", onBack, actions = {
        IconButton({ showInk = !showInk }) { KIcon(if (showInk) KI.EYE else KI.EYE_OFF) }
        IconButton({ info = true }) { Icon(Icons.Rounded.Edit, "Bilgileri düzenle") }
        IconButton({ deleting = true }) { Icon(Icons.Rounded.DeleteOutline, "Sil") }
    }) { pad ->
        if (cur == null) { Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Screen }
        InkPane(cur.pageId, Modifier.padding(pad).fillMaxSize(), inkVisible = showInk)
    }

    if (info && cur != null) MistakeInfoDialog(cur, subjects, { info = false }) { updated ->
        m = updated; scope.launch { repo.dao.update(updated) }
    }
    if (deleting && cur != null) ConfirmDialog("Soru silinsin mi?", "Soru ve çözümün kalıcı olarak silinecek.",
        onConfirm = { scope.launch { repo.deleteMistake(cur); onBack() } }, onDismiss = { deleting = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MistakeInfoDialog(m: Mistake, subjects: List<Subject>, onDismiss: () -> Unit, onSave: (Mistake) -> Unit) {
    var topic by remember { mutableStateOf(m.topic) }
    var note by remember { mutableStateOf(m.note) }
    var subject by remember { mutableStateOf(m.subjectId) }
    var reason by remember { mutableIntStateOf(m.reason) }
    var learned by remember { mutableStateOf(m.learned) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Soru bilgileri") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SubjectPicker(subjects, subject, { subject = it })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(topic, { topic = it }, label = { Text("Konu (ör. Türev)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text("Neden yanlış yaptım?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    REASONS.forEachIndexed { i, r -> FilterChip(reason == i, { reason = i }, { Text(r) }) }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Kendime not") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(learned, { learned = it }); Text("Artık öğrendim (tekrar sorma)")
                }
            }
        },
        confirmButton = {
            TextButton({ onSave(m.copy(topic = topic.trim(), note = note.trim(), subjectId = subject, reason = reason, learned = learned)); onDismiss() }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Vazgeç") } },
    )
}

/** Sayfayı (isteğe bağlı çözümsüz) salt-okunur görsel olarak gösterir. */
@Composable
private fun PageImage(pageId: Long, withInk: Boolean, modifier: Modifier = Modifier) {
    var bmp by remember(pageId, withInk) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(pageId, withInk) {
        bmp = withContext(Dispatchers.IO) {
            val ink = repo.loadInk(pageId); val bg = repo.loadBg(pageId)
            InkRenderer().renderPage(ink, bg, 1400, withInk = withInk).also { bg?.recycle() }
        }
    }
    Box(modifier.clip(MaterialTheme.shapes.medium).background(Color(InkRenderer.BOARD_BLACK)), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } ?: CircularProgressIndicator()
    }
}

@Composable
private fun GradeButtons(state: Srs.State, labels: List<String>, onGrade: (Srs.Grade) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val colors = listOf(Bad, Color(0xFFE39A3B), Good, Color(0xFF3D8BFF))
        Srs.Grade.entries.forEachIndexed { i, g ->
            Button({ onGrade(g) }, Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors[i], contentColor = Color.White)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(labels[i], maxLines = 1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(Srs.preview(state, g), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ReviewDone(count: Int, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎉", fontSize = 56.sp)
        Text(if (count == 0) "Tekrar edilecek bir şey yok" else "$count tekrar tamamlandı", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Sıradaki tekrarlar zamanı gelince burada görünecek.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onBack) { Text("Bitir") }
    }
}

@Composable
fun MistakeReviewScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val subjects by repo.dao.subjects().collectAsStateWithLifecycle(emptyList())
    var queue by remember { mutableStateOf<List<Mistake>?>(null) }
    var pos by remember { mutableIntStateOf(0) }
    var reveal by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { queue = repo.dao.dueMistakes(System.currentTimeMillis()) }
    val q = queue

    Screen(if (q != null && pos < q.size) "Tekrar ${pos + 1}/${q.size}" else "Tekrar", onBack) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                q == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                pos >= q.size -> ReviewDone(q.size, onBack)
                else -> {
                    val m = q[pos]
                    Column(Modifier.fillMaxSize().padding(16.dp).navigationBarsPadding()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SubjectDot(subjects.byId(m.subjectId)); Spacer(Modifier.width(8.dp))
                            Text(listOfNotNull(subjects.byId(m.subjectId)?.name, m.topic.ifBlank { null }, REASONS.getOrNull(m.reason)).joinToString(" · "),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(10.dp))
                        PageImage(m.pageId, reveal, Modifier.weight(1f).fillMaxWidth())
                        if (reveal && m.note.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Notum: ${m.note}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        if (!reveal) {
                            Text("Önce kâğıtta ya da kafanda yeniden çöz, sonra çözümüne bak.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Button({ reveal = true }, Modifier.fillMaxWidth()) { Text("Çözümü göster") }
                        } else {
                            GradeButtons(Srs.State(m.intervalDays, m.ease, m.reps, m.lapses), listOf("Çözemedim", "Zorlandım", "Çözdüm", "Çok kolay")) { g ->
                                scope.launch { repo.reviewMistake(m, g) }
                                reveal = false; pos++
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Bilgi kartları ----------------

@Composable
private fun DecksTab(nav: NavController) {
    val scope = rememberCoroutineScope()
    val subjects by repo.dao.subjects().collectAsStateWithLifecycle(emptyList())
    val now = remember { System.currentTimeMillis() }
    val decks by repo.dao.decks(now).collectAsStateWithLifecycle(emptyList())
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (decks.isEmpty()) item {
                Empty("Henüz kart desten yok", "Formül, tarih, kelime… Ezberlenecek her şey için deste aç; Karala doğru zamanda yeniden sorar.")
            }
            items(decks, key = { it.id }) { d ->
                val s = subjects.byId(d.subjectId)
                KCard(
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { nav.navigate("deck/${d.id}") }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SubjectDot(s, 8); Spacer(Modifier.width(6.dp))
                                Text("${s?.name ?: "Genel"} · ${d.total} kart", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (d.due > 0) Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("${d.due}", Modifier.padding(4.dp)) }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Yeni deste") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
    if (creating) DeckDialog(null, subjects, { creating = false }) { name, subjectId ->
        scope.launch { val id = repo.dao.insert(Deck(subjectId = subjectId, name = name, createdAt = System.currentTimeMillis())); nav.navigate("deck/$id") }
    }
}

@Composable
private fun DeckDialog(deck: Deck?, subjects: List<Subject>, onDismiss: () -> Unit, onSave: (String, Long?) -> Unit) {
    var name by remember { mutableStateOf(deck?.name ?: "") }
    var subject by remember { mutableStateOf(deck?.subjectId) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (deck == null) "Yeni deste" else "Desteyi düzenle") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Deste adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp)); SubjectPicker(subjects, subject, { subject = it })
            }
        },
        confirmButton = { TextButton({ onSave(name.trim(), subject); onDismiss() }, enabled = name.isNotBlank()) { Text("Kaydet") } },
        dismissButton = { TextButton(onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun DeckScreen(deckId: Long, nav: NavController) {
    val scope = rememberCoroutineScope()
    val subjects by repo.dao.subjects().collectAsStateWithLifecycle(emptyList())
    val deck by repo.dao.deck(deckId).collectAsStateWithLifecycle(null)
    val cards by repo.dao.cards(deckId).collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Card?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editDeck by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val now = remember(cards) { System.currentTimeMillis() }
    val due = cards.count { it.dueAt <= now }

    Screen(deck?.name ?: "", { nav.popBackStack() }, actions = {
        IconButton({ editDeck = true }) { Icon(Icons.Rounded.Edit, "Desteyi düzenle") }
        IconButton({ deleting = true }) { Icon(Icons.Rounded.DeleteOutline, "Desteyi sil") }
    }, fab = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Kart ekle") }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button({ nav.navigate("cards/$deckId/false") }, Modifier.weight(1f), enabled = due > 0) { Text(if (due > 0) "Tekrar et ($due)" else "Tekrar yok") }
                    OutlinedButton({ nav.navigate("cards/$deckId/true") }, Modifier.weight(1f), enabled = cards.isNotEmpty()) { Text("Hepsini çalış") }
                }
            }
            if (cards.isEmpty()) item { Empty("Bu destede kart yok", "Ön yüze soruyu, arka yüze cevabı yaz.") }
            items(cards, key = { it.id }) { c ->
                KCard(
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { editing = c }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(c.front, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(c.back, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (adding || editing != null) CardDialog(editing, { adding = false; editing = null },
        onDelete = { c -> scope.launch { repo.dao.deleteCard(c.id) } }) { front, back ->
        val e = editing
        scope.launch {
            val t = System.currentTimeMillis()
            if (e == null) repo.dao.insert(Card(deckId = deckId, front = front, back = back, createdAt = t, dueAt = t))
            else repo.dao.update(e.copy(front = front, back = back))
        }
    }
    if (editDeck) DeckDialog(deck, subjects, { editDeck = false }) { name, sid -> scope.launch { deck?.let { repo.dao.update(it.copy(name = name, subjectId = sid)) } } }
    if (deleting) ConfirmDialog("Deste silinsin mi?", "Destedeki ${cards.size} kart kalıcı olarak silinecek.",
        onConfirm = { scope.launch { repo.dao.deleteDeck(deckId); nav.popBackStack() } }, onDismiss = { deleting = false })
}

@Composable
private fun CardDialog(card: Card?, onDismiss: () -> Unit, onDelete: (Card) -> Unit, onSave: (String, String) -> Unit) {
    var front by remember { mutableStateOf(card?.front ?: "") }
    var back by remember { mutableStateOf(card?.back ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (card == null) "Yeni kart" else "Kartı düzenle") },
        text = {
            Column {
                OutlinedTextField(front, { front = it }, label = { Text("Ön yüz (soru)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(back, { back = it }, label = { Text("Arka yüz (cevap)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onSave(front.trim(), back.trim()); onDismiss() }, enabled = front.isNotBlank() && back.isNotBlank()) { Text("Kaydet") } },
        dismissButton = {
            Row {
                if (card != null) TextButton({ onDelete(card); onDismiss() }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                TextButton(onDismiss) { Text("Vazgeç") }
            }
        },
    )
}

@Composable
fun CardReviewScreen(deckId: Long, all: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var queue by remember { mutableStateOf<List<Card>?>(null) }
    var pos by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        queue = if (all) repo.dao.allCardsShuffled(deckId) else repo.dao.dueCards(deckId, System.currentTimeMillis())
    }
    val q = queue
    Screen(if (q != null && pos < q.size) "Kart ${pos + 1}/${q.size}" else "Kartlar", onBack) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                q == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                pos >= q.size -> ReviewDone(q.size, onBack)
                else -> {
                    val c = q[pos]
                    Column(Modifier.fillMaxSize().padding(16.dp).navigationBarsPadding()) {
                        LinearProgressIndicator({ pos / q.size.toFloat() }, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Surface(shape = MaterialTheme.shapes.large,
                            color = if (flipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.large).clickable { flipped = !flipped }) {
                            Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (flipped) "CEVAP" else "SORU", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                Text(if (flipped) c.back else c.front, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                                if (!flipped) {
                                    Spacer(Modifier.height(20.dp))
                                    Text("Cevabı görmek için dokun", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (!flipped) Button({ flipped = true }, Modifier.fillMaxWidth()) { Text("Cevabı göster") }
                        else GradeButtons(Srs.State(c.intervalDays, c.ease, c.reps, c.lapses), listOf("Bilemedim", "Zor", "Bildim", "Kolay")) { g ->
                            scope.launch { repo.reviewCard(c, g) }
                            flipped = false; pos++
                        }
                    }
                }
            }
        }
    }
}
