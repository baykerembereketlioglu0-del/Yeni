package app.karala.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.karala.App
import app.karala.ink.InkView
import app.karala.ink.PageBackground
import app.karala.ink.ToolType
import app.karala.overlay.Icon
import app.karala.overlay.Ui
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Katmandaki çizgi ikonların Compose karşılığı (tek ikon seti, iki arayüz). */
@Composable
fun KIcon(icon: Icon, tint: Color = LocalContentColor.current, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val path = remember(icon) { PathParser().parsePathString(icon.d).toPath() }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path, tint, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun KIconButton(icon: Icon, desc: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.35f)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { KIcon(icon, tint) }
}

private fun shapeIcon(t: ToolType) = when (t) {
    ToolType.ARROW -> Icon.ARROW; ToolType.RECT -> Icon.RECT; ToolType.ELLIPSE -> Icon.ELLIPSE; else -> Icon.LINE
}

/**
 * Uygulama içi çizim alanı: bir sayfayı yükler, düzenletir ve değişiklikleri otomatik kaydeder.
 * [trailing] araç çubuğunun sonuna eklenen ekran-özel düğmelerdir (sayfa gezinme vb.).
 */
@Composable
fun InkPane(
    pageId: Long,
    modifier: Modifier = Modifier,
    inkVisible: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val view = remember { InkView(ctx, editorMode = true) }
    val p = prefs

    var tool by remember { mutableStateOf(p.lastTool.takeIf { it != ToolType.LASER } ?: ToolType.PEN) }
    var settingsTick by remember { mutableIntStateOf(0) }   // renk/kalınlık değişince yeniden uygula
    var historyTick by remember { mutableIntStateOf(0) }
    var showOptions by remember { mutableStateOf(false) }
    var bgMenu by remember { mutableStateOf(false) }
    var loaded by remember(pageId) { mutableStateOf(false) }
    val dirty = remember { booleanArrayOf(false) }
    var saveJob by remember { mutableStateOf<Job?>(null) }

    fun flush(id: Long) {
        saveJob?.cancel()
        if (!dirty[0]) return
        dirty[0] = false
        val page = view.page
        App.instance.scope.launch { repo.saveInk(id, page) }
    }

    LaunchedEffect(pageId) {
        val ink = repo.loadInk(pageId)
        val bg = repo.loadBg(pageId)
        view.backgroundBitmap = bg
        view.setPage(ink)
        loaded = true
    }
    DisposableEffect(pageId) {
        view.onChanged = {
            dirty[0] = true
            saveJob?.cancel()
            saveJob = scope.launch { delay(700); flush(pageId) }
        }
        view.onHistoryChanged = { historyTick++ }
        onDispose { if (loaded) flush(pageId); view.onChanged = null; view.onHistoryChanged = null }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (loaded) flush(pageId) }

    // Araç durumunu çizim yüzeyine uygula.
    LaunchedEffect(tool, settingsTick) {
        view.tool = tool
        view.color = p.colorFor(tool)
        view.strokeWidth = p.widthFor(tool) * density
        view.eraserRadius = p.eraserSize * density / 2f
        view.eraserMode = p.eraserMode
        view.stylusOnly = p.stylusOnly
        view.shapeAssist = p.shapeAssist
    }
    LaunchedEffect(inkVisible) { view.inkVisible = inkVisible }

    fun select(t: ToolType) {
        if (tool == t) showOptions = !showOptions
        else { tool = t; p.lastTool = t; if (t.isShape) p.lastShape = t }
    }

    Column(modifier) {
        AndroidView({ view }, Modifier.weight(1f).fillMaxWidth())

        AnimatedVisibility(showOptions) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    settingsTick // yeniden çizim için oku
                    if (tool.isShape) Row {
                        listOf(ToolType.LINE, ToolType.ARROW, ToolType.RECT, ToolType.ELLIPSE).forEach { s ->
                            KIconButton(shapeIcon(s), s.name, active = tool == s) { tool = s; p.lastShape = s; p.lastTool = s }
                        }
                    }
                    if (tool == ToolType.ERASER) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!p.eraserPartial, { p.eraserPartial = false; settingsTick++ }, { Text("Çizgiyi sil") })
                        FilterChip(p.eraserPartial, { p.eraserPartial = true; settingsTick++ }, { Text("Parça sil") })
                    } else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val current = p.colorFor(tool)
                        Ui.PALETTE.forEach { c ->
                            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == current) 3.dp else 1.dp, if (c == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { p.setColorFor(tool, c); settingsTick++ })
                        }
                    }
                    val range = when (tool) { ToolType.HIGHLIGHTER -> 8f..44f; ToolType.ERASER -> 6f..64f; else -> 1f..14f }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Kalınlık", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(p.widthFor(tool).coerceIn(range), { p.setWidthFor(tool, it); settingsTick++ }, valueRange = range,
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                        Text("${p.widthFor(tool).toInt()}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                historyTick
                settingsTick
                KIconButton(if (p.stylusOnly) Icon.STYLUS else Icon.HAND,
                    if (p.stylusOnly) "Kalem modu: yalnızca kalem ucu çizer" else "El modu: parmakla çizilir", active = p.stylusOnly) {
                    p.stylusOnly = !p.stylusOnly; settingsTick++
                    android.widget.Toast.makeText(ctx, if (p.stylusOnly) "Kalem modu: yalnızca kalem ucu çizer" else "El modu: parmağınla çizebilirsin",
                        android.widget.Toast.LENGTH_SHORT).show()
                }
                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                KIconButton(Icon.PEN, "Kalem", tool == ToolType.PEN) { select(ToolType.PEN) }
                KIconButton(Icon.HIGHLIGHTER, "Fosforlu kalem", tool == ToolType.HIGHLIGHTER) { select(ToolType.HIGHLIGHTER) }
                KIconButton(Icon.ERASER, "Silgi", tool == ToolType.ERASER) { select(ToolType.ERASER) }
                KIconButton(shapeIcon(if (tool.isShape) tool else p.lastShape), "Şekiller", tool.isShape) { select(if (tool.isShape) tool else p.lastShape) }
                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                KIconButton(Icon.UNDO, "Geri al", enabled = view.editor.canUndo) { view.undo() }
                KIconButton(Icon.REDO, "Yinele", enabled = view.editor.canRedo) { view.redo() }
                KIconButton(Icon.TRASH, "Sayfayı temizle") { view.clearAll() }
                Box {
                    KIconButton(Icon.BOARD, "Zemin", bgMenu) { bgMenu = true }
                    DropdownMenu(bgMenu, { bgMenu = false }) {
                        listOf(PageBackground.NONE to "Düz (koyu)", PageBackground.WHITE to "Beyaz", PageBackground.BLACK to "Kara tahta",
                            PageBackground.GRID to "Kareli", PageBackground.LINED to "Çizgili", PageBackground.DOTS to "Noktalı").forEach { (bg, name) ->
                            DropdownMenuItem({ Text(name) }, { view.setPageBackground(bg); bgMenu = false })
                        }
                    }
                }
                trailing()
            }
        }
    }
}
