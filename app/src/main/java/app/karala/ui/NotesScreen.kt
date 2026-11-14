package app.karala.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.karala.data.Export
import app.karala.data.NoteWithMeta
import app.karala.overlay.Icon as KI
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val subjects by repo.dao.subjects().collectAsStateWithLifecycle(emptyList())
    val notes by repo.dao.notes().collectAsStateWithLifecycle(emptyList())
    var filter by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<NoteWithMeta?>(null) }
    var renaming by remember { mutableStateOf<NoteWithMeta?>(null) }
    var deleting by remember { mutableStateOf<NoteWithMeta?>(null) }
    val shown = notes.filter { filter == null || it.subjectId == filter }

    Screen("Defterler", fab = {
        ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Yeni defter") })
    }) { pad ->
        LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { SubjectPicker(subjects, filter, { filter = it }, noneLabel = "Tümü") }
            if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Empty("Henüz defter yok", "Ekran kalemiyle kaydettiğin çözümler burada derse göre toplanır. İstersen boş bir defter de açabilirsin.")
            }
            items(shown, key = { it.id }) { n ->
                val s = subjects.byId(n.subjectId)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.clip(MaterialTheme.shapes.medium)
                        .combinedClickable(onClick = { nav.navigate("note/${n.id}") }, onLongClick = { menuFor = n })) {
                    Column {
                        Box {
                            PageThumb(n.firstPageId, n.updatedAt, Modifier.fillMaxWidth().aspectRatio(1.35f).padding(6.dp))
                            if (n.pinned) Icon(Icons.Rounded.PushPin, "Sabitlendi", tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(18.dp))
                            DropdownMenu(menuFor?.id == n.id, { menuFor = null }) {
                                DropdownMenuItem({ Text(if (n.pinned) "Sabitlemeyi kaldır" else "Sabitle") }, {
                                    menuFor = null
                                    scope.launch { repo.dao.noteNow(n.id)?.let { repo.dao.update(it.copy(pinned = !it.pinned)) } }
                                })
                                DropdownMenuItem({ Text("Yeniden adlandır") }, { renaming = n; menuFor = null })
                                DropdownMenuItem({ Text("Sil", color = MaterialTheme.colorScheme.error) }, { deleting = n; menuFor = null })
                            }
                        }
                        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 2.dp)) {
                            Text(n.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SubjectDot(s, 8); Spacer(Modifier.width(6.dp))
                                Text("${s?.name ?: "Genel"} · ${n.pageCount} sayfa", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) NewNoteDialog(subjects, filter, { creating = false }) { title, subjectId ->
        scope.launch {
            val id = repo.createNote(title, subjectId)
            repo.addPage(id)
            nav.navigate("note/$id")
        }
    }
    renaming?.let { n ->
        InputDialog("Defteri yeniden adlandır", n.title, onConfirm = { t ->
            scope.launch { repo.dao.noteNow(n.id)?.let { repo.dao.update(it.copy(title = t)) } }
        }, onDismiss = { renaming = null })
    }
    deleting?.let { n ->
        ConfirmDialog("Defter silinsin mi?", "\"${n.title}\" ve ${n.pageCount} sayfası kalıcı olarak silinecek.",
            onConfirm = { scope.launch { repo.deleteNote(n.id) } }, onDismiss = { deleting = null })
    }
}

@Composable
private fun NewNoteDialog(subjects: List<app.karala.data.Subject>, initialSubject: Long?, onDismiss: () -> Unit, onCreate: (String, Long?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(initialSubject) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Yeni defter") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Başlık") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SubjectPicker(subjects, subject, { subject = it })
            }
        },
        confirmButton = { TextButton({ onCreate(title.trim().ifBlank { "Adsız defter" }, subject); onDismiss() }) { Text("Oluştur") } },
        dismissButton = { TextButton(onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun NoteEditorScreen(noteId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val note by repo.dao.note(noteId).collectAsStateWithLifecycle(null)
    val pages by repo.dao.pages(noteId).collectAsStateWithLifecycle(null)
    val subjects by repo.dao.subjects().collectAsStateWithLifecycle(emptyList())
    var index by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var subjectDialog by remember { mutableStateOf(false) }
    var deletePage by remember { mutableStateOf(false) }
    var goLast by remember { mutableStateOf(false) }

    val list = pages
    // Sayfası olmayan defter açılırsa boş bir sayfa ekle.
    LaunchedEffect(list?.size) { if (list != null && list.isEmpty()) repo.addPage(noteId) }
    LaunchedEffect(list?.size, goLast) {
        if (list != null && list.isNotEmpty()) {
            if (goLast) { index = list.size - 1; goLast = false }
            index = index.coerceIn(0, list.size - 1)
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = Export.decodeImage(ctx, uri)
            if (bmp != null) { repo.addPage(noteId, bg = bmp); goLast = true }
        }
    }
    val page = list?.getOrNull(index.coerceAtMost((list.size - 1).coerceAtLeast(0)))

    Screen(note?.title ?: "", onBack, actions = {
        IconButton({ scope.launch { repo.addPage(noteId); goLast = true } }) { Icon(Icons.Rounded.NoteAdd, "Boş sayfa ekle") }
        IconButton({ pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Icon(Icons.Rounded.AddPhotoAlternate, "Soru fotoğrafı ekle")
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Diğer") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Sayfayı PNG olarak paylaş") }, {
                    menu = false
                    page?.let { p -> scope.launch { Export.share(ctx, Export.pagePng(ctx, repo, p.id, "${note?.title}-${index + 1}"), "image/png") } }
                })
                DropdownMenuItem({ Text("Defteri PDF olarak paylaş") }, {
                    menu = false
                    scope.launch { Export.share(ctx, Export.notePdf(ctx, repo, noteId, note?.title ?: "defter"), "application/pdf") }
                })
                DropdownMenuItem({ Text("Yeniden adlandır") }, { menu = false; renaming = true })
                DropdownMenuItem({ Text("Dersi değiştir") }, { menu = false; subjectDialog = true })
                DropdownMenuItem({ Text("Bu sayfayı sil", color = MaterialTheme.colorScheme.error) }, { menu = false; deletePage = true })
            }
        }
    }) { pad ->
        if (page == null) { Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Screen }
        key(page.id) {
            InkPane(page.id, Modifier.padding(pad).fillMaxSize()) {
                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                KIconButton(KI.LEFT, "Önceki sayfa", enabled = index > 0) { index-- }
                Text("${index + 1}/${list!!.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 4.dp))
                KIconButton(KI.RIGHT, "Sonraki sayfa", enabled = index < list.size - 1) { index++ }
            }
        }
    }

    if (renaming) InputDialog("Defteri yeniden adlandır", note?.title ?: "", onConfirm = { t ->
        scope.launch { repo.dao.noteNow(noteId)?.let { repo.dao.update(it.copy(title = t)) } }
    }, onDismiss = { renaming = false })
    if (subjectDialog) AlertDialog(
        onDismissRequest = { subjectDialog = false }, title = { Text("Ders") },
        text = {
            SubjectPicker(subjects, note?.subjectId, { id ->
                scope.launch { repo.dao.noteNow(noteId)?.let { repo.dao.update(it.copy(subjectId = id)) } }
            })
        },
        confirmButton = { TextButton({ subjectDialog = false }) { Text("Tamam") } },
    )
    if (deletePage && page != null) ConfirmDialog("Sayfa silinsin mi?", "Bu sayfadaki çizimler kalıcı olarak silinecek.",
        onConfirm = { scope.launch { repo.deletePage(page.id) } }, onDismiss = { deletePage = false })
}
