package app.karala.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.karala.overlay.Icon as KI
import app.karala.overlay.OverlayService

/**
 * Ekran kalemini başlatma akışı: izin yoksa izin rehberi, varsa "El mi, Kalem mi?" seçimi, sonra servis.
 * Ana sayfa, kısayol ve ayarlar aynı akışı kullanır.
 */
class PenLauncher(private val ctx: Context) {
    var modeSheet by mutableStateOf(false)
    var permissionSheet by mutableStateOf(false)
    /** Geçerli çizim girişi (true = kalem). Arayüzün anında güncellenmesi için ayarın aynası. */
    var stylus by mutableStateOf(prefs.stylusOnly)

    fun launch() {
        stylus = prefs.stylusOnly
        when {
            !Settings.canDrawOverlays(ctx) -> permissionSheet = true
            prefs.askInputMode -> modeSheet = true
            else -> OverlayService.start(ctx, prefs.stylusOnly)
        }
    }

    /** Ekrana yeni bir yapışkan not ekler; izin yoksa izin rehberini açar. */
    fun addNote() {
        if (!Settings.canDrawOverlays(ctx)) permissionSheet = true
        else app.karala.overlay.StickyService.add(ctx)
    }

    /** Seçim penceresini her durumda açar (mod çipine dokunulduğunda). */
    fun pickMode() {
        stylus = prefs.stylusOnly
        if (!Settings.canDrawOverlays(ctx)) permissionSheet = true else modeSheet = true
    }

    fun confirm(stylusMode: Boolean, dontAsk: Boolean) {
        prefs.stylusOnly = stylusMode; stylus = stylusMode
        prefs.askInputMode = !dontAsk
        modeSheet = false
        OverlayService.start(ctx, stylusMode)
    }
}

@Composable
fun rememberPenLauncher(): PenLauncher { val ctx = LocalContext.current; return remember { PenLauncher(ctx) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PenLauncherHost(pen: PenLauncher) {
    val ctx = LocalContext.current
    // İzin ekranından dönünce: izin verildiyse rehberi kapat ve akışa devam et.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (pen.permissionSheet && Settings.canDrawOverlays(ctx)) { pen.permissionSheet = false; pen.launch() }
    }
    if (pen.modeSheet) ModalBottomSheet({ pen.modeSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        ModeSheetContent(pen.stylus, !prefs.askInputMode) { s, dontAsk -> pen.confirm(s, dontAsk) }
    }
    if (pen.permissionSheet) ModalBottomSheet({ pen.permissionSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        PermissionSheetContent(
            onOpenPermission = {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }
            },
            onOpenAppInfo = {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
            },
        )
    }
}

@Composable
fun ModeSheetContent(initialStylus: Boolean, initialDontAsk: Boolean, onConfirm: (stylus: Boolean, dontAsk: Boolean) -> Unit) {
    var stylus by remember { mutableStateOf(initialStylus) }
    var dontAsk by remember { mutableStateOf(initialDontAsk) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
        Text("Nasıl çizeceksin?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text("Sonradan araç çubuğundaki el / kalem düğmesiyle değiştirebilirsin.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeCard(KI.HAND, "El", "Parmağınla çizersin. Kalem ucu da çalışır.", !stylus, Modifier.weight(1f).fillMaxHeight()) { stylus = false }
            ModeCard(KI.STYLUS, "Kalem", "Yalnızca kalem ucu çizer. Elin ekrana değse de iz bırakmaz.", stylus, Modifier.weight(1f).fillMaxHeight()) { stylus = true }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Switch) { dontAsk = !dontAsk }.padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Seçimimi hatırla", style = MaterialTheme.typography.titleSmall)
                Text("Açıkken her başlatışta sorulmaz.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(dontAsk, { dontAsk = it })
        }
        Spacer(Modifier.height(10.dp))
        BrandButton("Ekran kalemini başlat", Modifier.fillMaxWidth()) { onConfirm(stylus, dontAsk) }
    }
}

@Composable
private fun ModeCard(icon: KI, title: String, desc: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.clip(shape)
        .background(if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
        .border(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, shape)
        .clickable(role = Role.RadioButton, onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(if (selected) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center) {
                KIcon(icon, if (selected) accent else MaterialTheme.colorScheme.onSurface, 40.dp)
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = if (selected) accent else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (selected) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(24.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, "Seçili", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun PermissionSheetContent(onOpenPermission: () -> Unit, onOpenAppInfo: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
        Text("Bir izin gerekiyor", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text("Videonun ve diğer uygulamaların üzerine yazabilmek için Karala'nın \"diğer uygulamalar üzerinde görüntüleme\" iznine ihtiyacı var.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Step(1, "İzin ekranını aç", "Açılan sayfada anahtarı aç, sonra geri gel.")
        Step(2, "Anahtar gri mi? \"Kısıtlı ayar\" mı yazıyor?",
            "Mağaza dışından kurulan uygulamalarda Android bunu bir kez sorar. Uygulama bilgilerini aç → sağ üstteki ⋮ menüsü → \"Kısıtlı ayarlara izin ver\". Ardından 1. adımı tekrarla.")
        Spacer(Modifier.height(8.dp))
        BrandButton("İzin ekranını aç", Modifier.fillMaxWidth(), onClick = onOpenPermission)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onOpenAppInfo, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(18.dp)) { Text("Uygulama bilgilerini aç") }
    }
}

@Composable
private fun Step(n: Int, title: String, text: String) {
    Row(Modifier.padding(bottom = 14.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Marka geçişli ana eylem düğmesi. */
@Composable
fun BrandButton(text: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val k = K
    Row(modifier.height(54.dp).clip(RoundedCornerShape(18.dp)).background(k.brand).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CompositionLocalProvider(LocalContentColor provides k.onBrand) {
            if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.titleMedium, color = k.onBrand, maxLines = 1)
        }
    }
}

